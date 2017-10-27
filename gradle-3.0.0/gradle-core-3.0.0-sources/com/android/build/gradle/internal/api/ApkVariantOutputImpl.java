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
import com.android.build.VariantOutput;
import com.android.build.gradle.api.ApkVariantOutput;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.ide.common.build.ApkData;
import com.google.common.base.MoreObjects;
import java.io.File;
import org.gradle.api.Task;

/**
 * Implementation of variant output for apk-generating variants.
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public class ApkVariantOutputImpl extends BaseVariantOutputImpl implements ApkVariantOutput {

    public ApkVariantOutputImpl(@NonNull ApkData apkData, @NonNull TaskContainer taskContainer) {
        super(apkData, taskContainer);
    }

    @Nullable
    @Override
    public PackageAndroidArtifact getPackageApplication() {
        return taskContainer.getTaskByType(PackageAndroidArtifact.class);
    }

    @NonNull
    @Override
    public File getOutputFile() {
        PackageAndroidArtifact packageAndroidArtifact = getPackageApplication();
        if (packageAndroidArtifact != null) {
            return new File(
                    packageAndroidArtifact.getOutputDirectory(), apkData.getOutputFileName());
        } else {
            return super.getOutputFile();
        }
    }

    @Nullable
    @Override
    public Task getZipAlign() {
        return getPackageApplication();
    }

    @Override
    public void setVersionCodeOverride(int versionCodeOverride) {
        apkData.setVersionCode(versionCodeOverride);
    }

    @Override
    public int getVersionCodeOverride() {
        return apkData.getVersionCode();
    }

    @Override
    public void setVersionNameOverride(String versionNameOverride) {
        apkData.setVersionName(versionNameOverride);
    }

    @Override
    public String getVersionNameOverride() {
        return apkData.getVersionName();
    }

    @Override
    public int getVersionCode() {
        return apkData.getVersionCode();
    }

    @Override
    public String getFilter(VariantOutput.FilterType filterType) {
        return apkData.getFilter(filterType);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("apkData", apkData).toString();
    }
}
