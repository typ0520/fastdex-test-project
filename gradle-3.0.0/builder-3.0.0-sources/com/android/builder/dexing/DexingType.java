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

package com.android.builder.dexing;

/**
 * The type of dex we produce. It can be:
 *
 * <ul>
 *   <li>mono dex: no multidex enabled, only one final DEX file produced
 *   <li>legacy multidex: multidex enabled, and min sdk version is less than 21
 *   <li>native multidex: multidex enabled, and min sdk version is greater or equal to 21
 * </ul>
 */
public enum DexingType {
    MONO_DEX(false, true),
    LEGACY_MULTIDEX(true, false),
    NATIVE_MULTIDEX(true, true);

    /** If this mode allows multiple DEX files. */
    private final boolean isMultiDex;

    /** If we should pre-dex in this dexing mode. */
    private final boolean preDex;

    DexingType(boolean isMultiDex, boolean preDex) {
        this.isMultiDex = isMultiDex;
        this.preDex = preDex;
    }

    public boolean isMultiDex() {
        return isMultiDex;
    }

    public boolean isPreDex() {
        return preDex;
    }
}
