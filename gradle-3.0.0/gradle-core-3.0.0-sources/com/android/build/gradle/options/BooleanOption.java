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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;

public enum BooleanOption implements Option<Boolean> {
    ENABLE_AAPT2("android.enableAapt2", true),
    ENABLE_IN_PROCESS_AAPT2("android.enableAapt2jni", false),
    ENABLE_DAEMON_MODE_AAPT2("android.enableAapt2DaemonMode", true),
    ENABLE_BUILD_CACHE("android.enableBuildCache", true),
    ENABLE_PROFILE_JSON("android.enableProfileJson", true),
    ENABLE_SDK_DOWNLOAD("android.builder.sdkDownload", true),
    ENABLE_TEST_SHARDING("android.androidTest.shardBetweenDevices"),
    ENABLE_DEX_ARCHIVE("android.useDexArchive", true),
    ENABLE_NEW_RESOURCE_PROCESSING("android.enableNewResourceProcessing", true),
    ENABLE_IMPROVED_DEPENDENCY_RESOLUTION("android.enableImprovedDependenciesResolution", true),
    ENABLE_INTERMEDIATE_ARTIFACTS_CACHE("android.enableIntermediateArtifactsCache", true),
    ENABLE_EXTRACT_ANNOTATIONS("android.enableExtractAnnotations", true),
    VERSION_CHECK_OVERRIDE_PROPERTY("android.overrideVersionCheck"),
    VERSION_CHECK_OVERRIDE_PROPERTY_OLD("com.android.build.gradle.overrideVersionCheck"),
    OVERRIDE_PATH_CHECK_PROPERTY("android.overridePathCheck"),
    OVERRIDE_PATH_CHECK_PROPERTY_OLD("com.android.build.gradle.overridePathCheck"),
    ENABLE_DESUGAR("android.enableDesugar", true),
    ENABLE_GRADLE_WORKERS("android.enableGradleWorkers", false),
    DISABLE_RES_MERGE_IN_LIBRARY("android.disable.res.merge", true),
    ENABLE_D8("android.enableD8", false),

    ENABLE_DEPRECATED_NDK("android.useDeprecatedNdk"),
    DISABLE_RESOURCE_VALIDATION("android.disableResourceValidation"),

    /** Set to true to build native .so libraries only for the device it will be run on. */
    BUILD_ONLY_TARGET_ABI("android.buildOnlyTargetAbi", true),
    KEEP_TIMESTAMPS_IN_APK("android.keepTimestampsInApk"),

    IDE_INVOKED_FROM_IDE(AndroidProject.PROPERTY_INVOKED_FROM_IDE),
    IDE_BUILD_MODEL_ONLY(AndroidProject.PROPERTY_BUILD_MODEL_ONLY),
    IDE_BUILD_MODEL_ONLY_ADVANCED(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED),
    IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES(
            AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES),
    IDE_REFRESH_EXTERNAL_NATIVE_MODEL(AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL),
    IDE_GENERATE_SOURCES_ONLY(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY),
    ENABLE_SEPARATE_APK_RESOURCES("android.enableSeparateApkRes", false),
    ENABLE_BUILDSCRIPT_CLASSPATH_CHECK("android.enableBuildScriptClasspathCheck", true),
    ;

    @NonNull private final String propertyName;
    private final boolean defaultValue;

    BooleanOption(@NonNull String propertyName) {
        this(propertyName, false);
    }

    BooleanOption(@NonNull String propertyName, boolean defaultValue) {
        this.propertyName = propertyName;
        this.defaultValue = defaultValue;
    }

    @Override
    @NonNull
    public String getPropertyName() {
        return propertyName;
    }

    @NonNull
    @Override
    public Boolean getDefaultValue() {
        return defaultValue;
    }

    @NonNull
    @Override
    public Boolean parse(@NonNull Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof CharSequence) {
            return Boolean.parseBoolean(value.toString());
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        throw new IllegalArgumentException(
                "Cannot parse project property "
                        + this.getPropertyName()
                        + "='"
                        + value
                        + "' of type '"
                        + value.getClass()
                        + "' as boolean.");
    }
}
