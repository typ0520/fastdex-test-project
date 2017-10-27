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
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.AaptOptions;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;

/**
 * Determines the values of enum typed project options, as set on the command line or environment.
 */
@Immutable
public final class EnumOptions {

    @NonNull private final AaptOptions.Namespacing namespacing;

    EnumOptions(@NonNull AaptOptions.Namespacing namespacing) {
        this.namespacing = namespacing;
    }

    @NonNull
    public AaptOptions.Namespacing getNamespacing() {
        return namespacing;
    }

    @Nullable
    private static <T extends Enum<T>> T get(
            @NonNull ImmutableMap<EnumOption, String> options,
            @NonNull EnumOption option,
            @NonNull Class<T> enumClass) {
        String optionValue = options.get(option);
        if (optionValue == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, optionValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Project property "
                            + option.getPropertyName()
                            + " is set to invalid value '"
                            + optionValue
                            + "'. Possible values are: "
                            + Joiner.on(", ").join(enumClass.getEnumConstants())
                            + ".");
        }
    }

    static EnumOptions load(@NonNull ImmutableMap<EnumOption, String> options) {
        AaptOptions.Namespacing namespacing =
                MoreObjects.firstNonNull(
                        get(
                                options,
                                EnumOption.AAPT_OPTIONS_NAMESPACING,
                                AaptOptions.Namespacing.class),
                        AaptOptions.Namespacing.DISABLED);

        return new EnumOptions(namespacing);
    }

    /**
     * Represents the strings that will be parsed in the enum options class.
     *
     * <p>An internal implementation detail of the outer {@link EnumOptions} class, where there will
     * be one field and getter method per enum constant.
     */
    enum EnumOption implements Option<String> {
        AAPT_OPTIONS_NAMESPACING("android.aaptNamespacing"),
        ;

        @NonNull private final String propertyName;

        EnumOption(@NonNull String propertyName) {
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
                            + "' as string to be interpreted as an enum constant.");
        }
    }
}
