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
package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputProperty;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.ide.common.build.ApkData;
import com.android.manifmerger.ManifestProvider;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

/**
 * A task that processes the manifest for test modules and tests in androidTest.
 *
 * <p>For both test modules and tests in androidTest process is the same, expect
 * for how the tested application id is extracted.</p>
 *
 * <p>Tests in androidTest get that info form the
 * {@link VariantConfiguration#getTestedApplicationId()}, while the test modules get the info from
 * the published intermediate manifest with type {@link AndroidArtifacts#TYPE_METADATA}
 * of the tested app.</p>
 */
public class ProcessTestManifest extends ManifestProcessorTask {

    @Nullable
    private FileCollection testTargetMetadata;

    @Nullable
    private File testManifestFile;

    /** Whether there's just a single APK with both test and tested code. */
    private boolean onlyTestApk;

    private File tmpDir;
    private String testApplicationId;
    private String testedApplicationId;
    private Supplier<String> minSdkVersion;
    private Supplier<String> targetSdkVersion;
    private Supplier<String> instrumentationRunner;
    private Supplier<Boolean> handleProfiling;
    private Supplier<Boolean> functionalTest;
    private Supplier<Map<String, Object>> placeholdersValues;

    private ArtifactCollection manifests;

    private Supplier<String> testLabel;

    private OutputScope outputScope;

    public OutputScope getOutputScope() {
        return outputScope;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        if (testedApplicationId == null && testTargetMetadata == null) {
            throw new RuntimeException("testedApplicationId and testTargetMetadata are null");
        }
        String testedApplicationId = this.getTestedApplicationId();
        if (!onlyTestApk && testTargetMetadata != null) {
            Collection<BuildOutput> manifestOutputs =
                    BuildOutputs.load(
                            TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, testTargetMetadata);
            java.util.Optional<BuildOutput> mainSplit =
                    manifestOutputs
                            .stream()
                            .filter(
                                    output ->
                                            output.getApkInfo().getType()
                                                    != VariantOutput.OutputType.SPLIT)
                            .findFirst();

            if (mainSplit.isPresent()) {
                testedApplicationId =
                        mainSplit.get().getProperties().get(BuildOutputProperty.PACKAGE_ID);
            } else {
                throw new RuntimeException("cannot find main APK");
            }
        }
        List<ApkData> apkDatas = outputScope.getApkDatas();
        if (apkDatas.isEmpty()) {
            throw new RuntimeException("No output defined for test module, please file a bug");
        }
        if (apkDatas.size() > 1) {
            throw new RuntimeException(
                    "Test modules only support a single split, this one defines"
                            + Joiner.on(",").join(apkDatas));
        }
        ApkData mainApkData = apkDatas.get(0);

        File manifestOutputFolder =
                new File(getManifestOutputDirectory(), mainApkData.getDirName());
        FileUtils.mkdirs(manifestOutputFolder);
        File manifestOutputFile = new File(manifestOutputFolder, SdkConstants.ANDROID_MANIFEST_XML);

        getBuilder()
                .mergeManifestsForTestVariant(
                        getTestApplicationId(),
                        getMinSdkVersion(),
                        getTargetSdkVersion(),
                        testedApplicationId,
                        getInstrumentationRunner(),
                        getHandleProfiling(),
                        getFunctionalTest(),
                        getTestLabel(),
                        getTestManifestFile(),
                        computeProviders(),
                        getPlaceholdersValues(),
                        manifestOutputFile,
                        getTmpDir());
        outputScope.addOutputForSplit(
                VariantScope.TaskOutputType.MERGED_MANIFESTS, mainApkData, manifestOutputFile);
        outputScope.save(
                VariantScope.TaskOutputType.MERGED_MANIFESTS, getManifestOutputDirectory());
    }

    @Nullable
    @Override
    public File getAaptFriendlyManifestOutputFile() {
        return null;
    }

    @InputFile
    @Optional
    public File getTestManifestFile() {
        return testManifestFile;
    }

    public void setTestManifestFile(File testManifestFile) {
        this.testManifestFile = testManifestFile;
    }

    public File getTmpDir() {
        return tmpDir;
    }

    public void setTmpDir(File tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Input
    public String getTestApplicationId() {
        return testApplicationId;
    }

    public void setTestApplicationId(String testApplicationId) {
        this.testApplicationId = testApplicationId;
    }

    @Input
    @Optional
    public String getTestedApplicationId() {
        return testedApplicationId;
    }

    public void setTestedApplicationId(String testedApplicationId) {
        this.testedApplicationId = testedApplicationId;
    }

    @Input
    public String getMinSdkVersion() {
        return minSdkVersion.get();
    }

    public void setMinSdkVersion(String minSdkVersion) {
        this.minSdkVersion = () -> minSdkVersion;
    }

    @Input
    public String getTargetSdkVersion() {
        return targetSdkVersion.get();
    }

    public void setTargetSdkVersion(String targetSdkVersion) {
        this.targetSdkVersion = () -> targetSdkVersion;
    }

    @Input
    public String getInstrumentationRunner() {
        return instrumentationRunner.get();
    }

    @Input
    public Boolean getHandleProfiling() {
        return handleProfiling.get();
    }

    @Input
    public Boolean getFunctionalTest() {
        return functionalTest.get();
    }

    @Input
    @Optional
    public String getTestLabel() {
        return testLabel.get();
    }

    @Input
    public Map<String, Object> getPlaceholdersValues() {
        return placeholdersValues.get();
    }

    @InputFiles
    @Optional
    @Nullable
    public FileCollection getTestTargetMetadata() {
        return testTargetMetadata;
    }

    /**
     * Compute the final list of providers based on the manifest file collection.
     * @return the list of providers.
     */
    public List<ManifestProvider> computeProviders() {
        final Set<ResolvedArtifactResult> artifacts = manifests.getArtifacts();
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size());

        for (ResolvedArtifactResult artifact : artifacts) {
            providers.add(new MergeManifests.ConfigAction.ManifestProviderImpl(
                    artifact.getFile(),
                    MergeManifests.getArtifactName(artifact)));
        }

        return providers;
    }

    @InputFiles
    public FileCollection getManifests() {
        return manifests.getArtifactFiles();
    }

    public static class ConfigAction implements TaskConfigAction<ProcessTestManifest> {

        @NonNull
        private final VariantScope scope;

        @Nullable
        private final FileCollection testTargetMetadata;

        public ConfigAction(
                @NonNull VariantScope scope,
                @Nullable FileCollection testTargetMetadata){
            this.scope = scope;
            this.testTargetMetadata = testTargetMetadata;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "Manifest");
        }

        @NonNull
        @Override
        public Class<ProcessTestManifest> getType() {
            return ProcessTestManifest.class;
        }

        @Override
        public void execute(@NonNull final ProcessTestManifest processTestManifestTask) {

            final VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    scope.getVariantConfiguration();

            processTestManifestTask.setTestManifestFile(config.getMainManifest());
            processTestManifestTask.outputScope = scope.getOutputScope();

            processTestManifestTask.setTmpDir(FileUtils.join(
                    scope.getGlobalScope().getIntermediatesDir(),
                    "tmp",
                    "manifest",
                    scope.getDirName()));

            processTestManifestTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processTestManifestTask.setVariantName(config.getFullName());


            processTestManifestTask.minSdkVersion =
                    TaskInputHelper.memoize(config.getMinSdkVersion()::getApiString);

            processTestManifestTask.targetSdkVersion =
                    TaskInputHelper.memoize(config.getTargetSdkVersion()::getApiString);

            processTestManifestTask.testTargetMetadata = testTargetMetadata;
            processTestManifestTask.setTestApplicationId(config.getTestApplicationId());

            // will only be used if testTargetMetadata is null.
            processTestManifestTask.setTestedApplicationId(config.getTestedApplicationId());

            VariantConfiguration testedConfig = config.getTestedConfig();
            processTestManifestTask.onlyTestApk =
                    testedConfig != null && testedConfig.getType() == VariantType.LIBRARY;

            processTestManifestTask.instrumentationRunner =
                    TaskInputHelper.memoize(config::getInstrumentationRunner);
            processTestManifestTask.handleProfiling =
                    TaskInputHelper.memoize(config::getHandleProfiling);
            processTestManifestTask.functionalTest =
                    TaskInputHelper.memoize(config::getFunctionalTest);
            processTestManifestTask.testLabel = TaskInputHelper.memoize(config::getTestLabel);

            processTestManifestTask.manifests = scope.getArtifactCollection(
                    RUNTIME_CLASSPATH, ALL, MANIFEST);

            processTestManifestTask.setManifestOutputDirectory(scope.getManifestOutputDirectory());

            processTestManifestTask.placeholdersValues =
                    TaskInputHelper.memoize(config::getManifestPlaceholders);

            scope.getVariantData()
                    .addTask(TaskContainer.TaskKind.PROCESS_MANIFEST, processTestManifestTask);
        }
    }
}
