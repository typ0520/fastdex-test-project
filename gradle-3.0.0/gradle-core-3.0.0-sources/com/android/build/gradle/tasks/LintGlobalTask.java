/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LintGradleClient;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.Reporter.Stats;
import com.android.tools.lint.Warning;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.UnusedResourceDetector;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public class LintGlobalTask extends LintBaseTask {

    private Map<String, VariantInputs> variantInputMap;
    private ConfigurableFileCollection allInputs;

    @InputFiles
    @Optional
    public FileCollection getAllInputs() {
        return allInputs;
    }

    @TaskAction
    public void lint() throws IOException {
        AndroidProject modelProject = createAndroidProject(getProject());
        lintAllVariants(modelProject);
    }

    /**
     * Runs lint individually on all the variants, and then compares the results across variants and
     * reports these
     */
    public void lintAllVariants(@NonNull AndroidProject modelProject) throws IOException {
        // In the Gradle integration we iterate over each variant, and
        // attribute unused resources to each variant, so don't make
        // each variant run go and inspect the inactive variant sources
        UnusedResourceDetector.sIncludeInactiveReferences = false;

        Map<Variant, List<Warning>> warningMap = Maps.newHashMap();
        List<LintBaseline> baselines = Lists.newArrayList();
        for (Variant variant : modelProject.getVariants()) {
            // we are not running lint on all the variants, so skip the ones where we don't have
            // a variant inputs (see TaskManager::isLintVariant)
            final VariantInputs variantInputs = variantInputMap.get(variant.getName());
            if (variantInputs != null) {
                Pair<List<Warning>, LintBaseline> pair =
                        runLint(modelProject, variant, variantInputs, false);
                List<Warning> warnings = pair.getFirst();
                warningMap.put(variant, warnings);
                LintBaseline baseline = pair.getSecond();
                if (baseline != null) {
                    baselines.add(baseline);
                }
            }
        }

        final LintOptions lintOptions = getLintOptions();

        // Compute error matrix
        boolean quiet = false;
        if (lintOptions != null) {
            quiet = lintOptions.isQuiet();
        }

        for (Map.Entry<Variant, List<Warning>> entry : warningMap.entrySet()) {
            Variant variant = entry.getKey();
            List<Warning> warnings = entry.getValue();
            if (!isFatalOnly() && !quiet) {
                LOG.warn(
                        "Ran lint on variant {}: {} issues found",
                        variant.getName(),
                        warnings.size());
            }
        }

        List<Warning> mergedWarnings = LintGradleClient.merge(warningMap, modelProject);
        int errorCount = 0;
        int warningCount = 0;
        for (Warning warning : mergedWarnings) {
            if (warning.severity == Severity.ERROR || warning.severity == Severity.FATAL) {
                errorCount++;
            } else if (warning.severity == Severity.WARNING) {
                warningCount++;
            }
        }

        // We pick the first variant to generate the full report and don't generate if we don't
        // have any variants.
        if (!modelProject.getVariants().isEmpty()) {
            Set<Variant> allVariants =
                    Sets.newTreeSet((v1, v2) -> v1.getName().compareTo(v2.getName()));

            allVariants.addAll(modelProject.getVariants());
            Variant variant = allVariants.iterator().next();

            IssueRegistry registry = new BuiltinIssueRegistry();
            LintCliFlags flags = new LintCliFlags();
            LintGradleClient client =
                    new LintGradleClient(
                            registry,
                            flags,
                            getProject(),
                            modelProject,
                            getSdkHome(),
                            variant,
                            variantInputMap.get(variant.getName()),
                            getBuildTools());
            syncOptions(
                    lintOptions,
                    client,
                    flags,
                    null,
                    getProject(),
                    getReportsDir(),
                    true,
                    isFatalOnly());

            // Compute baseline counts. This is tricky because an error could appear in
            // multiple variants, and in that case it should only be counted as filtered
            // from the baseline once, but if there are errors that appear only in individual
            // variants, then they shouldn't count as one. To correctly account for this we
            // need to ask the baselines themselves to merge their results. Right now they
            // only contain the remaining (fixed) issues; to address this we'd need to move
            // found issues to a different map such that at the end we can successively
            // merge the baseline instances together to a final one which has the full set
            // of filtered and remaining counts.
            int baselineErrorCount = 0;
            int baselineWarningCount = 0;
            int fixedCount = 0;
            if (!baselines.isEmpty()) {
                // Figure out the actual overlap; later I could stash these into temporary
                // objects to compare
                // For now just combine them in a dumb way
                for (LintBaseline baseline : baselines) {
                    baselineErrorCount =
                            Math.max(baselineErrorCount, baseline.getFoundErrorCount());
                    baselineWarningCount =
                            Math.max(baselineWarningCount, baseline.getFoundWarningCount());
                    fixedCount = Math.max(fixedCount, baseline.getFixedCount());
                }
            }

            Stats stats =
                    new Stats(
                            errorCount,
                            warningCount,
                            baselineErrorCount,
                            baselineWarningCount,
                            fixedCount);

            for (Reporter reporter : flags.getReporters()) {
                reporter.write(stats, mergedWarnings);
            }

            File baselineFile = flags.getBaselineFile();
            if (baselineFile != null && !baselineFile.exists()) {
                File dir = baselineFile.getParentFile();
                boolean ok = true;
                if (!dir.isDirectory()) {
                    ok = dir.mkdirs();
                }
                if (!ok) {
                    System.err.println("Couldn't create baseline folder " + dir);
                } else {
                    Reporter reporter = Reporter.createXmlReporter(client, baselineFile, true);
                    reporter.write(stats, mergedWarnings);
                    System.err.println("Created baseline file " + baselineFile);
                    if (LintGradleClient.continueAfterBaseLineCreated()) {
                        return;
                    }
                    System.err.println("(Also breaking build in case this was not intentional.)");
                    String message =
                            ""
                                    + "Created baseline file "
                                    + baselineFile
                                    + "\n"
                                    + "\n"
                                    + "Also breaking the build in case this was not intentional. If you\n"
                                    + "deliberately created the baseline file, re-run the build and this\n"
                                    + "time it should succeed without warnings.\n"
                                    + "\n"
                                    + "If not, investigate the baseline path in the lintOptions config\n"
                                    + "or verify that the baseline file has been checked into version\n"
                                    + "control.\n"
                                    + "\n"
                                    + "You can set the system property lint.baselines.continue=true\n"
                                    + "if you want to create many missing baselines in one go.";
                    throw new GradleException(message);
                }
            }

            if (baselineErrorCount > 0 || baselineWarningCount > 0) {
                System.out.println(
                        String.format(
                                "%1$s were filtered out because "
                                        + "they were listed in the baseline file, %2$s\n",
                                LintUtils.describeCounts(
                                        baselineErrorCount, baselineWarningCount, false, true),
                                baselineFile));
            }
            if (fixedCount > 0) {
                System.out.println(
                        String.format(
                                "%1$d errors/warnings were listed in the "
                                        + "baseline file (%2$s) but not found in the project; perhaps they have "
                                        + "been fixed?\n",
                                fixedCount, baselineFile));
            }

            if (flags.isSetExitCode() && errorCount > 0) {
                abort();
            }
        }
    }

    public static class GlobalConfigAction extends BaseConfigAction<LintGlobalTask> {

        private final Collection<VariantScope> variantScopes;

        public GlobalConfigAction(
                @NonNull GlobalScope globalScope, @NonNull Collection<VariantScope> variantScopes) {
            super(globalScope);
            this.variantScopes = variantScopes;
        }

        @NonNull
        @Override
        public String getName() {
            return TaskManager.LINT;
        }

        @NonNull
        @Override
        public Class<LintGlobalTask> getType() {
            return LintGlobalTask.class;
        }

        @Override
        public void execute(@NonNull LintGlobalTask lintTask) {
            super.execute(lintTask);

            lintTask.setDescription("Runs lint on all variants.");
            lintTask.setVariantName("");

            lintTask.allInputs = getGlobalScope().getProject().files();
            lintTask.variantInputMap =
                    variantScopes
                            .stream()
                            .map(
                                    variantScope -> {
                                        VariantInputs inputs = new VariantInputs(variantScope);
                                        lintTask.allInputs.from(inputs.getAllInputs());
                                        return inputs;
                                    })
                            .collect(Collectors.toMap(VariantInputs::getName, Function.identity()));
        }
    }
}
