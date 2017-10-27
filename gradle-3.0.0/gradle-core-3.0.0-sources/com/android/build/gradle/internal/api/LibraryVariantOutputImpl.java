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
import com.android.build.gradle.api.LibraryVariantOutput;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.tasks.AndroidZip;
import com.android.ide.common.build.ApkData;
import java.io.File;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Implementation of variant output for library variants.
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public class LibraryVariantOutputImpl extends BaseVariantOutputImpl implements LibraryVariantOutput {

    public LibraryVariantOutputImpl(
            @NonNull ApkData apkData, @NonNull TaskContainer taskContainer) {
        super(apkData, taskContainer);
    }

    @Override
    @NonNull
    protected ApkData getApkData() {
        return apkData;
    }

    @Nullable
    @Override
    public Zip getPackageLibrary() {
        return taskContainer.getTaskByType(AndroidZip.class);
    }

    @NonNull
    @Override
    public File getOutputFile() {
        Zip packageTask = getPackageLibrary();
        if (packageTask != null) {
            return new File(packageTask.getDestinationDir(), apkData.getOutputFileName());
        } else {
            return super.getOutputFile();
        }
    }

    @Override
    public int getVersionCode() {
        throw new RuntimeException("Libraries are not versioned");
    }
}
