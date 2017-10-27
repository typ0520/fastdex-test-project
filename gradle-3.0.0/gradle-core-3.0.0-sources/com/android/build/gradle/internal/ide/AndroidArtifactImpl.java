/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ClassField;
import com.android.builder.model.Dependencies;
import com.android.builder.model.InstantRun;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.TestOptions;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.build.ApkInfo;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of AndroidArtifact that is serializable
 */
@Immutable
final class AndroidArtifactImpl extends BaseArtifactImpl implements AndroidArtifact, Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean isSigned;
    @NonNull private final String baseName;
    @NonNull private final String applicationId;
    @NonNull private final String sourceGenTaskName;
    @NonNull private final List<File> generatedResourceFolders;
    @NonNull private final List<File> additionalRuntimeApks;
    @NonNull private final Collection<NativeLibrary> nativeLibraries;
    @NonNull private final Map<String, ClassField> buildConfigFields;
    @NonNull private final Map<String, ClassField> resValues;
    @NonNull private final InstantRun instantRun;
    @NonNull private final BuildOutputSupplier<Collection<BuildOutput>> splitOutputsSupplier;
    @NonNull private final BuildOutputSupplier<Collection<BuildOutput>> manifestSupplier;
    @Nullable private final String signingConfigName;
    @Nullable private final Set<String> abiFilters;
    @Nullable private final TestOptions testOptions;

    AndroidArtifactImpl(
            @NonNull String name,
            @NonNull String baseName,
            @NonNull String assembleTaskName,
            boolean isSigned,
            @Nullable String signingConfigName,
            @NonNull String applicationId,
            @NonNull String sourceGenTaskName,
            @NonNull String compileTaskName,
            @NonNull List<File> generatedSourceFolders,
            @NonNull List<File> generatedResourceFolders,
            @NonNull File classesFolder,
            @NonNull Set<File> additionalClassFolders,
            @NonNull File javaResourcesFolder,
            @NonNull Dependencies compileDependencies,
            @NonNull DependencyGraphs dependencyGraphs,
            @NonNull List<File> additionalRuntimeApks,
            @Nullable SourceProvider variantSourceProvider,
            @Nullable SourceProvider multiFlavorSourceProviders,
            @Nullable Set<String> abiFilters,
            @NonNull Collection<NativeLibrary> nativeLibraries,
            @NonNull Map<String, ClassField> buildConfigFields,
            @NonNull Map<String, ClassField> resValues,
            @NonNull InstantRun instantRun,
            @NonNull BuildOutputSupplier<Collection<BuildOutput>> splitOutputsSupplier,
            @NonNull BuildOutputSupplier<Collection<BuildOutput>> manifestSupplier,
            @Nullable TestOptions testOptions) {
        super(
                name,
                assembleTaskName,
                compileTaskName,
                classesFolder,
                additionalClassFolders,
                javaResourcesFolder,
                compileDependencies,
                dependencyGraphs,
                variantSourceProvider,
                multiFlavorSourceProviders,
                generatedSourceFolders);

        this.baseName = baseName;
        this.isSigned = isSigned;
        this.signingConfigName = signingConfigName;
        this.applicationId = applicationId;
        this.sourceGenTaskName = sourceGenTaskName;
        this.generatedResourceFolders = generatedResourceFolders;
        this.additionalRuntimeApks = additionalRuntimeApks;
        this.abiFilters = abiFilters;
        this.nativeLibraries = nativeLibraries;
        this.buildConfigFields = buildConfigFields;
        this.resValues = resValues;
        this.instantRun = instantRun;
        this.splitOutputsSupplier = splitOutputsSupplier;
        this.manifestSupplier = manifestSupplier;
        this.testOptions = testOptions;
    }

    @NonNull
    @Override
    public Collection<AndroidArtifactOutput> getOutputs() {
        Collection<BuildOutput> manifests = manifestSupplier.get();
        Collection<BuildOutput> outputs = splitOutputsSupplier.get();
        if (outputs.isEmpty()) {
            return manifests.isEmpty()
                    ? guessOutputsBasedOnNothing()
                    : guessOutputsBaseOnManifests();
        }

        List<BuildOutput> splitApksOutput =
                outputs.stream()
                        .filter(
                                splitOutput ->
                                        splitOutput.getApkInfo().getType()
                                                == OutputFile.OutputType.SPLIT)
                        .collect(Collectors.toList());
        if (splitApksOutput.isEmpty()) {
            // we don't have split APKs so each output is mapped to a different
            // AndroidArtifactOutput
            return outputs.stream()
                    .map(
                            splitOutput ->
                                    new AndroidArtifactOutputImpl(
                                            splitOutput,
                                            OutputScope.getOutput(
                                                    manifests,
                                                    TaskOutputHolder.TaskOutputType
                                                            .MERGED_MANIFESTS,
                                                    splitOutput.getApkInfo())))
                    .collect(Collectors.toList());
        } else {
            List<BuildOutput> mainApks =
                    outputs.stream()
                            .filter(
                                    splitOutput ->
                                            splitOutput.getApkInfo().getType()
                                                    == OutputFile.OutputType.MAIN)
                            .collect(Collectors.toList());
            if (mainApks.size() != 1) {
                throw new RuntimeException(
                        "Invalid main APK outputs : " + Joiner.on(",").join(mainApks));
            }
            return ImmutableList.of(
                    new AndroidArtifactOutputImpl(
                            mainApks.get(0),
                            OutputScope.getOutput(
                                    manifests,
                                    TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS,
                                    mainApks.get(0).getApkInfo()),
                            splitApksOutput));
        }
    }

    private Collection<AndroidArtifactOutput> guessOutputsBasedOnNothing() {
        ApkInfo mainApkInfo = ApkInfo.of(OutputFile.OutputType.MAIN, ImmutableList.of(), -1);

        return ImmutableList.of(
                new AndroidArtifactOutputImpl(
                        new BuildOutput(
                                TaskOutputHolder.TaskOutputType.APK,
                                mainApkInfo,
                                splitOutputsSupplier.guessOutputFile(
                                        baseName + SdkConstants.DOT_ANDROID_PACKAGE)),
                        new BuildOutput(
                                TaskOutputHolder.TaskOutputType.APK,
                                mainApkInfo,
                                manifestSupplier.guessOutputFile(
                                        SdkConstants.ANDROID_MANIFEST_XML))));
    }

    private Collection<AndroidArtifactOutput> guessOutputsBaseOnManifests() {

        return manifestSupplier
                .get()
                .stream()
                .map(
                        manifestOutput ->
                                new AndroidArtifactOutputImpl(
                                        new BuildOutput(
                                                TaskOutputHolder.TaskOutputType.APK,
                                                manifestOutput.getApkInfo(),
                                                splitOutputsSupplier.guessOutputFile(
                                                        baseName
                                                                + Joiner.on("-")
                                                                        .join(
                                                                                manifestOutput
                                                                                        .getApkInfo()
                                                                                        .getFilters()
                                                                                        .stream()
                                                                                        .map(
                                                                                                FilterData
                                                                                                        ::getIdentifier)
                                                                                        .collect(
                                                                                                Collectors
                                                                                                        .toList()))
                                                                + SdkConstants
                                                                        .DOT_ANDROID_PACKAGE)),
                                        manifestOutput))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isSigned() {
        return isSigned;
    }

    @Nullable
    @Override
    public String getSigningConfigName() {
        return signingConfigName;
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @NonNull
    @Override
    public String getSourceGenTaskName() {
        return sourceGenTaskName;
    }

    @NonNull
    @Override
    public Set<String> getIdeSetupTaskNames() {
        return Sets.newHashSet(getSourceGenTaskName());
    }

    @NonNull
    @Override
    public List<File> getGeneratedResourceFolders() {
        return generatedResourceFolders;
    }

    @Nullable
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    @NonNull
    @Override
    public Collection<NativeLibrary> getNativeLibraries() {
        return nativeLibraries;
    }

    @NonNull
    @Override
    public Map<String, ClassField> getBuildConfigFields() {
        return buildConfigFields;
    }

    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        return resValues;
    }

    @NonNull
    @Override
    public InstantRun getInstantRun() {
        return instantRun;
    }

    @NonNull
    @Override
    public List<File> getAdditionalRuntimeApks() {
        return additionalRuntimeApks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AndroidArtifactImpl that = (AndroidArtifactImpl) o;
        return isSigned == that.isSigned
                && Objects.equals(signingConfigName, that.signingConfigName)
                && Objects.equals(applicationId, that.applicationId)
                && Objects.equals(sourceGenTaskName, that.sourceGenTaskName)
                && Objects.equals(generatedResourceFolders, that.generatedResourceFolders)
                && Objects.equals(abiFilters, that.abiFilters)
                && Objects.equals(nativeLibraries, that.nativeLibraries)
                && Objects.equals(buildConfigFields, that.buildConfigFields)
                && Objects.equals(resValues, that.resValues)
                && Objects.equals(manifestSupplier, that.manifestSupplier)
                && Objects.equals(splitOutputsSupplier, that.splitOutputsSupplier)
                && Objects.equals(instantRun, that.instantRun)
                && Objects.equals(additionalRuntimeApks, that.additionalRuntimeApks)
                && Objects.equals(baseName, that.baseName)
                && Objects.equals(testOptions, that.testOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                splitOutputsSupplier,
                manifestSupplier,
                isSigned,
                signingConfigName,
                applicationId,
                sourceGenTaskName,
                generatedResourceFolders,
                abiFilters,
                nativeLibraries,
                buildConfigFields,
                resValues,
                instantRun,
                additionalRuntimeApks,
                baseName,
                testOptions);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("manifestProxy", manifestSupplier)
                .add("splitOutputsSupplier", splitOutputsSupplier)
                .add("isSigned", isSigned)
                .add("signingConfigName", signingConfigName)
                .add("applicationId", applicationId)
                .add("sourceGenTaskName", sourceGenTaskName)
                .add("generatedResourceFolders", generatedResourceFolders)
                .add("abiFilters", abiFilters)
                .add("nativeLibraries", nativeLibraries)
                .add("buildConfigFields", buildConfigFields)
                .add("resValues", resValues)
                .add("instantRun", instantRun)
                .add("testOptions", testOptions)
                .toString();
    }

    @Override
    @Nullable
    public TestOptions getTestOptions() {
        return testOptions;
    }
}
