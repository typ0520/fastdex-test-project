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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidVersion;


/**
 * Patching policy for delivering incremental code changes and triggering a cold start (application
 * restart).
 */
public enum InstantRunPatchingPolicy {

    /**
     * For Dalvik, a patch dex file will be generated with the incremental changes from the last
     * non incremental build or the last build that contained changes identified by the verifier as
     * incompatible.
     */
    PRE_LOLLIPOP(DexPackagingPolicy.STANDARD, false /* useMultidex */),

    /**
     * For L and above, each shard dex file described above will be packaged in a single pure split
     * APK that will be pushed and installed on the device using adb install-multiple commands.
     */
    MULTI_APK(DexPackagingPolicy.INSTANT_RUN_MULTI_APK, true /* useMultidex */),

    /**
     * For O and above, we ship the resources in a separate APK from the main APK.
     *
     * <p>In a near future, this can be merged with the {@link #MULTI_APK} case but - we need to
     * test this thoroughly back to 21. - we need aapt2 in the stable build tools to support all
     * cases.
     */
    MULTI_APK_SEPARATE_RESOURCES(DexPackagingPolicy.INSTANT_RUN_MULTI_APK, true /* useMultidex */);

    @NonNull
    private final DexPackagingPolicy dexPatchingPolicy;
    private final boolean useMultiDex;

    InstantRunPatchingPolicy(@NonNull DexPackagingPolicy dexPatchingPolicy, boolean useMultiDex) {
        this.dexPatchingPolicy = dexPatchingPolicy;
        this.useMultiDex = useMultiDex;
    }

    /**
     * Returns true of this packaging policy relies on multidex or not.
     */
    public boolean useMultiDex() {
        return useMultiDex;
    }

    public static boolean useMultiApk(@Nullable InstantRunPatchingPolicy subject) {
        return subject != null && subject.useMultiApk();
    }

    public boolean useMultiApk() {
        return this == MULTI_APK_SEPARATE_RESOURCES || this == MULTI_APK;
    }

    /**
     * Returns the dex packaging policy for this patching policy. There can be variations depending
     * on the target platforms.
     * @return the desired dex packaging policy for dex files
     */
    @NonNull
    public DexPackagingPolicy getDexPatchingPolicy() {
        return dexPatchingPolicy;
    }

    /**
     * Returns the patching policy following the {@link AndroidProject#PROPERTY_BUILD_API} value
     * passed by Android Studio.
     *
     * @param androidVersion the android version of the target device
     * @param useAapt2OrAbove use aapt2 or above to process resources.
     * @return a {@link InstantRunPatchingPolicy} instance.
     */
    @NonNull
    public static InstantRunPatchingPolicy getPatchingPolicy(
            AndroidVersion androidVersion,
            boolean useAapt2OrAbove,
            boolean createSeparateApkForResources) {

        if (androidVersion.getFeatureLevel() < AndroidVersion.ART_RUNTIME.getFeatureLevel()) {
            return PRE_LOLLIPOP;
        } else {
            return androidVersion.getFeatureLevel() >= AndroidVersion.VersionCodes.O
                            && useAapt2OrAbove
                            && createSeparateApkForResources
                    ? MULTI_APK_SEPARATE_RESOURCES
                    : MULTI_APK;
        }
    }

}
