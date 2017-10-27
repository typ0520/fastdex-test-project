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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.ide.common.build.ApkData;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * Implementation of {@link PackagingScope} which delegates to *Scope objects available during
 * normal Gradle builds.
 */
public class DefaultGradlePackagingScope implements PackagingScope {

    private final VariantScope mVariantScope;
    private final GlobalScope mGlobalScope;

    public DefaultGradlePackagingScope(@NonNull VariantScope variantScope) {
        mVariantScope = variantScope;
        mGlobalScope = mVariantScope.getGlobalScope();
    }

    @NonNull
    @Override
    public AndroidBuilder getAndroidBuilder() {
        return mGlobalScope.getAndroidBuilder();
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return mVariantScope.getFullVariantName();
    }

    @NonNull
    @Override
    public AndroidVersion getMinSdkVersion() {
        return mVariantScope.getMinSdkVersion();
    }

    @NonNull
    @Override
    public InstantRunBuildContext getInstantRunBuildContext() {
        return mVariantScope.getInstantRunBuildContext();
    }

    @NonNull
    @Override
    public File getInstantRunSupportDir() {
        return mVariantScope.getInstantRunSupportDir();
    }

    @NonNull
    @Override
    public File getIncrementalDir(@NonNull String name) {
        return mVariantScope.getIncrementalDir(name);
    }

    @NonNull
    @Override
    public FileCollection getDexFolders() {
        return mVariantScope
                .getTransformManager()
                .getPipelineOutputAsFileCollection(StreamFilter.DEX);
    }


    @NonNull
    @Override
    public FileCollection getJavaResources() {
        return mVariantScope.getTransformManager()
                .getPipelineOutputAsFileCollection(StreamFilter.RESOURCES);
    }

    @NonNull
    @Override
    public FileCollection getJniFolders() {
        return mVariantScope.getTransformManager()
                .getPipelineOutputAsFileCollection(StreamFilter.NATIVE_LIBS);
    }

    @NonNull
    @Override
    public MultiOutputPolicy getMultiOutputPolicy() {
        return mVariantScope.getVariantData().getOutputScope().getMultiOutputPolicy();
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return mGlobalScope.getExtension().getSplits().getAbiFilters();
    }

    @Nullable
    @Override
    public Set<String> getSupportedAbis() {
        return mVariantScope.getVariantConfiguration().getSupportedAbis();
    }

    @Override
    public boolean isDebuggable() {
        return mVariantScope.getVariantConfiguration().getBuildType().isDebuggable();
    }

    @Override
    public boolean isJniDebuggable() {
        return mVariantScope.getVariantConfiguration().getBuildType().isJniDebuggable();
    }

    @Nullable
    @Override
    public CoreSigningConfig getSigningConfig() {
        return mVariantScope.getVariantConfiguration().getSigningConfig();
    }

    @NonNull
    @Override
    public PackagingOptions getPackagingOptions() {
        return mGlobalScope.getExtension().getPackagingOptions();
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String name) {
        return mVariantScope.getTaskName(name);
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return mVariantScope.getTaskName(prefix, suffix);
    }

    @NonNull
    @Override
    public Project getProject() {
        return mGlobalScope.getProject();
    }

    @Override
    public String getProjectBaseName() {
        return mGlobalScope.getProjectBaseName();
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return mVariantScope.getInstantRunSplitApkOutputFolder();
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return mVariantScope.getVariantConfiguration().getApplicationId();
    }

    @Override
    public int getVersionCode() {
        // FIX ME : DELETE this API and have everyone use the concept of mainSplit.
        ApkData mainApkData = mVariantScope.getOutputScope().getMainSplit();
        if (mainApkData != null) {
            return mainApkData.getVersionCode();
        }
        return mVariantScope.getVariantConfiguration().getVersionCode();
    }

    @Nullable
    @Override
    public String getVersionName() {
        return mVariantScope.getVariantConfiguration().getVersionName();
    }

    @NonNull
    @Override
    public AaptOptions getAaptOptions() {
        return DslAdaptersKt.convert(mGlobalScope.getExtension().getAaptOptions());
    }

    @Override
    public ProjectOptions getProjectOptions() {
        return mGlobalScope.getProjectOptions();
    }

    @Override
    public OutputScope getOutputScope() {
        return mVariantScope.getOutputScope();
    }

    // TaskOutputHolder

    @NonNull
    @Override
    public FileCollection getOutput(@NonNull OutputType outputType) {
        return mVariantScope.getOutput(outputType);
    }

    @Override
    public boolean hasOutput(@NonNull OutputType outputType) {
        return mVariantScope.hasOutput(outputType);
    }

    @Override
    public ConfigurableFileCollection addTaskOutput(
            @NonNull TaskOutputType outputType, @NonNull Object file, @Nullable String taskName) {
        return mVariantScope.addTaskOutput(outputType, file, taskName);
    }

    @NonNull
    @Override
    public ConfigurableFileCollection createAnchorOutput(@NonNull AnchorOutputType outputType) {
        return mVariantScope.createAnchorOutput(outputType);
    }

    @Override
    public void addToAnchorOutput(
            @NonNull AnchorOutputType outputType, @NonNull File file, @NonNull String taskName) {
        mVariantScope.addToAnchorOutput(outputType, file, taskName);
    }

    @Override
    public void addToAnchorOutput(
            @NonNull AnchorOutputType outputType, @NonNull FileCollection fileCollection) {
        mVariantScope.addToAnchorOutput(outputType, fileCollection);
    }

    @Override
    public void addTask(TaskContainer.TaskKind taskKind, Task task) {
        mVariantScope.getVariantData().addTask(taskKind, task);
    }

    @NonNull
    @Override
    public File getInstantRunResourceApkFolder() {
        return mVariantScope.getInstantRunResourceApkFolder();
    }

    @Override
    public boolean isAbiSplitsEnabled() {
        return mGlobalScope.getExtension().getSplits().getAbi().isEnable();
    }

    @Override
    public boolean isDensitySplitsEnabled() {
        return mGlobalScope.getExtension().getSplits().getDensity().isEnable();
    }
}
