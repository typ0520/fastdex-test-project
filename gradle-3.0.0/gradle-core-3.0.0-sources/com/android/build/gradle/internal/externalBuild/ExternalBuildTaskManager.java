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

package com.android.build.gradle.internal.externalBuild;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.VariantOutput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.ExtractJarsTransform;
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder;
import com.android.build.gradle.internal.transforms.PreDexTransform;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.PreColdSwapTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.dexing.DexingType;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.profile.Recorder;
import com.android.builder.signing.DefaultSigningConfig;
import com.android.ide.common.build.ApkData;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;
import org.apache.commons.io.Charsets;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Task Manager for External Build system integration.
 */
class ExternalBuildTaskManager {

    @NonNull private final ExternalBuildGlobalScope globalScope;
    @NonNull private final Project project;
    @NonNull private final AndroidTaskRegistry androidTasks;
    @NonNull private final TaskContainerAdaptor tasks;
    @NonNull private final Recorder recorder;

    ExternalBuildTaskManager(
            @NonNull ExternalBuildGlobalScope globalScope,
            @NonNull Project project,
            @NonNull Recorder recorder) {
        this.globalScope = globalScope;
        this.project = project;
        this.tasks = new TaskContainerAdaptor(project.getTasks());
        this.recorder = recorder;
        this.androidTasks = new AndroidTaskRegistry();
    }

    void createTasks(@NonNull ExternalBuildExtension externalBuildExtension) throws Exception {

        // anchor task
        AndroidTask<ExternalBuildAnchorTask> externalBuildAnchorTask =
                androidTasks.create(tasks, new ExternalBuildAnchorTask.ConfigAction());

        ExternalBuildContext externalBuildContext = new ExternalBuildContext(
                externalBuildExtension);

        File file = project.file(externalBuildExtension.buildManifestPath);
        ExternalBuildManifestLoader.loadAndPopulateContext(
                new File(externalBuildExtension.getExecutionRoot()),
                file,
                project,
                globalScope.getProjectOptions(),
                externalBuildContext);

        ExtraModelInfo modelInfo =
                new ExtraModelInfo(globalScope.getProjectOptions(), project.getLogger());
        TransformManager transformManager = new TransformManager(
                project, androidTasks, modelInfo, recorder);

        transformManager.addStream(
                OriginalStream.builder(project, "project-classes")
                        .addContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .addScope(QualifiedContent.Scope.PROJECT)
                        .setJars(externalBuildContext::getInputJarFiles)
                        .build());

        // add an empty java resources directory for now.
        // the folder itself doesn't actually matter, but it has to be consistent
        // for gradle's up-to-date check
        transformManager.addStream(
                OriginalStream.builder(project, "project-res")
                        .addContentType(QualifiedContent.DefaultContentType.RESOURCES)
                        .addScope(QualifiedContent.Scope.PROJECT)
                        .setFolder(new File(project.getBuildDir(), "temp/streams/resources"))
                        .build());

        // add an empty native libraries resources directory for now.
        // the folder itself doesn't actually matter, but it has to be consistent
        // for gradle's up-to-date check
        transformManager.addStream(
                OriginalStream.builder(project, "project-jni–libs")
                        .addContentType(ExtendedContentType.NATIVE_LIBS)
                        .addScope(QualifiedContent.Scope.PROJECT)
                        .setFolder(new File(project.getBuildDir(), "temp/streams/native_libs"))
                        .build());

        File androidManifestFile =
                new File(externalBuildContext.getExecutionRoot(),
                        externalBuildContext
                                .getBuildManifest()
                                .getAndroidManifest()
                                .getExecRootPath());

        File processedAndroidResourcesFile =
                new File(externalBuildContext.getExecutionRoot(),
                        externalBuildContext.getBuildManifest().getResourceApk().getExecRootPath());

        ApkData mainApkData =
                new OutputFactory.DefaultApkData(
                        VariantOutput.OutputType.MAIN,
                        "",
                        "main",
                        "main",
                        "main",
                        "debug.apk",
                        ImmutableList.of());

        ExternalBuildVariantScope variantScope =
                new ExternalBuildVariantScope(
                        globalScope,
                        project.getBuildDir(),
                        externalBuildContext,
                        new AaptOptions(null, false, null),
                        new DefaultManifestParser(androidManifestFile),
                        ImmutableList.of(mainApkData));

        // massage the manifest file.

        // Extract the passed jars into folders as the InstantRun transforms can only handle folders.
        ExtractJarsTransform extractJarsTransform = new ExtractJarsTransform(
                ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                ImmutableSet.of(QualifiedContent.Scope.PROJECT));
        Optional<AndroidTask<TransformTask>> extractJarsTask =
                transformManager.addTransform(tasks, variantScope, extractJarsTransform);

        InstantRunTaskManager instantRunTaskManager =
                new InstantRunTaskManager(
                        project.getLogger(),
                        variantScope,
                        transformManager,
                        androidTasks,
                        tasks,
                        recorder);

        // create output.json for the provided built artifacts.
        File manifest =
                createBuildOutputs(
                        androidManifestFile.getParentFile(),
                        mainApkData,
                        TaskOutputHolder.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                        androidManifestFile);

        File resources =
                createBuildOutputs(
                        processedAndroidResourcesFile.getParentFile(),
                        mainApkData,
                        TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                        processedAndroidResourcesFile);

        AndroidTask<BuildInfoLoaderTask> buildInfoLoaderTask =
                instantRunTaskManager.createInstantRunAllTasks(
                        new DexOptions(modelInfo),
                        externalBuildContext.getAndroidBuilder()::getDexByteCodeConverter,
                        extractJarsTask.orElse(null),
                        externalBuildAnchorTask,
                        EnumSet.of(QualifiedContent.Scope.PROJECT),
                        project.files(manifest),
                        project.files(resources),
                        false /* addResourceVerifier */,
                        1);

        extractJarsTask.ifPresent(t -> t.dependsOn(tasks, buildInfoLoaderTask));

        AndroidTask<PreColdSwapTask> preColdswapTask =
                instantRunTaskManager.createPreColdswapTask(globalScope.getProjectOptions());

        if (variantScope.getInstantRunBuildContext().getPatchingPolicy()
                != InstantRunPatchingPolicy.PRE_LOLLIPOP) {
            instantRunTaskManager.createSlicerTask();
        }

        createDexTasks(externalBuildContext, transformManager, variantScope);

        SigningConfig manifestSigningConfig = createManifestSigningConfig(externalBuildContext);

        PackagingScope packagingScope =
                new ExternalBuildPackagingScope(
                        project, externalBuildContext, variantScope, transformManager,
                        manifestSigningConfig);

        OutputScope outputScope = packagingScope.getOutputScope();
        outputScope.addOutputForSplit(
                TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, mainApkData, androidManifestFile);
        outputScope.addOutputForSplit(
                TaskOutputHolder.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                mainApkData,
                androidManifestFile);
        outputScope.addOutputForSplit(
                TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                mainApkData,
                processedAndroidResourcesFile);

        packagingScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, androidManifestFile, null);

        // TODO: Where should assets come from?
        // For now, we need to fake an assets task output since we don't have one, use a
        // non-existing folder name.
        File assetsFolder = FileUtils.join(project.getBuildDir(), "__external_assets__");
        packagingScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.MERGED_ASSETS, assetsFolder, null);

        Logger logger = Logging.getLogger(ExternalBuildTaskManager.class);

        InstantRunSliceSplitApkBuilder slicesApkBuilder =
                new InstantRunSliceSplitApkBuilder(
                        logger,
                        project,
                        variantScope.getInstantRunBuildContext(),
                        externalBuildContext.getAndroidBuilder(),
                        globalScope.getBuildCache(),
                        packagingScope,
                        packagingScope.getSigningConfig(),
                        AaptGeneration.fromProjectOptions(globalScope.getProjectOptions()),
                        packagingScope.getAaptOptions(),
                        packagingScope.getInstantRunSplitApkOutputFolder(),
                        packagingScope.getInstantRunSupportDir(),
                        new File(
                                packagingScope.getIncrementalDir("InstantRunSliceSplitApkBuilder"),
                                "aapt-temp"),
                        null);

        Optional<AndroidTask<TransformTask>> transformTaskAndroidTask =
                transformManager.addTransform(tasks, variantScope, slicesApkBuilder);

        AndroidTask<PackageApplication> packageApp =
                androidTasks.create(
                        tasks,
                        new PackageApplication.StandardConfigAction(
                                packagingScope,
                                FileUtils.join(globalScope.getBuildDir(), "outputs", "apk"),
                                variantScope.getInstantRunBuildContext().getPatchingPolicy(),
                                VariantScope.TaskOutputType.MERGED_RES,
                                project.files(processedAndroidResourcesFile),
                                project.files(androidManifestFile),
                                VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                                variantScope.getOutputScope(),
                                globalScope.getBuildCache(),
                                TaskOutputHolder.TaskOutputType.APK));

        transformTaskAndroidTask.ifPresent(
                transformTaskAndroidTask1 ->
                        packageApp.dependsOn(tasks, transformTaskAndroidTask1));

        variantScope.setPackageApplicationTask(packageApp);

        AndroidTask<BuildInfoWriterTask> buildInfoWriterTask = androidTasks.create(tasks,
                new BuildInfoWriterTask.ConfigAction(variantScope, logger));

        // finally, generate the build-info.xml
        instantRunTaskManager.configureBuildInfoWriterTask(buildInfoWriterTask, packageApp);

        externalBuildAnchorTask.dependsOn(tasks, packageApp);
        externalBuildAnchorTask.dependsOn(tasks, buildInfoWriterTask);

        for (AndroidTask<? extends DefaultTask> task : variantScope.getColdSwapBuildTasks()) {
            task.dependsOn(tasks, preColdswapTask);
        }
    }

    private File createBuildOutputs(
            File parentFolder,
            ApkData apkData,
            TaskOutputHolder.TaskOutputType outputType,
            File file)
            throws IOException {

        File output = new File(parentFolder, outputType.name());
        FileUtils.mkdirs(output);
        BuildOutput buildOutput = new BuildOutput(outputType, apkData, file);
        String buildOutputs =
                BuildOutputs.persist(
                        output.toPath(),
                        ImmutableList.of(outputType),
                        ImmutableSetMultimap.of(outputType, buildOutput));
        Files.write(buildOutputs, BuildOutputs.getMetadataFile(output), Charsets.UTF_8);
        return output;
    }

    private void createDexTasks(
            @NonNull ExternalBuildContext externalBuildContext,
            @NonNull TransformManager transformManager,
            @NonNull ExternalBuildVariantScope variantScope) {
        AndroidBuilder androidBuilder = externalBuildContext.getAndroidBuilder();
        final DexingType dexingType = DexingType.NATIVE_MULTIDEX;

        PreDexTransform preDexTransform =
                new PreDexTransform(
                        new DefaultDexOptions(),
                        androidBuilder,
                        variantScope.getGlobalScope().getBuildCache(),
                        dexingType,
                        1);
        transformManager.addTransform(tasks, variantScope, preDexTransform);
    }

    private static SigningConfig createManifestSigningConfig(
            ExternalBuildContext externalBuildContext) {
        SigningConfig config = new SigningConfig(BuilderConstants.EXTERNAL_BUILD);
        config.setStorePassword(DefaultSigningConfig.DEFAULT_PASSWORD);
        config.setKeyAlias(DefaultSigningConfig.DEFAULT_ALIAS);
        config.setKeyPassword(DefaultSigningConfig.DEFAULT_PASSWORD);

        File keystore =
                new File(
                        externalBuildContext.getExecutionRoot(),
                        externalBuildContext
                                .getBuildManifest()
                                .getDebugKeystore()
                                .getExecRootPath());
        checkState(
                keystore.isFile(),
                "Keystore file from the manifest (%s) does not exist.",
                keystore.getAbsolutePath());
        config.setStoreFile(keystore);

        return config;
    }
}
