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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;


/** Implementation of {@link com.android.builder.model.Dependencies} interface */
@Immutable
public class DependenciesImpl implements Dependencies, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull private final List<AndroidLibrary> libraries;
    @NonNull private final List<JavaLibrary> javaLibraries;
    @NonNull private final List<String> projects;

    DependenciesImpl(
            @NonNull List<AndroidLibrary> libraries,
            @NonNull List<JavaLibrary> javaLibraries,
            @NonNull List<String> projects) {
        this.libraries = libraries;
        this.javaLibraries = javaLibraries;
        this.projects = projects;
    }

    @NonNull
    @Override
    public Collection<AndroidLibrary> getLibraries() {
        return libraries;
    }

    @NonNull
    @Override
    public Collection<JavaLibrary> getJavaLibraries() {
        return javaLibraries;
    }

    @NonNull
    @Override
    public List<String> getProjects() {
        return projects;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("libraries", libraries)
                .add("javaLibraries", javaLibraries)
                .add("projects", projects)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DependenciesImpl that = (DependenciesImpl) o;
        return Objects.equals(libraries, that.libraries)
                && Objects.equals(javaLibraries, that.javaLibraries)
                && Objects.equals(projects, that.projects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(libraries, javaLibraries, projects);
    }
}
