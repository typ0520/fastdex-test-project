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

package com.android.build.gradle.shrinker.parser;

/**
 * Represents the second four bytes of a class file, where the minor and major version is stored.
 */
public class BytecodeVersion {
    private final int bytes;

    public BytecodeVersion(int bytes) {
        this.bytes = bytes;
    }

    /**
     * The four bytes represented as Java int: the lower two bytes are the major version, the higher
     * ones are the minor version.
     *
     * <p>This most likely is equal to {@link org.objectweb.asm.Opcodes#V1_6} or similar.
     */
    public int getBytes() {
        return bytes;
    }
}
