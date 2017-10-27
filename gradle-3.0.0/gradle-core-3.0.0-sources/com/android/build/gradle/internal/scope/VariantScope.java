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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.VariantPublishingSpec;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * A scope containing data for a specific variant.
 */
public interface VariantScope extends TransformVariantScope, InstantRunVariantScope,
        TaskOutputHolder {

    @Override
    @NonNull
    GlobalScope getGlobalScope();

    @NonNull
    GradleVariantConfiguration getVariantConfiguration();

    @NonNull
    VariantPublishingSpec getPublishingSpec();

    @NonNull
    BaseVariantData getVariantData();

    @Nullable
    CodeShrinker getCodeShrinker();

    @NonNull
    List<File> getProguardFiles();

    @NonNull
    List<File> getTestProguardFiles();

    @NonNull
    List<File> getConsumerProguardFiles();

    @Nullable
    PostprocessingFeatures getPostprocessingFeatures();

    boolean useResourceShrinker();

    boolean isCrunchPngs();

    @Override
    @NonNull
    InstantRunBuildContext getInstantRunBuildContext();

    boolean isTestOnly();

    boolean isBaseFeature();

    @NonNull
    DexingType getDexingType();

    @NonNull
    AndroidVersion getMinSdkVersion();

    @NonNull
    TransformManager getTransformManager();

    @Nullable
    Collection<Object> getNdkBuildable();

    void setNdkBuildable(@NonNull Collection<Object> ndkBuildable);

    @Nullable
    Collection<File> getNdkSoFolder();

    void setNdkSoFolder(@NonNull Collection<File> ndkSoFolder);

    @Nullable
    File getNdkObjFolder();

    void setNdkObjFolder(@NonNull File ndkObjFolder);

    @Nullable
    File getNdkDebuggableLibraryFolders(@NonNull Abi abi);

    void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath);

    @Nullable
    BaseVariantData getTestedVariantData();

    @NonNull
    File getInstantRunSplitApkOutputFolder();

    @NonNull
    FileCollection getJavaClasspath(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType);

    @NonNull
    FileCollection getJavaClasspath(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey);

    @NonNull
    ArtifactCollection getJavaClasspathArtifacts(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey);

    boolean keepDefaultBootstrap();

    @NonNull
    File getJavaOutputDir();

    @NonNull
    FileCollection getArtifactFileCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType);

    @NonNull
    ArtifactCollection getArtifactCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType);

    @NonNull
    Supplier<Collection<File>> getLocalPackagedJars();

    @NonNull
    FileCollection getProvidedOnlyClasspath();

    @NonNull
    File getIntermediateJarOutputFolder();

    @NonNull
    File getProguardComponentsJarFile();

    @NonNull
    File getManifestKeepListProguardFile();

    @NonNull
    File getMainDexListFile();

    @NonNull
    File getRenderscriptSourceOutputDir();

    @NonNull
    File getRenderscriptLibOutputDir();

    @NonNull
    File getFinalResourcesDir();

    void setResourceOutputDir(@NonNull File resourceOutputDir);

    @NonNull
    File getDefaultMergeResourcesOutputDir();

    @NonNull
    File getMergeResourcesOutputDir();

    void setMergeResourceOutputDir(@Nullable File mergeResourceOutputDir);

    @NonNull
    File getCompiledResourcesOutputDir();

    @NonNull
    File getResourceBlameLogDir();

    @NonNull
    File getMergeNativeLibsOutputDir();

    @NonNull
    File getMergeShadersOutputDir();

    @NonNull
    File getBuildConfigSourceOutputDir();

    @NonNull
    File getGeneratedResOutputDir();

    @NonNull
    File getGeneratedPngsOutputDir();

    @NonNull
    File getRenderscriptResOutputDir();

    @NonNull
    File getRenderscriptObjOutputDir();

    @NonNull
    File getSourceFoldersJavaResDestinationDir();

    @NonNull
    File getJavaResourcesDestinationDir();

    @NonNull
    File getRClassSourceOutputDir();

    @NonNull
    File getAidlSourceOutputDir();

    @NonNull
    File getShadersOutputDir();

    @NonNull
    File getPackagedAidlDir();

    /**
     * Returns the path to an optional recipe file (only used for libraries) which describes
     * typedefs defined in the library, and how to process them (typically which typedefs
     * to omit during packaging).
     */
    @NonNull
    File getTypedefFile();

    /**
     * Returns a place to store incremental build data. The {@code name} argument has to be unique
     * per task, ideally generated with {@link TaskConfigAction#getName()}.
     */
    @NonNull
    File getIncrementalDir(String name);

    @NonNull
    File getCoverageReportDir();

    @NonNull
    File getClassOutputForDataBinding();

    @NonNull
    File getLayoutInfoOutputForDataBinding();

    @NonNull
    File getLayoutFolderOutputForDataBinding();

    @NonNull
    File getLayoutInputFolderForDataBinding();

    @NonNull
    File getBuildFolderForDataBindingCompiler();

    @NonNull
    File getGeneratedClassListOutputFileForDataBinding();

    @NonNull
    File getBundleFolderForDataBinding();

    @NonNull
    File getProguardOutputFolder();

    @NonNull
    File getProcessAndroidResourcesProguardOutputFile();

    @NonNull
    File getGenerateSplitAbiResOutputDirectory();

    @NonNull
    File getSplitDensityOrLanguagesPackagesOutputDirectory();

    @NonNull
    File getSplitAbiPackagesOutputDirectory();

    @NonNull
    File getFullApkPackagesOutputDirectory();

    @NonNull
    File getSplitSupportDirectory();

    @NonNull
    File getAaptFriendlyManifestOutputDirectory();

    @NonNull
    File getInstantRunManifestOutputDirectory();

    @NonNull
    File getMicroApkManifestFile();

    @NonNull
    File getMicroApkResDirectory();

    @NonNull
    File getBaseBundleDir();

    @NonNull
    File getAarLocation();

    @NonNull
    File getAnnotationProcessorOutputDir();

    @NonNull
    File getMainJarOutputDir();

    @NonNull
    File getCompatibleScreensManifestDirectory();

    @NonNull
    File getManifestOutputDirectory();

    @NonNull
    File getApkLocation();

    AndroidTask<? extends ManifestProcessorTask> getManifestProcessorTask();

    void setManifestProcessorTask(
            AndroidTask<? extends ManifestProcessorTask> manifestProcessorTask);

    AndroidTask<DefaultTask> getAssembleTask();

    void setAssembleTask(@NonNull AndroidTask<DefaultTask> assembleTask);

    AndroidTask<? extends DefaultTask> getPreBuildTask();

    void setPreBuildTask(AndroidTask<? extends DefaultTask> preBuildTask);

    AndroidTask<Task> getSourceGenTask();

    void setSourceGenTask(AndroidTask<Task> sourceGenTask);

    AndroidTask<Task> getResourceGenTask();

    void setResourceGenTask(AndroidTask<Task> resourceGenTask);

    AndroidTask<Task> getAssetGenTask();

    void setAssetGenTask(AndroidTask<Task> assetGenTask);

    AndroidTask<CheckManifest> getCheckManifestTask();

    void setCheckManifestTask(AndroidTask<CheckManifest> checkManifestTask);

    AndroidTask<RenderscriptCompile> getRenderscriptCompileTask();

    void setRenderscriptCompileTask(AndroidTask<RenderscriptCompile> renderscriptCompileTask);

    AndroidTask<AidlCompile> getAidlCompileTask();

    void setAidlCompileTask(AndroidTask<AidlCompile> aidlCompileTask);

    @Nullable
    AndroidTask<MergeResources> getMergeResourcesTask();

    void setMergeResourcesTask(@Nullable AndroidTask<MergeResources> mergeResourcesTask);

    @Nullable
    AndroidTask<MergeSourceSetFolders> getMergeAssetsTask();

    void setMergeAssetsTask(@Nullable AndroidTask<MergeSourceSetFolders> mergeAssetsTask);

    AndroidTask<GenerateBuildConfig> getGenerateBuildConfigTask();

    void setGenerateBuildConfigTask(AndroidTask<GenerateBuildConfig> generateBuildConfigTask);

    AndroidTask<Sync> getProcessJavaResourcesTask();

    void setProcessJavaResourcesTask(AndroidTask<Sync> processJavaResourcesTask);

    void setMergeJavaResourcesTask(AndroidTask<TransformTask> mergeJavaResourcesTask);

    AndroidTask<TransformTask> getMergeJavaResourcesTask();

    @Nullable
    AndroidTask<? extends JavaCompile> getJavacTask();

    void setJavacTask(@Nullable AndroidTask<? extends JavaCompile> javacTask);

    AndroidTask<Task> getCompileTask();
    void setCompileTask(AndroidTask<Task> compileTask);

    AndroidTask<GenerateApkDataTask> getMicroApkTask();
    void setMicroApkTask(AndroidTask<GenerateApkDataTask> microApkTask);

    AndroidTask<?> getCoverageReportTask();

    void setCoverageReportTask(AndroidTask<?> coverageReportTask);

    @Nullable
    AndroidTask<ExternalNativeBuildTask> getExternalNativeBuildTask();
    void setExternalNativeBuildTask(@NonNull AndroidTask<ExternalNativeBuildTask> task);

    @Nullable
    ExternalNativeJsonGenerator getExternalNativeJsonGenerator();
    void setExternalNativeJsonGenerator(@NonNull ExternalNativeJsonGenerator generator);

    @NonNull
    Collection<NativeBuildConfigValue> getExternalNativeBuildConfigValues();
    void addExternalNativeBuildConfigValues(@NonNull Collection<NativeBuildConfigValue> values);

    @Nullable
    InstantRunTaskManager getInstantRunTaskManager();
    void setInstantRunTaskManager(InstantRunTaskManager taskManager);

    @NonNull
    File getProcessResourcePackageOutputDirectory();

    void setProcessResourcesTask(
            AndroidTask<ProcessAndroidResources> processAndroidResourcesAndroidTask);

    AndroidTask<ProcessAndroidResources> getProcessResourcesTask();

    void setDataBindingExportBuildInfoTask(AndroidTask<DataBindingExportBuildInfoTask> task);

    AndroidTask<DataBindingExportBuildInfoTask> getDataBindingExportBuildInfoTask();

    @NonNull
    VariantDependencies getVariantDependencies();

    @NonNull
    File getInstantRunResourceApkFolder();

    enum Java8LangSupport {
        INVALID,
        UNUSED,
        DESUGAR,
        RETROLAMBDA,
        DEXGUARD,
    }

    @NonNull
    Java8LangSupport getJava8LangSupportType();

    @NonNull
    DexerTool getDexer();

    @NonNull
    DexMergerTool getDexMerger();

    @NonNull
    ConfigurableFileCollection getTryWithResourceRuntimeSupportJar();
}
