/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.tasks.LintBaseTask;
import com.android.builder.Version;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.LintOptions;
import com.android.builder.model.Variant;
import com.android.sdklib.BuildToolInfo;
import com.android.tools.lint.LintCliClient;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Warning;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.w3c.dom.Document;

public class LintGradleClient extends LintCliClient {
    private final AndroidProject modelProject;

    /**
     * Variant to run the client on.
     */
    @NonNull private final Variant variant;

    private final org.gradle.api.Project gradleProject;
    private File sdkHome;
    @NonNull private final LintBaseTask.VariantInputs variantInputs;
    private final BuildToolInfo buildToolInfo;

    public LintGradleClient(
            @NonNull IssueRegistry registry,
            @NonNull LintCliFlags flags,
            @NonNull org.gradle.api.Project gradleProject,
            @NonNull AndroidProject modelProject,
            @Nullable File sdkHome,
            @NonNull Variant variant,
            @NonNull LintBaseTask.VariantInputs variantInputs,
            @Nullable BuildToolInfo buildToolInfo) {
        super(flags, CLIENT_GRADLE);
        this.gradleProject = gradleProject;
        this.modelProject = modelProject;
        this.sdkHome = sdkHome;
        this.variantInputs = variantInputs;
        this.registry = registry;
        this.buildToolInfo = buildToolInfo;
        this.variant = variant;
    }

    @Nullable
    @Override
    public String getClientRevision() {
        return Version.ANDROID_GRADLE_PLUGIN_VERSION;
    }

    @NonNull
    @Override
    public Configuration getConfiguration(@NonNull Project project, @Nullable LintDriver driver) {
        // Look up local lint configuration for this project, either via Gradle lintOptions
        // or via local lint.xml
        AndroidProject gradleProjectModel = project.getGradleProjectModel();
        if (gradleProjectModel != null) {
            LintOptions lintOptions = gradleProjectModel.getLintOptions();
            File lintXml = lintOptions.getLintConfig();
            if (lintXml == null) {
                lintXml = new File(project.getDir(), DefaultConfiguration.CONFIG_FILE_NAME);
            }

            Map<String, Integer> overrides = lintOptions.getSeverityOverrides();
            if (overrides != null && !overrides.isEmpty()) {
                return new CliConfiguration(lintXml, getConfiguration(), project,
                        flags.isFatalOnly()) {
                    @NonNull
                    @Override
                    public Severity getSeverity(@NonNull Issue issue) {
                        Integer optionSeverity = overrides.get(issue.getId());
                        if (optionSeverity != null) {
                            Severity severity = Severity.fromLintOptionSeverity(optionSeverity);

                            if (flags.isFatalOnly() && severity != Severity.FATAL) {
                                return Severity.IGNORE;
                            }

                            return severity;
                        }

                        return super.getSeverity(issue);
                    }
                };
            }
        }

        return super.getConfiguration(project, driver);
    }

    @NonNull
    @Override
    public List<File> findRuleJars(@NonNull Project project) {
        return variantInputs.getRuleJars();
    }

    @NonNull
    @Override
    protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
        // Should not be called by lint since we supply an explicit set of projects
        // to the LintRequest
        throw new IllegalStateException();
    }

    @Override
    public File getSdkHome() {
        if (sdkHome != null) {
            return sdkHome;
        }
        return super.getSdkHome();
    }

    @Nullable
    @Override
    public File getCacheDir(@Nullable String name, boolean create) {
        String relative = FD_INTERMEDIATES + separator + "lint-cache";
        if (name != null) {
            relative += File.separator + name;
        }
        File dir = new File(gradleProject.getRootProject().getBuildDir(), relative);
        if (dir.exists() || create && dir.mkdirs()) {
            return dir;
        }

        return super.getCacheDir(name, create);
    }

    @Override
    @NonNull
    protected LintRequest createLintRequest(@NonNull List<File> files) {
        LintRequest lintRequest = new LintRequest(this, files);
        LintGradleProject.ProjectSearch search = new LintGradleProject.ProjectSearch();
        Project project = search.getProject(this, gradleProject, variant.getName());
        lintRequest.setProjects(Collections.singletonList(project));

        return lintRequest;
    }

    /** Whether lint should continue running after a baseline has been created */
    public static boolean continueAfterBaseLineCreated() {
        return VALUE_TRUE.equals(System.getProperty("lint.baselines.continue"));
    }

    /** Run lint with the given registry and return the resulting warnings */
    @NonNull
    public Pair<List<Warning>,LintBaseline> run(@NonNull IssueRegistry registry)
            throws IOException {
        int exitCode = run(registry, Collections.emptyList());

        if (exitCode == LintCliFlags.ERRNO_CREATED_BASELINE) {
            if (continueAfterBaseLineCreated()) {
                return Pair.of(Collections.emptyList(), driver.getBaseline());
            }
            throw new GradleException("Aborting build since new baseline file was created");
        }

        return Pair.of(warnings, driver.getBaseline());
    }

    /**
     * Given a list of results from separate variants, merge them into a single
     * list of warnings, and mark their
     * @param warningMap a map from variant to corresponding warnings
     * @param project the project model
     * @return a merged list of issues
     */
    @NonNull
    public static List<Warning> merge(
            @NonNull Map<Variant,List<Warning>> warningMap,
            @NonNull AndroidProject project) {
        // Easy merge?
        if (warningMap.size() == 1) {
            return warningMap.values().iterator().next();
        }
        int maxCount = 0;
        for (List<Warning> warnings : warningMap.values()) {
            int size = warnings.size();
            maxCount = Math.max(size, maxCount);
        }
        if (maxCount == 0) {
            return Collections.emptyList();
        }

        int totalVariantCount = project.getVariants().size();

        List<Warning> merged = Lists.newArrayListWithExpectedSize(2 * maxCount);

        // Map fro issue to message to line number to file name to canonical warning
        Map<Issue,Map<String, Map<Integer, Map<String, Warning>>>> map =
                Maps.newHashMapWithExpectedSize(2 * maxCount);

        for (Map.Entry<Variant,List<Warning>> entry : warningMap.entrySet()) {
            Variant variant = entry.getKey();
            List<Warning> warnings = entry.getValue();
            for (Warning warning : warnings) {
                Map<String,Map<Integer,Map<String,Warning>>> messageMap = map.get(warning.issue);
                if (messageMap == null) {
                    messageMap = Maps.newHashMap();
                    map.put(warning.issue, messageMap);
                }
                Map<Integer, Map<String, Warning>> lineMap = messageMap.get(warning.message);
                if (lineMap == null) {
                    lineMap = Maps.newHashMap();
                    messageMap.put(warning.message, lineMap);
                }
                Map<String, Warning> fileMap = lineMap.get(warning.line);
                if (fileMap == null) {
                    fileMap = Maps.newHashMap();
                    lineMap.put(warning.line, fileMap);
                }
                String fileName = warning.file != null ? warning.file.getName() : "<unknown>";
                Warning canonical = fileMap.get(fileName);
                if (canonical == null) {
                    canonical = warning;
                    fileMap.put(fileName, canonical);
                    canonical.variants = Sets.newHashSet();
                    canonical.gradleProject = project;
                    merged.add(canonical);
                }
                canonical.variants.add(variant);
            }
        }

        // Clear out variants on any nodes that define all
        for (Warning warning : merged) {
            if (warning.variants != null && warning.variants.size() == totalVariantCount) {
                // If this error is present in all variants, just clear it out
                warning.variants = null;
            }

        }

        Collections.sort(merged);
        return merged;
    }

    @Override
    protected void addProgressPrinter() {
        // No progress printing from the Gradle lint task; gradle tasks
        // do not really do that, even for long-running jobs.
    }

    @Nullable
    @Override
    public BuildToolInfo getBuildTools(@NonNull Project project) {
        return buildToolInfo;
    }

    @Override
    public void report(@NonNull Context context, @NonNull Issue issue, @NonNull Severity severity,
            @NonNull Location location, @NonNull String message, @NonNull TextFormat format,
            @Nullable LintFix fix) {
        if (issue == IssueRegistry.LINT_ERROR
                && message.startsWith("No `.class` files were found in project")) {
            // In Gradle, .class files are always generated when needed, so no need
            // to flag this (and it's erroneous on library projects)
            return;
        }
        super.report(context, issue, severity, location, message, format, fix);
    }

    @Nullable
    @Override
    public Document getMergedManifest(@NonNull Project project) {
        File manifest = variantInputs.getMergedManifest();
        try {
            String xml = Files.asCharSource(manifest, Charsets.UTF_8).read();
            Document document = XmlUtils.parseDocumentSilently(xml, true);
            if (document != null) {
                // Note for later that we'll need to resolve locations from
                // the merged manifest
                resolveMergeManifestSources(document, variantInputs.getManifestMergeReport());

                return document;
            }
        } catch (IOException ioe) {
            log(ioe, "Could not read %1$s", manifest);
        }

        return super.getMergedManifest(project);
    }
}
