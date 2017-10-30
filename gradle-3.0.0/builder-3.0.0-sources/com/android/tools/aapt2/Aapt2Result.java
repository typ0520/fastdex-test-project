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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nonnull;

@AutoValue
public abstract class Aapt2Result {

    public abstract int getReturnCode();

    @Nonnull
    public abstract ImmutableList<Message> getMessages();

    public static Builder builder() {
        return new AutoValue_Aapt2Result.Builder();
    }

    @AutoValue
    public abstract static class Message {

        public enum LogLevel {
            NOTE,
            WARN,
            ERROR,
            ;
        }

        static Message create(
                @Nonnull LogLevel level, @Nonnull String path, long line, @Nonnull String message) {
            return new AutoValue_Aapt2Result_Message(level, path, line, message);
        }

        @Nonnull
        public abstract LogLevel getLevel();

        @Nonnull
        public abstract String getPath();

        public abstract long getLine();

        @Nonnull
        public abstract String getMessage();

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(getLevel().name()).append(": ").append(getPath());
            if (getLine() != -1) {
                builder.append(":").append(getLine());
            }
            builder.append(" ").append(getMessage());
            return builder.toString();
        }
    }

    @AutoValue.Builder
    public abstract static class Builder implements Aapt2JniLogCallback {

        @Override
        public void log(int levelValue, @Nonnull String path, long line, @Nonnull String message) {
            messagesBuilder()
                    .add(
                            Message.create(
                                    Aapt2JniLogCallback.intToLogLevel(levelValue),
                                    path,
                                    line,
                                    message));
        }

        abstract Builder setReturnCode(int returnCode);

        abstract ImmutableList.Builder<Message> messagesBuilder();

        abstract Aapt2Result build();
    }
}
