/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils.executeBuildProcessAndLogError;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * This strategy uses the older custom CMake (version 3.6) that directly generates the JSON file as
 * part of project configuration.
 */
class CmakeAndroidNinjaExternalNativeJsonGenerator extends CmakeExternalNativeJsonGenerator {
    // Constructor
    CmakeAndroidNinjaExternalNativeJsonGenerator(
            @NonNull NdkHandler ndkHandler,
            int minSdkVersion,
            @NonNull String variantName,
            @NonNull Collection<Abi> abis,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File sdkFolder,
            @NonNull File ndkFolder,
            @NonNull File soFolder,
            @NonNull File objFolder,
            @NonNull File jsonFolder,
            @NonNull File makeFile,
            @NonNull File cmakeInstallFolder,
            boolean debuggable,
            @Nullable List<String> buildArguments,
            @Nullable List<String> cFlags,
            @Nullable List<String> cppFlags,
            @NonNull List<File> nativeBuildConfigurationsJsons) {
        super(
                ndkHandler,
                minSdkVersion,
                variantName,
                abis,
                androidBuilder,
                sdkFolder,
                ndkFolder,
                soFolder,
                objFolder,
                jsonFolder,
                makeFile,
                cmakeInstallFolder,
                debuggable,
                buildArguments,
                cFlags,
                cppFlags,
                nativeBuildConfigurationsJsons);
    }

    @NonNull
    @Override
    List<String> getCacheArguments(@NonNull String abi, int abiPlatformVersion) {
        List<String> cacheArguments = getCommonCacheArguments(abi, abiPlatformVersion);

        cacheArguments.add(
                String.format("-DCMAKE_TOOLCHAIN_FILE=%s", getToolChainFile().getAbsolutePath()));
        cacheArguments.add(
                String.format("-DCMAKE_MAKE_PROGRAM=%s", getNinjaExecutable().getAbsolutePath()));
        cacheArguments.add("-GAndroid Gradle - Ninja");
        return cacheArguments;
    }

    @NonNull
    @Override
    public String executeProcess(
            @NonNull String abi, int abiPlatformVersion, @NonNull File outputJsonDir)
            throws ProcessException, IOException {
        return executeBuildProcessAndLogError(
                androidBuilder, getProcessBuilder(abi, abiPlatformVersion, outputJsonDir), true);
    }


    @NonNull
    private File getNinjaExecutable() {
        if (isWindows()) {
            return new File(getCmakeBinFolder(), "ninja.exe");
        }
        return new File(getCmakeBinFolder(), "ninja");
    }
};
