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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.repository.Revision;
import java.io.File;
import java.util.Collection;
import java.util.List;

/** Factory class to create Cmake strategy object based on Cmake version. */
class CmakeExternalNativeJsonGeneratorFactory {
    /**
     * Creates a Cmake strategy object for the given cmake revision. We currently only support Cmake
     * versions 3.6+.
     */
    public static ExternalNativeJsonGenerator createCmakeStrategy(
            @NonNull Revision cmakeRevision,
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
        // Custom Cmake shipped with Android studio has a fixed version, we'll just use that exact
        // version to check.
        if (cmakeRevision.equals(
                Revision.parseRevision(
                        ExternalNativeBuildTaskUtils.CUSTOM_FORK_CMAKE_VERSION,
                        Revision.Precision.MICRO))) {
            return new CmakeAndroidNinjaExternalNativeJsonGenerator(
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

        if (cmakeRevision.getMajor() < 3
                || (cmakeRevision.getMajor() == 3 && cmakeRevision.getMinor() <= 6)) {
            throw new RuntimeException(
                    "Unexpected/unsupported CMake version "
                            + cmakeRevision.toString()
                            + ". Try 3.7.0 or later.");
        }

        return new CmakeServerExternalNativeJsonGenerator(
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
}
