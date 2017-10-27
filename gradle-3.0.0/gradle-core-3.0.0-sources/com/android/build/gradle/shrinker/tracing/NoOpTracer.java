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

package com.android.build.gradle.shrinker.tracing;

import com.android.annotations.NonNull;
import java.util.Collections;
import java.util.Map;

/** {@link Tracer} that does nothing. Used when {@code -whyareyoukeeping} was not set. */
public class NoOpTracer<T> implements Tracer<T> {

    @NonNull
    @Override
    public Trace<T> startTrace() {
        return new NoOpTrace<>();
    }

    @Override
    public void nodeReached(@NonNull T node, @NonNull Trace<T> trace) {
        // Do nothing.
    }

    @NonNull
    @Override
    public Map<T, Trace<T>> getRecordedTraces() {
        return Collections.emptyMap();
    }
}
