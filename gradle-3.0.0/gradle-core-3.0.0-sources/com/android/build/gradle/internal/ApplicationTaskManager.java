/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APK;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.JAVAC;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AppPreBuildTask;
import com.android.build.gradle.internal.tasks.ApplicationId;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.TestPreBuildTask;
import com.android.build.gradle.internal.transforms.InstantRunDependenciesApkBuilder;
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Optional;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for creating tasks in an Android application project.
 */
public class ApplicationTaskManager extends TaskManager {

    public ApplicationTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantScope(
            @NonNull final TaskFactory tasks, @NonNull final VariantScope variantScope) {
        BaseVariantData variantData = variantScope.getVariantData();
        assert variantData instanceof ApplicationVariantData;

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        handleMicroApp(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to publish the applicationId.
        createApplicationIdWriterTask(tasks, variantScope);

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeApkManifestsTask(tasks, variantScope));

        // Add a task to create the res values
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createGenerateResValuesTask(tasks, variantScope));

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createRenderscriptTask(tasks, variantScope));

        // Add a task to merge the resource folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                (Recorder.VoidBlock) () -> createMergeResourcesTask(tasks, variantScope, true));

        // Add a task to merge the asset folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeAssetsTask(tasks, variantScope, null));

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createBuildConfigTask(tasks, variantScope));

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    // Add a task to process the Android Resources and generate source files
                    createApkProcessResTask(tasks, variantScope);

                    // Add a task to process the java resources
                    createProcessJavaResTask(tasks, variantScope);
                });

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_AIDL_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createAidlTask(tasks, variantScope));

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_SHADER_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createShaderTask(tasks, variantScope));

        // Add NDK tasks
        if (!isComponentModelPlugin()) {
            recorder.record(
                    ExecutionType.APP_TASK_MANAGER_CREATE_NDK_TASK,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createNdkTasks(tasks, variantScope));
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(tasks, getNdkBuildable(variantData));
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // Add external native build tasks

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(tasks, variantScope);
                });

        // Add a task to merge the jni libs folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeJniLibFoldersTasks(tasks, variantScope));

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(tasks, variantScope);

        // Add a compile task
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_COMPILE_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> addCompileTask(tasks, variantScope));

        createStripNativeLibraryTask(tasks, variantScope);

        if (variantScope.getOutputScope().getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS)) {
            if (extension.getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException(
                        "Pure splits can only be used with buildtools 21 and later");
            }

            recorder.record(
                    ExecutionType.APP_TASK_MANAGER_CREATE_SPLIT_TASK,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createSplitTasks(tasks, variantScope));
        }

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    AndroidTask<BuildInfoWriterTask> buildInfoWriterTask =
                            createInstantRunPackagingTasks(tasks, variantScope);
                    createPackagingTask(tasks, variantScope, buildInfoWriterTask);
                });

        // create the lint tasks.
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_LINT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createLintTasks(tasks, variantScope));
    }

    private void addCompileTask(@NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        // create data binding merge task before the javac task so that it can
        // parse jars before any consumer
        createDataBindingMergeArtifactsTaskIfNecessary(tasks, variantScope);
        AndroidTask<? extends JavaCompile> javacTask = createJavacTask(tasks, variantScope);
        VariantScope.Java8LangSupport java8LangSupport = variantScope.getJava8LangSupportType();
        if (java8LangSupport == VariantScope.Java8LangSupport.INVALID) {
            return;
        }
        // Only warn for users of retrolambda and dexguard
        String pluginName = null;
        if (java8LangSupport == VariantScope.Java8LangSupport.DEXGUARD) {
            pluginName = "dexguard";
        } else if (java8LangSupport == VariantScope.Java8LangSupport.RETROLAMBDA) {
            pluginName = "me.tatarka.retrolambda";
        }

        if (pluginName != null) {
            String warningMsg =
                    String.format(
                            "One of the plugins you are using supports Java 8 "
                                    + "language features. To try the support built into"
                                    + " the Android plugin, remove the following from "
                                    + "your build.gradle:\n"
                                    + "    apply plugin: '%s'\n"
                                    + "To learn more, go to https://d.android.com/r/"
                                    + "tools/java-8-support-message.html\n",
                            pluginName);

            androidBuilder
                    .getErrorReporter()
                    .handleSyncWarning(null, SyncIssue.TYPE_GENERIC, warningMsg);
        }

        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, tasks, variantScope);
        createPostCompilationTasks(tasks, variantScope);
    }

    /** Create tasks related to creating pure split APKs containing sharded dex files. */
    @Nullable
    private AndroidTask<BuildInfoWriterTask> createInstantRunPackagingTasks(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {

        if (!variantScope.getInstantRunBuildContext().isInInstantRunMode()
                || variantScope.getInstantRunTaskManager() == null) {
            return null;
        }

        AndroidTask<BuildInfoWriterTask> buildInfoGeneratorTask =
                getAndroidTasks()
                        .create(
                                tasks,
                                new BuildInfoWriterTask.ConfigAction(variantScope, getLogger()));

        variantScope.getInstantRunTaskManager()
                        .configureBuildInfoWriterTask(buildInfoGeneratorTask);

        InstantRunPatchingPolicy patchingPolicy =
                variantScope.getInstantRunBuildContext().getPatchingPolicy();

        if (InstantRunPatchingPolicy.useMultiApk(patchingPolicy)) {

            PackagingScope packagingScope = new DefaultGradlePackagingScope(variantScope);

            // create the transforms that will create the dependencies apk.
            InstantRunDependenciesApkBuilder dependenciesApkBuilder =
                    new InstantRunDependenciesApkBuilder(
                            getLogger(),
                            project,
                            variantScope.getInstantRunBuildContext(),
                            variantScope.getGlobalScope().getAndroidBuilder(),
                            variantScope.getGlobalScope().getBuildCache(),
                            packagingScope,
                            packagingScope.getSigningConfig(),
                            AaptGeneration.fromProjectOptions(projectOptions),
                            packagingScope.getAaptOptions(),
                            new File(packagingScope.getInstantRunSplitApkOutputFolder(), "dep"),
                            packagingScope.getInstantRunSupportDir(),
                            new File(
                                    packagingScope.getIncrementalDir(
                                            "InstantRunDependenciesApkBuilder"),
                                    "aapt-temp"));

            Optional<AndroidTask<TransformTask>> dependenciesApkBuilderTask =
                    variantScope
                            .getTransformManager()
                            .addTransform(tasks, variantScope, dependenciesApkBuilder);

            dependenciesApkBuilderTask.ifPresent(
                    task -> task.dependsOn(tasks, getValidateSigningTask(tasks, packagingScope)));

            // and now the transform that will create a split FULL_APK for each slice.
            InstantRunSliceSplitApkBuilder slicesApkBuilder =
                    new InstantRunSliceSplitApkBuilder(
                            getLogger(),
                            project,
                            variantScope.getInstantRunBuildContext(),
                            variantScope.getGlobalScope().getAndroidBuilder(),
                            variantScope.getGlobalScope().getBuildCache(),
                            packagingScope,
                            packagingScope.getSigningConfig(),
                            AaptGeneration.fromProjectOptions(projectOptions),
                            packagingScope.getAaptOptions(),
                            new File(packagingScope.getInstantRunSplitApkOutputFolder(), "slices"),
                            packagingScope.getInstantRunSupportDir(),
                            new File(
                                    packagingScope.getIncrementalDir(
                                            "InstantRunSliceSplitApkBuilder"),
                                    "aapt-temp"),
                            globalScope
                                    .getProjectOptions()
                                    .get(OptionalBooleanOption.SERIAL_AAPT2));

            Optional<AndroidTask<TransformTask>> transformTaskAndroidTask = variantScope
                    .getTransformManager().addTransform(tasks, variantScope, slicesApkBuilder);

            if (transformTaskAndroidTask.isPresent()) {
                AndroidTask<TransformTask> splitApk = transformTaskAndroidTask.get();
                splitApk.dependsOn(tasks, getValidateSigningTask(tasks, packagingScope));
                variantScope.getAssembleTask().dependsOn(tasks, splitApk);
                buildInfoGeneratorTask
                        .configure(tasks, task -> task.mustRunAfter(splitApk.getName()));
            }

            // if the assembleVariant task run, make sure it also runs the task to generate
            // the build-info.xml.
            variantScope.getAssembleTask().dependsOn(tasks, buildInfoGeneratorTask);
        }
        return buildInfoGeneratorTask;
    }

    @Override
    protected void postJavacCreation(
            @NonNull final TaskFactory tasks, @NonNull VariantScope scope) {
        final FileCollection javacOutput = scope.getOutput(JAVAC);
        final FileCollection preJavacGeneratedBytecode =
                scope.getVariantData().getAllPreJavacGeneratedBytecode();
        final FileCollection postJavacGeneratedBytecode =
                scope.getVariantData().getAllPostJavacGeneratedBytecode();

        // Create the classes artifact for uses by external test modules.
        File dest =
                new File(
                        globalScope.getBuildDir(),
                        FileUtils.join(
                                FD_INTERMEDIATES,
                                "classes-jar",
                                scope.getVariantConfiguration().getDirName()));

        AndroidTask<Jar> task =
                androidTasks.create(
                        tasks,
                        new TaskConfigAction<Jar>() {
                            @NonNull
                            @Override
                            public String getName() {
                                return scope.getTaskName("bundleAppClasses");
                            }

                            @NonNull
                            @Override
                            public Class<Jar> getType() {
                                return Jar.class;
                            }

                            @Override
                            public void execute(@NonNull Jar task) {
                                task.from(javacOutput);
                                task.from(preJavacGeneratedBytecode);
                                task.from(postJavacGeneratedBytecode);
                                task.setDestinationDir(dest);
                                task.setArchiveName("classes.jar");
                            }
                        });

        scope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.APP_CLASSES,
                new File(dest, "classes.jar"),
                task.getName());

        // create a lighter weight version for usage inside the same module (unit tests basically)
        ConfigurableFileCollection fileCollection =
                scope.createAnchorOutput(TaskOutputHolder.AnchorOutputType.ALL_CLASSES);
        fileCollection.from(javacOutput);
        fileCollection.from(preJavacGeneratedBytecode);
        fileCollection.from(postJavacGeneratedBytecode);
    }

    @Override
    protected AndroidTask<? extends DefaultTask> createVariantPreBuildTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        switch (scope.getVariantConfiguration().getType()) {
            case DEFAULT:
                return getAndroidTasks().create(tasks, new AppPreBuildTask.ConfigAction(scope));
            case ANDROID_TEST:
                return getAndroidTasks().create(tasks, new TestPreBuildTask.ConfigAction(scope));
            default:
                return super.createVariantPreBuildTask(tasks, scope);
        }
    }

    @NonNull
    @Override
    protected Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /**
     * Configure variantData to generate embedded wear application.
     */
    private void handleMicroApp(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
        Boolean unbundledWearApp = variantConfiguration.getMergedFlavor().getWearAppUnbundled();

        if (!Boolean.TRUE.equals(unbundledWearApp)
                && variantConfiguration.getBuildType().isEmbedMicroApp()) {
            Configuration wearApp = variantData.getVariantDependency().getWearAppConfiguration();
            assert wearApp != null : "Wear app with no wearApp configuration";
            if (!wearApp.getAllDependencies().isEmpty()) {
                Action<AttributeContainer> setApkArtifact =
                        container -> container.attribute(ARTIFACT_TYPE, APK.getType());
                FileCollection files =
                        wearApp.getIncoming()
                                .artifactView(config -> config.attributes(setApkArtifact))
                                .getFiles();
                createGenerateMicroApkDataTask(tasks, scope, files);
            }
        } else {
            if (Boolean.TRUE.equals(unbundledWearApp)) {
                createGenerateMicroApkDataTask(tasks, scope, null);
            }
        }
    }

    private void createApplicationIdWriterTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {

        File applicationIdOutputDirectory =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "applicationId",
                        variantScope.getVariantConfiguration().getDirName());

        AndroidTask<ApplicationIdWriterTask> writeTask =
                androidTasks.create(
                        tasks,
                        new ApplicationIdWriterTask.ConfigAction(
                                variantScope, applicationIdOutputDirectory));

        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.METADATA_APP_ID_DECLARATION,
                ApplicationId.getOutputFile(applicationIdOutputDirectory),
                writeTask.getName());
    }

}
