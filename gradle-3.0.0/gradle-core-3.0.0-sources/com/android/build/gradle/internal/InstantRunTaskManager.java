/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.transforms.InstantRunDex;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import com.android.build.gradle.internal.transforms.InstantRunTransform;
import com.android.build.gradle.internal.transforms.InstantRunVerifierTransform;
import com.android.build.gradle.internal.transforms.NoChangesVerifierTransform;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.CheckManifestInInstantRunMode;
import com.android.build.gradle.tasks.PreColdSwapTask;
import com.android.build.gradle.tasks.ir.FastDeployRuntimeExtractorTask;
import com.android.build.gradle.tasks.ir.GenerateInstantRunAppInfoTask;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.profile.Recorder;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskState;

/**
 * Task Manager for InstantRun related transforms configuration and tasks handling.
 */
public class InstantRunTaskManager {

    @Nullable private AndroidTask<TransformTask> verifierTask;
    @Nullable private AndroidTask<TransformTask> reloadDexTask;
    @Nullable private AndroidTask<BuildInfoLoaderTask> buildInfoLoaderTask;

    @NonNull
    private final Logger logger;

    @NonNull
    private final InstantRunVariantScope variantScope;

    @NonNull
    private final TransformManager transformManager;

    @NonNull
    private final AndroidTaskRegistry androidTasks;

    @NonNull
    private final TaskFactory tasks;
    @NonNull private final Recorder recorder;

    public InstantRunTaskManager(
            @NonNull Logger logger,
            @NonNull InstantRunVariantScope instantRunVariantScope,
            @NonNull TransformManager transformManager,
            @NonNull AndroidTaskRegistry androidTasks,
            @NonNull TaskFactory tasks,
            @NonNull Recorder recorder) {
        this.logger = logger;
        this.variantScope = instantRunVariantScope;
        this.transformManager = transformManager;
        this.androidTasks = androidTasks;
        this.tasks = tasks;
        this.recorder = recorder;
    }

    public AndroidTask<BuildInfoLoaderTask> createInstantRunAllTasks(
            DexOptions dexOptions,
            @NonNull Supplier<DexByteCodeConverter> dexByteCodeConverter,
            @Nullable AndroidTask<?> preTask,
            AndroidTask<?> anchorTask,
            Set<? super QualifiedContent.Scope> resMergingScopes,
            FileCollection instantRunMergedManifests,
            FileCollection processedResources,
            boolean addDependencyChangeChecker,
            int minSdkForDx) {
        final Project project = variantScope.getGlobalScope().getProject();

        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();

        buildInfoLoaderTask = androidTasks.create(tasks,
                new BuildInfoLoaderTask.ConfigAction(variantScope, logger));

        // always run the verifier first, since if it detects incompatible changes, we
        // should skip bytecode enhancements of the changed classes.
        InstantRunVerifierTransform verifierTransform =
                new InstantRunVerifierTransform(variantScope, recorder);
        Optional<AndroidTask<TransformTask>> verifierTaskOptional =
                transformManager.addTransform(tasks, transformVariantScope, verifierTransform);
        verifierTask = verifierTaskOptional.orElse(null);
        verifierTaskOptional.ifPresent(t -> t.optionalDependsOn(tasks, preTask));

        NoChangesVerifierTransform javaResourcesVerifierTransform =
                new NoChangesVerifierTransform(
                        "javaResourcesVerifier",
                        variantScope.getInstantRunBuildContext(),
                        ImmutableSet.of(
                                QualifiedContent.DefaultContentType.RESOURCES,
                                ExtendedContentType.NATIVE_LIBS),
                        resMergingScopes,
                        InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED);

        Optional<AndroidTask<TransformTask>> javaResourcesVerifierTask =
                transformManager.addTransform(
                        tasks, transformVariantScope, javaResourcesVerifierTransform);
        javaResourcesVerifierTask.ifPresent(t -> t.optionalDependsOn(tasks, verifierTask));

        InstantRunTransform instantRunTransform = new InstantRunTransform(
                WaitableExecutor.useGlobalSharedThreadPool(),
                variantScope);
        Optional<AndroidTask<TransformTask>> instantRunTask =
                transformManager.addTransform(tasks, transformVariantScope, instantRunTransform);

        // create the manifest file change checker. This task should always run even if the
        // processAndroidResources task did not run. It is possible (through an IDE sync mainly)
        // that the processAndroidResources task ran in a previous non InstantRun enabled
        // invocation.
        AndroidTask<CheckManifestInInstantRunMode> checkManifestTask =
                androidTasks.create(
                        tasks,
                        new CheckManifestInInstantRunMode.ConfigAction(
                                transformVariantScope,
                                variantScope,
                                instantRunMergedManifests,
                                processedResources));

        instantRunTask.ifPresent(t ->
                t.dependsOn(
                        tasks,
                        buildInfoLoaderTask,
                        verifierTask,
                        javaResourcesVerifierTask.orElse(null),
                        checkManifestTask));

        if (addDependencyChangeChecker) {
            NoChangesVerifierTransform dependenciesVerifierTransform =
                    new NoChangesVerifierTransform(
                            "dependencyChecker",
                            variantScope.getInstantRunBuildContext(),
                            ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                            Sets.immutableEnumSet(QualifiedContent.Scope.EXTERNAL_LIBRARIES),
                            InstantRunVerifierStatus.DEPENDENCY_CHANGED);
            Optional<AndroidTask<TransformTask>> dependenciesVerifierTask =
                    transformManager.addTransform(
                            tasks, transformVariantScope, dependenciesVerifierTransform);
            dependenciesVerifierTask.ifPresent(t -> t.optionalDependsOn(tasks, verifierTask));
            instantRunTask.ifPresent(t ->
                    t.optionalDependsOn(tasks, dependenciesVerifierTask.orElse(null)));
        }


        AndroidTask<FastDeployRuntimeExtractorTask> extractorTask = androidTasks.create(
                tasks, new FastDeployRuntimeExtractorTask.ConfigAction(variantScope));
        extractorTask.dependsOn(tasks, buildInfoLoaderTask);

        // also add a new stream for the extractor task output.
        transformManager.addStream(
                OriginalStream.builder(project, "main-split-from-extractor")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(InternalScope.MAIN_SPLIT)
                        .setJar(variantScope.getIncrementalRuntimeSupportJar())
                        .setDependency(extractorTask.get(tasks))
                        .build());

        AndroidTask<GenerateInstantRunAppInfoTask> generateAppInfoAndroidTask =
                androidTasks.create(
                        tasks,
                        new GenerateInstantRunAppInfoTask.ConfigAction(
                                transformVariantScope, variantScope, instantRunMergedManifests));

        // create the AppInfo.class for this variant.
        // TODO: remove this get() as it forces task creation.
        GenerateInstantRunAppInfoTask generateInstantRunAppInfoTask =
                generateAppInfoAndroidTask.get(tasks);

        // also add a new stream for the injector task output.
        transformManager.addStream(
                OriginalStream.builder(project, "main-split-from-injector")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(InternalScope.MAIN_SPLIT)
                        .setJar(generateInstantRunAppInfoTask.getOutputFile())
                        .setDependency(generateInstantRunAppInfoTask)
                        .build());

        anchorTask.optionalDependsOn(tasks, instantRunTask.orElse(null));

        // we always produce the reload.dex irrespective of the targeted version,
        // and if we are not in incremental mode, we need to still need to clean our output state.
        InstantRunDex reloadDexTransform =
                new InstantRunDex(
                        variantScope, dexByteCodeConverter, dexOptions, logger, minSdkForDx);

        reloadDexTask =
                transformManager
                        .addTransform(tasks, transformVariantScope, reloadDexTransform)
                        .orElse(null);
        anchorTask.optionalDependsOn(tasks, reloadDexTask);


        return buildInfoLoaderTask;
    }

    /** Creates all InstantRun related transforms after compilation. */
    @NonNull
    public AndroidTask<PreColdSwapTask> createPreColdswapTask(
            @NonNull ProjectOptions projectOptions) {

        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();
        InstantRunBuildContext context = variantScope.getInstantRunBuildContext();

        if (transformVariantScope.getGlobalScope().isActive(OptionalCompilationStep.FULL_APK)) {
            context.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        } else if (transformVariantScope.getGlobalScope().isActive(
                OptionalCompilationStep.RESTART_ONLY)) {
            context.setVerifierStatus(InstantRunVerifierStatus.COLD_SWAP_REQUESTED);
        }

        AndroidTask<PreColdSwapTask> preColdSwapTask = androidTasks.create(
                tasks, new PreColdSwapTask.ConfigAction("preColdswap",
                        transformVariantScope,
                        variantScope));

        preColdSwapTask.optionalDependsOn(tasks, verifierTask);

        return preColdSwapTask;
    }


    /**
     * If we are at API 21 or above, we generate multi-dexes.
     */
    public void createSlicerTask() {
        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();
        //
        InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);
        Optional<AndroidTask<TransformTask>> slicing =
                transformManager.addTransform(tasks, transformVariantScope, slicer);
        slicing.ifPresent(variantScope::addColdSwapBuildTask);
    }

    /**
     * Configures the task to save the build-info.xml and sets its dependencies for instant run.
     *
     * <p>This task does not depend on other tasks, so if previous tasks fails it will still run.
     * Instead the read build info task is {@link Task#finalizedBy(Object...)} the write build info
     * task, so whenever the read task runs the write task must also run.
     *
     * <p>It also {@link Task#mustRunAfter(Object...)} the various build types so that it runs after
     * those tasks, but runs even if those tasks fail. Using {@link Task#dependsOn(Object...)}
     * would not run the task if a previous task failed.
     *
     * @param buildInfoWriterTask the task instance.
     */
    public void configureBuildInfoWriterTask(
            @NonNull AndroidTask<BuildInfoWriterTask> buildInfoWriterTask,
            AndroidTask<?>... dependencies) {
        Preconditions.checkNotNull(buildInfoLoaderTask,
                "createInstantRunAllTasks() should have been called first ");
        buildInfoLoaderTask.configure(
                tasks,
                readerTask -> readerTask.finalizedBy(buildInfoWriterTask.getName()));

        buildInfoWriterTask.configure(tasks, writerTask -> {
            if (reloadDexTask != null) {
                writerTask.mustRunAfter(reloadDexTask.getName());
            }
            if (dependencies != null) {
                for (AndroidTask<?> dependency : dependencies) {
                    writerTask.mustRunAfter(dependency.getName());
                }
            }
        });

        // Register a task execution listener to allow the writer task to write the temp build info
        // on build failure, which will get merged into the next build.
        variantScope
                .getGlobalScope()
                .getProject()
                .getGradle()
                .getTaskGraph()
                .addTaskExecutionListener(
                        new TaskExecutionAdapter() {
                            @Override
                            public void afterExecute(Task task, TaskState state) {
                                //noinspection ThrowableResultOfMethodCallIgnored
                                if (state.getFailure() != null) {
                                    variantScope.getInstantRunBuildContext().setBuildHasFailed();
                                }
                            }
                        });
    }

}
