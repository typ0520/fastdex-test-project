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
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;

public enum StringOption implements Option<String> {
    BUILD_CACHE_DIR("android.buildCacheDir"),

    IDE_BUILD_TARGET_DENSITY(AndroidProject.PROPERTY_BUILD_DENSITY),
    IDE_BUILD_TARGET_ABI(AndroidProject.PROPERTY_BUILD_ABI),

    IDE_RESTRICT_VARIANT_PROJECT(AndroidProject.PROPERTY_RESTRICT_VARIANT_PROJECT),
    IDE_RESTRICT_VARIANT_NAME(AndroidProject.PROPERTY_RESTRICT_VARIANT_NAME),

    // Signing options
    IDE_SIGNING_STORE_TYPE(AndroidProject.PROPERTY_SIGNING_STORE_TYPE),
    IDE_SIGNING_STORE_FILE(AndroidProject.PROPERTY_SIGNING_STORE_FILE),
    IDE_SIGNING_STORE_PASSWORD(AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD),
    IDE_SIGNING_KEY_ALIAS(AndroidProject.PROPERTY_SIGNING_KEY_ALIAS),
    IDE_SIGNING_KEY_PASSWORD(AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD),

    IDE_APK_LOCATION(AndroidProject.PROPERTY_APK_LOCATION),

    // Instant run
    IDE_OPTIONAL_COMPILATION_STEPS(AndroidProject.PROPERTY_OPTIONAL_COMPILATION_STEPS),
    IDE_COLD_SWAP_MODE(AndroidProject.PROPERTY_SIGNING_COLDSWAP_MODE),
    IDE_VERSION_NAME_OVERRIDE(AndroidProject.PROPERTY_VERSION_NAME),
    
    IDE_TARGET_DEVICE_CODENAME(AndroidProject.PROPERTY_BUILD_API_CODENAME),

    // Profiler plugin
    IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS("android.advanced.profiling.transforms"),

    // Testing
    DEVICE_POOL_SERIAL("com.android.test.devicepool.serial"),
    ;

    @NonNull private final String propertyName;

    StringOption(@NonNull String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    @NonNull
    public String getPropertyName() {
        return propertyName;
    }

    @Nullable
    @Override
    public String getDefaultValue() {
        return null;
    }

    @NonNull
    @Override
    public String parse(@NonNull Object value) {
        if (value instanceof CharSequence || value instanceof Number) {
            return value.toString();
        }
        throw new IllegalArgumentException(
                "Cannot parse project property "
                        + this.getPropertyName()
                        + "='"
                        + value
                        + "' of type '"
                        + value.getClass()
                        + "' as string.");
    }
}
