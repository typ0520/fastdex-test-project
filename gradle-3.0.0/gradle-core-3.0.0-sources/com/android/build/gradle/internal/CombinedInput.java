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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;

/**
 * An input that combines other inputs, so that every time an individual input is changed, the
 * combined input is also changed.
 *
 * <p>This is a workaround for a Gradle bug: If an {@code @Optional @OutputFile/@OutputDirectory}
 * property is set to null in a build, subsequent builds will always be UP-TO-DATE even if the
 * output file/directory is set to a different value. See https://issuetracker.google.com/67418335
 * for more details.
 *
 * <p>The workaround is to create a new property combining those properties and make this new
 * property an {@code @Input}. This way, the build will not be UP-TO-DATE if any of the output
 * files/directories changes.
 *
 * <p>TODO: We will need to remove this workaround once Gradle has a fix.
 */
public final class CombinedInput {

    @SuppressWarnings("StringBufferField")
    @NonNull
    private final StringBuilder input = new StringBuilder();

    /** Creates a new {@code CombinedInput} instance. */
    public CombinedInput() {}

    /** Creates a new {@code CombinedInput} instance with some existing input. */
    public CombinedInput(@NonNull String existingInput) {
        input.append(existingInput);
    }

    /** Adds a property. */
    @NonNull
    public CombinedInput add(@NonNull String propertyName, @Nullable File propertyValue) {
        if (input.length() > 0) {
            input.append("\n");
        }

        input.append(
                String.format(
                        "%1$s=%2$s",
                        propertyName, propertyValue != null ? propertyValue.getPath() : "null"));

        return this;
    }

    @Override
    public String toString() {
        return input.toString();
    }
}
