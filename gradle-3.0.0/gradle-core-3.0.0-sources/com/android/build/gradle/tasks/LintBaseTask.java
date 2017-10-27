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

import static com.android.build.VariantOutput.OutputType.FULL_SPLIT;
import static com.android.build.VariantOutput.OutputType.MAIN;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.LINT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.AnchorOutputType.ALL_CLASSES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.APK;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.LIBRARY_MANIFEST;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.LINT_JAR;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.MANIFEST_MERGE_REPORT;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LintGradleClient;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.Warning;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.detector.api.Issue;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

public abstract class LintBaseTask extends BaseTask {
    /**
     * Whether lint should attempt to do deep analysis of libraries. E.g. when building up the
     * project graph, when it encounters an AndroidLibrary or JavaLibrary dependency, it should
     * check if it's a local project, and if so recursively initialize the project with the local
     * source paths etc of the library (in the past, this was not the case: it would naively just
     * point to the library's resources and class files, which were the compiled outputs.
     *
     * <p>The new behavior is clearly the correct behavior (see issue #194092), but since this is a
     * risky fix, we're putting it behind a flag now and as soon as we get some real user testing,
     * we should enable this by default and remove the old code.
     */
    public static final boolean MODEL_LIBRARIES = true;

    protected static final Logger LOG = Logging.getLogger(LintBaseTask.class);

    @Nullable protected LintOptions lintOptions;
    @Nullable protected File sdkHome;
    private boolean fatalOnly;
    protected ToolingModelBuilderRegistry toolingRegistry;
    @Nullable protected File reportsDir;

    @Nullable
    public LintOptions getLintOptions() {
        return lintOptions;
    }

    @Nullable
    public File getSdkHome() {
        return sdkHome;
    }

    public ToolingModelBuilderRegistry getToolingRegistry() {
        return toolingRegistry;
    }

    protected void setFatalOnly(boolean fatalOnly) {
        this.fatalOnly = fatalOnly;
    }

    public boolean isFatalOnly() {
        return fatalOnly;
    }

    @Nullable
    public File getReportsDir() {
        return reportsDir;
    }

    protected void abort() {
        String message;
        if (fatalOnly) {
            message =
                    ""
                            + "Lint found fatal errors while assembling a release target.\n"
                            + "\n"
                            + "To proceed, either fix the issues identified by lint, or modify your build script as follows:\n"
                            + "...\n"
                            + "android {\n"
                            + "    lintOptions {\n"
                            + "        checkReleaseBuilds false\n"
                            + "        // Or, if you prefer, you can continue to check for errors in release builds,\n"
                            + "        // but continue the build even when errors are found:\n"
                            + "        abortOnError false\n"
                            + "    }\n"
                            + "}\n"
                            + "...";
        } else {
            message =
                    ""
                            + "Lint found errors in the project; aborting build.\n"
                            + "\n"
                            + "Fix the issues identified by lint, or add the following to your build script to proceed with errors:\n"
                            + "...\n"
                            + "android {\n"
                            + "    lintOptions {\n"
                            + "        abortOnError false\n"
                            + "    }\n"
                            + "}\n"
                            + "...";
        }
        throw new GradleException(message);
    }

    /** Runs lint on the given variant and returns the set of warnings */
    protected Pair<List<Warning>, LintBaseline> runLint(
            /*
             * Note that as soon as we disable {@link #MODEL_LIBRARIES} this is
             * unused and we can delete it and all the callers passing it recursively
             */
            @NonNull AndroidProject modelProject,
            @NonNull Variant variant,
            @NonNull VariantInputs variantInputs,
            boolean report) {
        IssueRegistry registry = createIssueRegistry();
        LintCliFlags flags = new LintCliFlags();
        LintGradleClient client =
                new LintGradleClient(
                        registry,
                        flags,
                        getProject(),
                        modelProject,
                        sdkHome,
                        variant,
                        variantInputs,
                        getBuildTools());
        if (fatalOnly) {
            flags.setFatalOnly(true);
        }
        if (lintOptions != null) {
            syncOptions(
                    lintOptions,
                    client,
                    flags,
                    variant,
                    getProject(),
                    reportsDir,
                    report,
                    fatalOnly);
        }
        if (!report || fatalOnly) {
            flags.setQuiet(true);
        }
        flags.setWriteBaselineIfMissing(report && !fatalOnly);

        Pair<List<Warning>, LintBaseline> warnings;
        try {
            warnings = client.run(registry);
        } catch (IOException e) {
            throw new GradleException("Invalid arguments.", e);
        }

        if (report && client.haveErrors() && flags.isSetExitCode()) {
            abort();
        }

        return warnings;
    }

    protected static void syncOptions(
            @NonNull LintOptions options,
            @NonNull LintGradleClient client,
            @NonNull LintCliFlags flags,
            @Nullable Variant variant,
            @NonNull Project project,
            @Nullable File reportsDir,
            boolean report,
            boolean fatalOnly) {
        options.syncTo(
                client,
                flags,
                variant != null ? variant.getName() : null,
                project,
                reportsDir,
                report);

        boolean displayEmpty = !(fatalOnly || flags.isQuiet());
        for (Reporter reporter : flags.getReporters()) {
            reporter.setDisplayEmpty(displayEmpty);
        }
    }

    protected AndroidProject createAndroidProject(@NonNull Project gradleProject) {
        String modelName = AndroidProject.class.getName();
        ToolingModelBuilder modelBuilder = toolingRegistry.getBuilder(modelName);
        assert modelBuilder != null;

        // setup the level 3 sync.
        final ExtraPropertiesExtension ext = gradleProject.getExtensions().getExtraProperties();
        ext.set(
                AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED,
                Integer.toString(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD));
        ext.set(AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD, true);

        try {
            return (AndroidProject) modelBuilder.buildAll(modelName, gradleProject);
        } finally {
            ext.set(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED, null);
            ext.set(AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD, null);
        }
    }

    private static BuiltinIssueRegistry createIssueRegistry() {
        return new LintGradleIssueRegistry();
    }

    // Issue registry when Lint is run inside Gradle: we replace the Gradle
    // detector with a local implementation which directly references Groovy
    // for parsing. In Studio on the other hand, the implementation is replaced
    // by a PSI-based check. (This is necessary for now since we don't have a
    // tool-agnostic API for the Groovy AST and we don't want to add a 6.3MB dependency
    // on Groovy itself quite yet.
    private static class LintGradleIssueRegistry extends BuiltinIssueRegistry {
        private boolean mInitialized;

        public LintGradleIssueRegistry() {}

        @NonNull
        @Override
        public List<Issue> getIssues() {
            List<Issue> issues = super.getIssues();
            if (!mInitialized) {
                mInitialized = true;
                for (Issue issue : issues) {
                    if (issue.getImplementation().getDetectorClass() == GradleDetector.class) {
                        issue.setImplementation(GroovyGradleDetector.IMPLEMENTATION);
                    }
                }
            }

            return issues;
        }
    }

    public static class VariantInputs {
        @NonNull private final String name;
        @NonNull private final FileCollection localLintJarCollection;
        @NonNull private final FileCollection dependencyLintJarCollection;
        @NonNull private final FileCollection mergedManifest;
        @Nullable private final FileCollection mergedManifestReport;
        private List<File> lintRuleJars;

        private final ConfigurableFileCollection allInputs;

        public VariantInputs(@NonNull VariantScope variantScope) {
            name = variantScope.getFullVariantName();
            allInputs = variantScope.getGlobalScope().getProject().files();

            allInputs.from(
                    localLintJarCollection = variantScope.getGlobalScope().getOutput(LINT_JAR));
            allInputs.from(
                    dependencyLintJarCollection =
                            variantScope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, LINT));

            if (variantScope.hasOutput(MERGED_MANIFESTS)) {
                mergedManifest = variantScope.getOutput(MERGED_MANIFESTS);
            } else if (variantScope.hasOutput(LIBRARY_MANIFEST)) {
                mergedManifest = variantScope.getOutput(LIBRARY_MANIFEST);
            } else {
                throw new RuntimeException(
                        "VariantInputs initialized with no merged manifest on: "
                                + variantScope.getVariantConfiguration().getType());
            }
            allInputs.from(mergedManifest);

            if (variantScope.hasOutput(MANIFEST_MERGE_REPORT)) {
                allInputs.from(
                        mergedManifestReport = variantScope.getOutput(MANIFEST_MERGE_REPORT));
            } else {
                throw new RuntimeException(
                        "VariantInputs initialized with no merged manifest report on: "
                                + variantScope.getVariantConfiguration().getType());
            }

            // these inputs are only there to ensure that the lint task runs after these build
            // intermediates/outputs are built.
            allInputs.from(variantScope.getOutput(ALL_CLASSES));
            if (variantScope.hasOutput(APK)) {
                allInputs.from(variantScope.getOutput(APK));
            }
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public FileCollection getAllInputs() {
            return allInputs;
        }

        /** the lint rule jars */
        @NonNull
        public List<File> getRuleJars() {
            if (lintRuleJars == null) {
                lintRuleJars =
                        Streams.concat(
                                        dependencyLintJarCollection.getFiles().stream(),
                                        localLintJarCollection.getFiles().stream())
                                .filter(File::isFile)
                                .collect(Collectors.toList());
            }

            return lintRuleJars;
        }

        /** the merged manifest of the current module */
        @NonNull
        public File getMergedManifest() {
            File file = mergedManifest.getSingleFile();
            if (file.isFile()) {
                return file;
            }

            Collection<BuildOutput> manifests =
                    BuildOutputs.load(
                            ImmutableList.of(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS),
                            file);

            if (manifests.isEmpty()) {
                throw new RuntimeException("Can't find any manifest in folder: " + file);
            }

            // first search for a main manifest
            Optional<File> mainManifest =
                    manifests
                            .stream()
                            .filter(buildOutput -> buildOutput.getApkInfo().getType() == MAIN)
                            .map(BuildOutput::getOutputFile)
                            .findFirst();
            if (mainManifest.isPresent()) {
                return mainManifest.get();
            }

            // else search for a full_split with no filters.
            Optional<File> universalSplit =
                    manifests
                            .stream()
                            .filter(
                                    output ->
                                            output.getApkInfo().getType() == FULL_SPLIT
                                                    && output.getFilters().isEmpty())
                            .map(BuildOutput::getOutputFile)
                            .findFirst();

            // return the universal Manifest, or a random one if not found.
            return universalSplit.orElseGet(() -> manifests.iterator().next().getOutputFile());
        }

        @Nullable
        public File getManifestMergeReport() {
            if (mergedManifestReport == null) {
                return null;
            }

            return mergedManifestReport.getSingleFile();
        }
    }

    public abstract static class BaseConfigAction<T extends LintBaseTask>
            implements TaskConfigAction<T> {

        @NonNull private final GlobalScope globalScope;

        public BaseConfigAction(@NonNull GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        protected GlobalScope getGlobalScope() {
            return globalScope;
        }

        @Override
        public void execute(@NonNull T lintTask) {
            lintTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            lintTask.lintOptions = globalScope.getExtension().getLintOptions();
            File sdkFolder = globalScope.getSdkHandler().getSdkFolder();
            if (sdkFolder != null) {
                lintTask.sdkHome = sdkFolder;
            }

            lintTask.toolingRegistry = globalScope.getToolingRegistry();
            lintTask.reportsDir = globalScope.getReportsDir();
            lintTask.setAndroidBuilder(globalScope.getAndroidBuilder());
        }
    }
}
