/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.builder.model.ProductFlavor;
import org.gradle.api.Named;

/**
 * A product flavor with addition properties for building with Gradle plugin.
 */
public interface CoreProductFlavor extends ProductFlavor, Named {

    @Nullable
    CoreNdkOptions getNdkConfig();

    @Nullable
    CoreExternalNativeBuildOptions getExternalNativeBuildOptions();
    /**
     * The Jack toolchain is deprecated.
     *
     * <p>If you want to use Java 8 language features, use the improved support included in the
     * default toolchain. To learn more, read <a
     * href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     * features</a>.
     */
    @Deprecated
    @NonNull
    CoreJackOptions getJackOptions();

    @NonNull
    JavaCompileOptions getJavaCompileOptions();

    @NonNull
    CoreShaderOptions getShaders();
}
