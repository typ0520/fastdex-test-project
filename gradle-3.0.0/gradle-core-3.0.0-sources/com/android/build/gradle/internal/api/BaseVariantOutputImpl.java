/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.ide.common.build.ApkData;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import org.gradle.api.Task;

/**
 * Implementation of the base variant output. This is the base class for items common to apps,
 * test apps, and libraries
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public abstract class BaseVariantOutputImpl implements BaseVariantOutput {

    @NonNull protected final TaskContainer taskContainer;
    protected final ApkData apkData;

    protected BaseVariantOutputImpl(
            @NonNull ApkData apkData, @NonNull TaskContainer taskContainer) {
        this.apkData = apkData;
        this.taskContainer = taskContainer;
    }

    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        return getApkData().getMainOutputFile();
    }

    @NonNull
    protected ApkData getApkData() {
        return apkData;
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return getApkData().getMainOutputFile().getOutputFile();
    }

    @NonNull
    @Override
    public ImmutableList<OutputFile> getOutputs() {
        return ImmutableList.of(this);
    }

    @Nullable
    @Override
    public ProcessAndroidResources getProcessResources() {
        return taskContainer.getTaskByType(ProcessAndroidResources.class);
    }

    @Override
    @Nullable
    public ManifestProcessorTask getProcessManifest() {
        return taskContainer.getTaskByType(ManifestProcessorTask.class);
    }

    @Nullable
    @Override
    public Task getAssemble() {
        return taskContainer.getTaskByKind(TaskContainer.TaskKind.ASSEMBLE);
    }

    @NonNull
    @Override
    public String getName() {
        return getApkData().getBaseName();
    }

    @NonNull
    @Override
    public String getBaseName() {
        return getApkData().getBaseName();
    }

    @NonNull
    @Override
    public String getDirName() {
        return getApkData().getDirName();
    }

    @NonNull
    @Override
    public String getOutputType() {
        return getApkData().getOutputType();
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return getApkData().getFilterTypes();
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return getApkData().getFilters();
    }

    @Nullable
    public String getFilter(String filterType) {
        return getApkData().getFilter(filterType);
    }

    public String getOutputFileName() {
        return apkData.getOutputFileName();
    }

    public void setOutputFileName(String outputFileName) {
        apkData.setOutputFileName(outputFileName);
    }
}
