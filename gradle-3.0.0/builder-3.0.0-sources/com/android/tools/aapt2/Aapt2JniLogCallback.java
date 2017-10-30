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

package com.android.tools.aapt2;

import javax.annotation.Nonnull;

/**
 * Interface for AAPT log messages to be processed back through JNI.
 *
 * <p>Changes must only be made if updating the implementation in AAPT2 JNI at the same time.
 */
public interface Aapt2JniLogCallback {

    /**
     * Log a message from AAPT2.
     *
     * @param level the log level, see {@link #intToLogLevel(int)}.
     * @param path the path of the input file.
     * @param line the line in that input file, -1 if not defined.
     * @param message the actual message.
     */
    @SuppressWarnings("unused") // Called from JNI.
    void log(int level, @Nonnull String path, long line, @Nonnull String message);

    /**
     * Maps the integer passed by AAPT2 back to the enum log level.
     *
     * @param loglevel The int passed by AAPT2
     * @return the corresponding {@link Aapt2Result.Message.LogLevel} enum value.
     * @throws IllegalArgumentException if the int is not valid.
     */
    @Nonnull
    static Aapt2Result.Message.LogLevel intToLogLevel(int loglevel) {
        switch (loglevel) {
            case 1:
                return Aapt2Result.Message.LogLevel.NOTE;
            case 2:
                return Aapt2Result.Message.LogLevel.WARN;
            case 3:
                return Aapt2Result.Message.LogLevel.ERROR;
            default:
                throw new IllegalArgumentException();
        }
    }
}
