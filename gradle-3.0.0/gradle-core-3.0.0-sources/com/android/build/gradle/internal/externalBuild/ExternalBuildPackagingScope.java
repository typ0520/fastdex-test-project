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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.sdklib.AndroidVersion;
import com.android.utils.StringHelper;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * A {@link PackagingScope} used with external build plugin.
 */
public class ExternalBuildPackagingScope implements PackagingScope {

    private final Project mProject;
    private final ExternalBuildContext mExternalBuildContext;
    private final ExternalBuildApkManifest.ApkManifest mBuildManifest;
    @NonNull
    private final ExternalBuildVariantScope mVariantScope;
    @NonNull
    private final TransformManager mTransformManager;
    @NonNull private InstantRunBuildContext mInstantRunBuildContext;
    @Nullable
    private final SigningConfig mSigningConfig;
    private final ProjectOptions mProjectOptions;

    public ExternalBuildPackagingScope(
            @NonNull Project project,
            @NonNull ExternalBuildContext externalBuildContext,
            @NonNull ExternalBuildVariantScope variantScope,
            @NonNull TransformManager transformManager,
            @Nullable SigningConfig signingConfig) {
        mProject = project;
        mExternalBuildContext = externalBuildContext;
        mBuildManifest = externalBuildContext.getBuildManifest();
        mVariantScope = variantScope;
        mTransformManager = transformManager;
        mSigningConfig = signingConfig;
        mInstantRunBuildContext = mVariantScope.getInstantRunBuildContext();
        mProjectOptions = variantScope.getGlobalScope().getProjectOptions();
    }

    @NonNull
    @Override
    public AndroidBuilder getAndroidBuilder() {
        return mExternalBuildContext.getAndroidBuilder();
    }

    @Override
    public String getProjectBaseName() {
        // FIX ME !
        return mExternalBuildContext.getExecutionRoot().getName();
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return mVariantScope.getFullVariantName();
    }

    @NonNull
    @Override
    public AndroidVersion getMinSdkVersion() {
        return new AndroidVersion(
                mInstantRunBuildContext.getAndroidVersion().getApiLevel(),
                mInstantRunBuildContext.getAndroidVersion().getCodename());
    }

    @NonNull
    @Override
    public InstantRunBuildContext getInstantRunBuildContext() {
        return mInstantRunBuildContext;
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
        return mTransformManager.getPipelineOutputAsFileCollection(StreamFilter.DEX);
    }

    @NonNull
    @Override
    public FileCollection getJavaResources() {
        // TODO: do we want to support java resources?
        return getProject().files();
    }

    @NonNull
    @Override
    public FileCollection getJniFolders() {
        return getProject().files();
    }

    @NonNull
    @Override
    public MultiOutputPolicy getMultiOutputPolicy() {
        return MultiOutputPolicy.SPLITS;
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return Collections.emptySet();
    }

    @NonNull
    private ApkOutputFile getMainOutputFile() {
        return mVariantScope.getMainOutputFile();
    }

    @Nullable
    @Override
    public Set<String> getSupportedAbis() {
        return null;
    }

    @Override
    public boolean isDebuggable() {
        return true;
    }

    @Override
    public boolean isJniDebuggable() {
        return false;
    }

    @Nullable
    @Override
    public CoreSigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    @Override
    public ProjectOptions getProjectOptions() {
        return mProjectOptions;
    }

    @NonNull
    @Override
    public PackagingOptions getPackagingOptions() {
        return new PackagingOptions();
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String name) {
        return name + StringHelper.capitalize(getFullVariantName());
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return prefix + getFullVariantName() + suffix;
    }

    @NonNull
    @Override
    public Project getProject() {
        return mProject;
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return mVariantScope.getInstantRunSplitApkOutputFolder();
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return mVariantScope.getApplicationId();
    }

    @Override
    public int getVersionCode() {
        return mVariantScope.getVersionCode();
    }

    @Nullable
    @Override
    public String getVersionName() {
        return mVariantScope.getVersionName();
    }

    @NonNull
    @Override
    public AaptOptions getAaptOptions() {
        return mVariantScope.getAaptOptions();
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
        // not needed as customization not allowed in external build system.
    }

    @NonNull
    @Override
    public File getInstantRunResourceApkFolder() {
        return mVariantScope.getInstantRunResourceApkFolder();
    }

    @Override
    public boolean isAbiSplitsEnabled() {
        return false;
    }

    @Override
    public boolean isDensitySplitsEnabled() {
        return false;
    }
}
