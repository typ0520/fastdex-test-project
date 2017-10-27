/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.builder.model.AndroidArtifactOutput;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of AndroidArtifactOutput that is serializable
 */
@Immutable
final class AndroidArtifactOutputImpl implements AndroidArtifactOutput, Serializable {
    private static final long serialVersionUID = 1L;

    private final BuildOutput buildOutput;
    // even if we have pure splits, only one manifest file really matters.
    private final BuildOutput manifestOutput;
    private final Collection<BuildOutput> splitApksOutputs;

    public AndroidArtifactOutputImpl(BuildOutput buildOutput, BuildOutput manifestOutput) {
        this(buildOutput, manifestOutput, ImmutableList.of());
    }

    public AndroidArtifactOutputImpl(
            BuildOutput mainApk, BuildOutput manifestOutput, List<BuildOutput> splitApksOutputs) {
        this.buildOutput = mainApk;
        this.manifestOutput = manifestOutput;
        this.splitApksOutputs = splitApksOutputs;
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return getMainOutputFile().getOutputFile();
    }

    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        return buildOutput;
    }

    @NonNull
    @Override
    public Collection<OutputFile> getOutputs() {
        ImmutableList.Builder<OutputFile> outputFileBuilder = ImmutableList.builder();
        outputFileBuilder.add(buildOutput);
        splitApksOutputs.forEach(outputFileBuilder::add);
        return outputFileBuilder.build();
    }

    @NonNull
    @Override
    public String getAssembleTaskName() {
        throw new RuntimeException("Deprecated.");
    }

    @NonNull
    @Override
    public String getOutputType() {
        return buildOutput.getOutputType();
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return buildOutput.getFilterTypes();
    }

    @Nullable
    public String getFilter(String filterType) {
        return buildOutput.getFilter(filterType);
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return buildOutput.getFilters();
    }

    @NonNull
    @Override
    public File getGeneratedManifest() {
        return manifestOutput.getOutputFile();
    }

    @Override
    public int getVersionCode() {
        return buildOutput.getApkInfo().getVersionCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AndroidArtifactOutputImpl that = (AndroidArtifactOutputImpl) o;
        return Objects.equals(buildOutput, that.buildOutput)
                && Objects.equals(manifestOutput, that.manifestOutput)
                && Objects.equals(splitApksOutputs, that.splitApksOutputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(splitApksOutputs, manifestOutput, buildOutput);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("output", buildOutput)
                .add("manifest", manifestOutput)
                .add("pure splits", splitApksOutputs)
                .toString();
    }
}
