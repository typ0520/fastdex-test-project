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
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.impldep.org.jetbrains.annotations.NotNull;

/** Data needed by the packaging tasks. */
public interface PackagingScope extends TaskOutputHolder {

    /**
     * The {@link AndroidBuilder} to use.
     */
    @NonNull
    AndroidBuilder getAndroidBuilder();

    /**
     * Full name of the variant.
     */
    @NonNull
    String getFullVariantName();

    /** Min SDK version of the artifact to create. */
    @NonNull
    AndroidVersion getMinSdkVersion();

    @NonNull
    InstantRunBuildContext getInstantRunBuildContext();

    /**
     * Directory with instant run support files.
     */
    @NonNull
    File getInstantRunSupportDir();

    /**
     * Returns the directory for storing incremental files.
     */
    @NonNull
    File getIncrementalDir(@NonNull String name);

    @NonNull
    FileCollection getDexFolders();

    @NonNull
    FileCollection getJavaResources();

    @NonNull
    FileCollection getJniFolders();

    @NonNull
    MultiOutputPolicy getMultiOutputPolicy();

    @NonNull
    Set<String> getAbiFilters();

    @Nullable
    Set<String> getSupportedAbis();

    boolean isDebuggable();

    boolean isJniDebuggable();

    @Nullable
    CoreSigningConfig getSigningConfig();

    @NonNull
    PackagingOptions getPackagingOptions();

    @NonNull
    String getTaskName(@NonNull String name);

    @NonNull
    String getTaskName(@NonNull String prefix, @NonNull String suffix);

    @NonNull
    Project getProject();

    /** Returns the project base name */
    String getProjectBaseName();

    @NonNull
    File getInstantRunSplitApkOutputFolder();

    @NonNull
    String getApplicationId();

    int getVersionCode();

    @Nullable
    String getVersionName();

    @NonNull
    AaptOptions getAaptOptions();

    @NotNull
    ProjectOptions getProjectOptions();

    OutputScope getOutputScope();

    void addTask(TaskContainer.TaskKind taskKind, Task task);

    @NonNull
    File getInstantRunResourceApkFolder();

    boolean isAbiSplitsEnabled();

    boolean isDensitySplitsEnabled();
}
