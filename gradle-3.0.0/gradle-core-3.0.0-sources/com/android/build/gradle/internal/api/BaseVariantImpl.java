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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.attributes.ProductFlavorAttr;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.api.SourceKind;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.BuildType;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Base class for variants.
 *
 * <p>This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public abstract class BaseVariantImpl implements BaseVariant {

    @NonNull private final ObjectFactory objectFactory;
    @NonNull protected final AndroidBuilder androidBuilder;

    @NonNull protected final ReadOnlyObjectProvider readOnlyObjectProvider;

    @NonNull protected final NamedDomainObjectContainer<BaseVariantOutput> outputs;

    BaseVariantImpl(
            @NonNull ObjectFactory objectFactory,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        this.objectFactory = objectFactory;
        this.androidBuilder = androidBuilder;
        this.readOnlyObjectProvider = readOnlyObjectProvider;
        this.outputs = outputs;
    }

    @NonNull
    protected abstract BaseVariantData getVariantData();

    public void addOutputs(@NonNull List<BaseVariantOutput> outputs) {
       this.outputs.addAll(outputs);
    }

    @Override
    @NonNull
    public String getName() {
        return getVariantData().getVariantConfiguration().getFullName();
    }

    @Override
    @NonNull
    public String getDescription() {
        return getVariantData().getDescription();
    }

    @Override
    @NonNull
    public String getDirName() {
        return getVariantData().getVariantConfiguration().getDirName();
    }

    @Override
    @NonNull
    public String getBaseName() {
        return getVariantData().getVariantConfiguration().getBaseName();
    }

    @NonNull
    @Override
    public String getFlavorName() {
        return getVariantData().getVariantConfiguration().getFlavorName();
    }

    @NonNull
    @Override
    public DomainObjectCollection<BaseVariantOutput> getOutputs() {
        return outputs;
    }

    @Override
    @NonNull
    public BuildType getBuildType() {
        return readOnlyObjectProvider.getBuildType(
                getVariantData().getVariantConfiguration().getBuildType());
    }

    @Override
    @NonNull
    public List<ProductFlavor> getProductFlavors() {
        return new ImmutableFlavorList(
                getVariantData().getVariantConfiguration().getProductFlavors(),
                readOnlyObjectProvider);
    }

    @Override
    @NonNull
    public ProductFlavor getMergedFlavor() {
        return getVariantData().getVariantConfiguration().getMergedFlavor();
    }

    @NonNull
    @Override
    public JavaCompileOptions getJavaCompileOptions() {
        return getVariantData().getVariantConfiguration().getJavaCompileOptions();
    }

    @NonNull
    @Override
    public List<SourceProvider> getSourceSets() {
        return getVariantData().getVariantConfiguration().getSortedSourceProviders();
    }

    @NonNull
    @Override
    public List<ConfigurableFileTree> getSourceFolders(@NonNull SourceKind folderType) {
        switch (folderType) {
            case JAVA:
                return getVariantData().getJavaSources();
            default:
                androidBuilder
                        .getErrorReporter()
                        .handleSyncError(
                                null,
                                SyncIssue.TYPE_GENERIC,
                                "Unknown SourceKind value: " + folderType);
        }

        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Configuration getCompileConfiguration() {
        return getVariantData().getVariantDependency().getCompileClasspath();
    }

    @NonNull
    @Override
    public Configuration getRuntimeConfiguration() {
        return getVariantData().getVariantDependency().getRuntimeClasspath();
    }

    @NonNull
    @Override
    public Configuration getAnnotationProcessorConfiguration() {
        return getVariantData().getVariantDependency().getAnnotationProcessorConfiguration();
    }

    @Override
    @NonNull
    public String getApplicationId() {
        return getVariantData().getApplicationId();
    }

    @Override
    @NonNull
    public Task getPreBuild() {
        return getVariantData().preBuildTask;
    }

    @Override
    @NonNull
    public Task getCheckManifest() {
        return getVariantData().checkManifestTask;
    }

    @Override
    @NonNull
    public AidlCompile getAidlCompile() {
        return getVariantData().aidlCompileTask;
    }

    @Override
    @NonNull
    public RenderscriptCompile getRenderscriptCompile() {
        return getVariantData().renderscriptCompileTask;
    }

    @Override
    public MergeResources getMergeResources() {
        return getVariantData().mergeResourcesTask;
    }

    @Override
    public MergeSourceSetFolders getMergeAssets() {
        return getVariantData().mergeAssetsTask;
    }

    @Override
    public GenerateBuildConfig getGenerateBuildConfig() {
        return getVariantData().generateBuildConfigTask;
    }

    @Override
    @Nullable
    public JavaCompile getJavaCompile() {
        return getVariantData().javacTask;
    }

    @NonNull
    @Override
    public Task getJavaCompiler() {
        return getVariantData().javacTask;
    }

    @NonNull
    @Override
    public NdkCompile getNdkCompile() {
        return getVariantData().ndkCompileTask;
    }

    @Override
    public Collection<ExternalNativeBuildTask> getExternalNativeBuildTasks() {
        return getVariantData().externalNativeBuildTasks;
    }

    @Nullable
    @Override
    public Task getObfuscation() {
        return getVariantData().obfuscationTask;
    }

    @Nullable
    @Override
    public File getMappingFile() {
        VariantScope scope = getVariantData().getScope();
        if (scope.hasOutput(TaskOutputHolder.TaskOutputType.APK_MAPPING)) {
            return scope.getOutput(TaskOutputHolder.TaskOutputType.APK_MAPPING).getSingleFile();
        }
        return null;
    }

    @Override
    @NonNull
    public Sync getProcessJavaResources() {
        return getVariantData().processJavaResourcesTask;
    }

    @Override
    @Nullable
    public Task getAssemble() {
        return getVariantData().getTaskByKind(TaskContainer.TaskKind.ASSEMBLE);
    }

    @Override
    public void addJavaSourceFoldersToModel(@NonNull File... generatedSourceFolders) {
        getVariantData().addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    @Override
    public void addJavaSourceFoldersToModel(@NonNull Collection<File> generatedSourceFolders) {
        getVariantData().addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    @Override
    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... sourceFolders) {
        getVariantData().registerJavaGeneratingTask(task, sourceFolders);
    }

    @Override
    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> sourceFolders) {
        getVariantData().registerJavaGeneratingTask(task, sourceFolders);
    }

    @Override
    public void registerExternalAptJavaOutput(@NonNull ConfigurableFileTree folder) {
        getVariantData().registerExternalAptJavaOutput(folder);
    }

    @Override
    public void registerGeneratedResFolders(@NonNull FileCollection folders) {
        getVariantData().registerGeneratedResFolders(folders);
    }

    @Override
    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull File... generatedResFolders) {
        getVariantData().registerResGeneratingTask(task, generatedResFolders);
    }

    @Override
    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedResFolders) {
        getVariantData().registerResGeneratingTask(task, generatedResFolders);
    }

    @Override
    public Object registerPreJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        return getVariantData().registerPreJavacGeneratedBytecode(fileCollection);
    }

    @Override
    @Deprecated
    public Object registerGeneratedBytecode(@NonNull FileCollection fileCollection) {
        return registerPreJavacGeneratedBytecode(fileCollection);
    }

    @Override
    public void registerPostJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        getVariantData().registerPostJavacGeneratedBytecode(fileCollection);
    }

    @NonNull
    @Override
    public FileCollection getCompileClasspath(@Nullable Object generatorKey) {
        return getVariantData()
                .getScope()
                .getJavaClasspath(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactType.CLASSES,
                        generatorKey);
    }

    @NonNull
    @Override
    public ArtifactCollection getCompileClasspathArtifacts(@Nullable Object generatorKey) {
        return getVariantData()
                .getScope()
                .getJavaClasspathArtifacts(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactType.CLASSES,
                        generatorKey);
    }

    @NonNull
    @Override
    public FileCollection getDataBindingDependencyArtifacts() {
        VariantScope scope = getVariantData().getScope();
        if (scope.hasOutput(TaskOutputHolder.TaskOutputType.DATA_BINDING_DEPENDENCY_ARTIFACTS)) {
            return scope.getOutput(
                    TaskOutputHolder.TaskOutputType.DATA_BINDING_DEPENDENCY_ARTIFACTS);
        }

        return scope.getGlobalScope().getProject().files();
    }

    @Override
    public void buildConfigField(
            @NonNull String type, @NonNull String name, @NonNull String value) {
        getVariantData().getVariantConfiguration().addBuildConfigField(type, name, value);
    }

    @Override
    public void resValue(@NonNull String type, @NonNull String name, @NonNull String value) {
        getVariantData().getVariantConfiguration().addResValue(type, name, value);
    }


    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull String requestedValue) {
        _missingDimensionStrategy(dimension, ImmutableList.of(requestedValue));
    }

    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull String... requestedValues) {
        _missingDimensionStrategy(dimension, ImmutableList.copyOf(requestedValues));
    }

    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull List<String> requestedValues) {
        _missingDimensionStrategy(dimension, ImmutableList.copyOf(requestedValues));
    }

    private void _missingDimensionStrategy(
            @NonNull String dimension, @NonNull ImmutableList<String> alternatedValues) {
        final VariantScope variantScope = getVariantData().getScope();

        // First, setup the requested value, which isn't the actual requested value, but
        // the variant name, modified
        final String requestedValue = VariantManager.getModifiedName(getName());

        final Attribute<ProductFlavorAttr> attributeKey =
                Attribute.of(dimension, ProductFlavorAttr.class);
        final ProductFlavorAttr attributeValue =
                objectFactory.named(ProductFlavorAttr.class, requestedValue);

        VariantDependencies dependencies = variantScope.getVariantDependencies();
        dependencies.getCompileClasspath().getAttributes().attribute(attributeKey, attributeValue);
        dependencies.getRuntimeClasspath().getAttributes().attribute(attributeKey, attributeValue);
        dependencies
                .getAnnotationProcessorConfiguration()
                .getAttributes()
                .attribute(attributeKey, attributeValue);

        // then add the fallbacks which contain the actual requested value
        AttributesSchema schema =
                variantScope.getGlobalScope().getProject().getDependencies().getAttributesSchema();

        VariantManager.addFlavorStrategy(
                schema, dimension, ImmutableMap.of(requestedValue, alternatedValues));
    }

    @Override
    public void setOutputsAreSigned(boolean isSigned) {
        getVariantData().outputsAreSigned = isSigned;
    }

    @Override
    public boolean getOutputsAreSigned() {
        return getVariantData().outputsAreSigned;
    }
}
