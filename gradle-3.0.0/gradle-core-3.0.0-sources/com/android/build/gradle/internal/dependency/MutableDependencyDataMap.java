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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.builder.dependency.DependencyMutableData;
import com.android.builder.dependency.level2.Dependency;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for all android dependencies mutable data
 */
public interface MutableDependencyDataMap {

    static MutableDependencyDataMap newInstance() {
        return new MutableDependencyDataMap() {
            Map<Dependency, DependencyMutableData> dataMap = new HashMap<>();

            @NonNull
            private synchronized DependencyMutableData getFor(Dependency library) {
                return dataMap.computeIfAbsent(library, k -> new DependencyMutableData());
            }

            @Override public boolean isSkipped(Dependency dependency) {
                DependencyMutableData dependencyMutableData = dataMap.get(dependency);
                return dependencyMutableData != null && dependencyMutableData.isSkipped();
            }

            @Override
            public void skip(Dependency dependency) {
                getFor(dependency).skip();
            }

            @Override
            public void setProvided(Dependency dependency) {
                getFor(dependency).setProvided(true);
            }

            @Override
            public boolean isProvided(Dependency dependency) {
                DependencyMutableData dependencyMutableData = dataMap.get(dependency);
                return dependencyMutableData != null && dependencyMutableData.isProvided();
            }

            @NonNull
            @Override
            public List<String> getProvidedList() {
                List<String> providedList = Lists.newArrayListWithExpectedSize(dataMap.size());
                for (Map.Entry<Dependency, DependencyMutableData> entry : dataMap.entrySet()) {
                    if (entry.getValue().isProvided()) {
                        providedList.add(entry.getKey().getAddress().toString());
                    }
                }
                return providedList;
            }

            @NonNull
            @Override
            public List<String> getSkippedList() {
                List<String> skippedList = Lists.newArrayListWithExpectedSize(dataMap.size());
                for (Map.Entry<Dependency, DependencyMutableData> entry : dataMap.entrySet()) {
                    if (entry.getValue().isSkipped()) {
                        skippedList.add(entry.getKey().getAddress().toString());
                    }
                }
                return skippedList;
            }

        };
    }

    void skip(Dependency library);
    boolean isSkipped(Dependency library);

    void setProvided(Dependency dependency);
    boolean isProvided(Dependency dependency);

    @NonNull
    List<String> getProvidedList();
    @NonNull
    List<String> getSkippedList();

    MutableDependencyDataMap EMPTY = new MutableDependencyDataMap() {
        @Override
        public void skip(Dependency dependency) {
            throw new RuntimeException(String.format("cannot set skipped attribute "
                    + "on a dependency with another scope than Package : %1$s", dependency));
        }

        @Override
        public boolean isSkipped(Dependency library) {
            return false;
        };

        @Override
        public void setProvided(Dependency dependency) {
            throw new RuntimeException(String.format("cannot set skipped attribute "
                    + "on a dependency with another scope than Compile : %1$s", dependency));
        }

        @Override
        public boolean isProvided(Dependency dependency) {
            return false;
        }

        @NonNull
        @Override
        public List<String> getProvidedList() {
            return ImmutableList.of();
        }

        @NonNull
        @Override
        public List<String> getSkippedList() {
            return ImmutableList.of();
        }
    };
}
