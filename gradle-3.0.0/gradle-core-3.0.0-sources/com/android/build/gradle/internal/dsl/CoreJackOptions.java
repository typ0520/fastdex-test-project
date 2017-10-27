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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * The Jack toolchain is <em>deprecated</em>.
 *
 * <p>If you want to use Java 8 language features, use the improved support included in the default
 * toolchain. To learn more, read <a
 * href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
 * features</a>.
 *
 * @deprecated For more information, read <a
 *     href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
 *     features</a>.
 */
@Deprecated
public interface CoreJackOptions {

    /**
     * Whether to use Jack for compilation. By default, this value is {@code false}.
     *
     * <p>The Jack toolchain is <em>deprecated</em>.
     *
     * <p>If you want to use Java 8 language features, use the improved support included in the
     * default toolchain. To learn more, read <a
     * href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     * features</a>.
     *
     * @deprecated For more information, read <a
     *     href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     *     features</a>.
     */
    @Deprecated
    @Nullable
    Boolean isEnabled();

    /**
     * Whether to run Jack the same JVM as Gradle. By default, this value is {@code true}.
     *
     * <p>The Jack toolchain is <em>deprecated</em>.
     *
     * <p>If you want to use Java 8 language features, use the improved support included in the
     * default toolchain. To learn more, read <a
     * href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     * features</a>.
     *
     * @deprecated For more information, read <a
     *     href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     *     features</a>.
     */
    @Deprecated
    @Nullable
    Boolean isJackInProcess();

    /**
     * Additional parameters to be passed to Jack.
     *
     * <p>The Jack toolchain is <em>deprecated</em>.
     *
     * <p>If you want to use Java 8 language features, use the improved support included in the
     * default toolchain. To learn more, read <a
     * href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     * features</a>.
     *
     * @deprecated For more information, read <a
     *     href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     *     features</a>.
     */
    @Deprecated
    @NonNull
    Map<String, String> getAdditionalParameters();

    /**
     * Jack plugins that will be added to the Jack pipeline.
     *
     * <p>The Jack toolchain is <em>deprecated</em>.
     *
     * <p>If you want to use Java 8 language features, use the improved support included in the
     * default toolchain. To learn more, read <a
     * href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     * features</a>.
     *
     * @deprecated For more information, read <a
     *     href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
     *     features</a>.
     */
    @Deprecated
    @NonNull
    List<String> getPluginNames();
}
