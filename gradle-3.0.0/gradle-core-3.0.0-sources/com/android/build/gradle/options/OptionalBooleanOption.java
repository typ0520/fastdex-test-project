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

public enum OptionalBooleanOption implements Option<Boolean> {
    SIGNING_V1_ENABLED(AndroidProject.PROPERTY_SIGNING_V1_ENABLED),
    SIGNING_V2_ENABLED(AndroidProject.PROPERTY_SIGNING_V2_ENABLED),
    IDE_TEST_ONLY(AndroidProject.PROPERTY_TEST_ONLY),
    SERIAL_AAPT2(AndroidProject.PROPERTY_INVOKE_JNI_AAPT2_LINK_SERIALLY)
    ;

    @NonNull private final String propertyName;

    OptionalBooleanOption(@NonNull String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    @NonNull
    public String getPropertyName() {
        return propertyName;
    }

    @Nullable
    @Override
    public Boolean getDefaultValue() {
        return null;
    }

    @NonNull
    @Override
    public Boolean parse(@NonNull Object value) {
        if (value instanceof CharSequence) {
            return Boolean.parseBoolean(value.toString());
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
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
