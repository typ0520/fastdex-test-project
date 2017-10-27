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

import static com.google.common.base.Verify.verify;

import com.android.annotations.NonNull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The obvious implementation of {@link Tracer}. */
public class RealTracer<T> implements Tracer<T> {

    @NonNull private final Set<T> nodesToExplain;
    @NonNull private final Map<T, Trace<T>> traces;

    public RealTracer(@NonNull Set<T> nodesToExplain) {
        this.nodesToExplain = nodesToExplain;
        this.traces = new ConcurrentHashMap<>();
    }

    @NonNull
    @Override
    public Trace<T> startTrace() {
        return new Trace<>(null, null, null);
    }

    @Override
    public void nodeReached(@NonNull T node, @NonNull Trace<T> trace) {
        verify(node.equals(trace.node), "Trace does not end with the node.");

        if (nodesToExplain.contains(node)) {
            verify(!traces.containsKey(node), "Node %s already recorded.", node);
            traces.put(node, trace);
        }
    }

    @NonNull
    @Override
    public Map<T, Trace<T>> getRecordedTraces() {
        return traces;
    }
}
