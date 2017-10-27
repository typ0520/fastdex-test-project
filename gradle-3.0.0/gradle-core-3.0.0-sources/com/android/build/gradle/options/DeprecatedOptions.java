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

/**
 * The list of options that have been removed.
 *
 * <p>If any of the deprecated options are set, a sync error will be raised.
 */
public enum DeprecatedOptions implements Option<String> {
    INCREMENTAL_JAVA_COMPILE(
            "android.incrementalJavaCompile",
            "The android.incrementalJavaCompile property has been replaced by a DSL property. "
                    + "Please add the following to your build.gradle instead:\n"
                    + "android {\n"
                    + "  compileOptions.incremental = false\n"
                    + "}"),
    THREAD_POOL_SIZE_OLD(
            "com.android.build.threadPoolSize",
            "The com.android.build.threadPoolSize property has been replaced by "
                    + IntegerOption.THREAD_POOL_SIZE.getPropertyName()),
    ENABLE_IMPROVED_DEPENDENCY_RESOLUTION(
            "android.enableImprovedDependenciesResolution",
            "The android.enableImprovedDependenciesResolution property does not have any effect. "
                    + "Dependency resolution is only performed during task execution phase."),
    ;

    @NonNull private final String propertyName;
    @NonNull private final String errorMessage;

    DeprecatedOptions(@NonNull String propertyName, @NonNull String errorMessage) {
        this.propertyName = propertyName;
        this.errorMessage = errorMessage;
    }

    @NonNull
    @Override
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
        return errorMessage;
    }
}
