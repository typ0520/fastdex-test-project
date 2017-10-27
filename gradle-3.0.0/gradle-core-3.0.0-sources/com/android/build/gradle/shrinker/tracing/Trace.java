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
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.gradle.shrinker.DependencyType;
import com.android.utils.Pair;
import java.util.ArrayList;
import java.util.List;

/**
 * Singly-linked list of nodes visited so far.
 *
 * <p>The last element in the list is a sentinel, with the values being null.
 */
@Immutable
public class Trace<T> {
    @Nullable final T node;
    @Nullable final DependencyType dependencyType;
    @Nullable private final Trace<T> rest;

    Trace(@Nullable T node, @Nullable DependencyType dependencyType, @Nullable Trace<T> rest) {
        this.node = node;
        this.rest = rest;
        this.dependencyType = dependencyType;
    }

    public Trace<T> with(@NonNull T node, @NonNull DependencyType dependencyType) {
        return new Trace<>(node, dependencyType, this);
    }

    public List<Pair<T, DependencyType>> toList() {
        List<Pair<T, DependencyType>> result = new ArrayList<>();
        Trace<T> current = this;
        while (current != null) {
            if (current.node == null) {
                // Sentinel, this is the first entry in the trace, with just nulls.
                break;
            }
            result.add(Pair.of(current.node, current.dependencyType));
            current = current.rest;
        }
        return result;
    }
}
