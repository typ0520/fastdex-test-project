/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.SyncIssue;
import java.util.Arrays;

/**
 * DSL object for configuring dx options.
 */
@SuppressWarnings("unused") // Exposed in the DSL.
public class DexOptions extends DefaultDexOptions {

    private static final String INCREMENTAL_IGNORED =
            "The `android.dexOptions.incremental` property"
                    + " is deprecated and it has no effect on the build process.";

    private static final String OPTIMIZE_IGNORED =
            "The `android.dexOptions.optimize` property is deprecated. Dex will"
                    + " always be optimized.";

    private final ErrorReporter mErrorReporter;

    public DexOptions(ErrorReporter errorReporter) {
        this.mErrorReporter = errorReporter;
    }

    /** @deprecated ignored */
    @Deprecated
    public boolean getIncremental() {
        mErrorReporter.handleSyncWarning(
                null,
                SyncIssue.TYPE_GENERIC,
                INCREMENTAL_IGNORED);
        return false;
    }

    public void setIncremental(boolean ignored) {
        mErrorReporter.handleSyncWarning(
                null,
                SyncIssue.TYPE_GENERIC,
                INCREMENTAL_IGNORED);
    }

    public void additionalParameters(String... parameters) {
        this.setAdditionalParameters(Arrays.asList(parameters));
    }

    /**
     * @deprecated Dex will always be optimized. Invoking this method has no effect.
     */
    @Deprecated
    public void setOptimize(@SuppressWarnings("UnusedParameters") Boolean optimize) {
        mErrorReporter.handleSyncWarning(
                null,
                SyncIssue.TYPE_GENERIC,
                OPTIMIZE_IGNORED + "\n" + OPTIMIZE_WARNING);
    }
}
