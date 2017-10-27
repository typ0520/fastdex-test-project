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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.SigningException;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.signing.KeytoolException;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Package a abi dimension specific split APK */
public class PackageSplitAbi extends BaseTask {

    private FileCollection processedAbiResources;

    private File outputDirectory;

    private boolean jniDebuggable;

    private SigningConfig signingConfig;

    private FileCollection jniFolders;

    private AndroidVersion minSdkVersion;

    private File incrementalDir;

    private Collection<String> aaptOptionsNoCompress;
    private OutputScope outputScope;

    private Set<String> splits;

    @InputFiles
    public FileCollection getProcessedAbiResources() {
        return processedAbiResources;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @Input
    public Set<String> getSplits() {
        return splits;
    }

    @Input
    public boolean isJniDebuggable() {
        return jniDebuggable;
    }

    @Nested
    @Optional
    public SigningConfig getSigningConfig() {
        return signingConfig;
    }

    @InputFiles
    public FileCollection getJniFolders() {
        return jniFolders;
    }

    @Input
    public int getMinSdkVersion() {
        return minSdkVersion.getFeatureLevel();
    }

    @Input
    public Collection<String> getNoCompressExtensions() {
        return aaptOptionsNoCompress != null ? aaptOptionsNoCompress : Collections.emptyList();
    }

    @TaskAction
    protected void doFullTaskAction()
            throws SigningException, KeytoolException, PackagerException, IOException {

        FileUtils.cleanOutputDir(incrementalDir);

        outputScope.parallelForEachOutput(
                BuildOutputs.load(
                        VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES, processedAbiResources),
                VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES,
                VariantScope.TaskOutputType.ABI_PACKAGED_SPLIT,
                (split, output) -> {
                    String apkName = getApkName(split);

                    File outFile = new File(outputDirectory, apkName);

                    try (IncrementalPackager pkg =
                            new IncrementalPackagerBuilder()
                                    .withOutputFile(outFile)
                                    .withSigning(signingConfig)
                                    .withCreatedBy(getBuilder().getCreatedBy())
                                    .withMinSdk(getMinSdkVersion())
                                    // .withManifest(manifest)
                                    .withAaptOptionsNoCompress(aaptOptionsNoCompress)
                                    .withIntermediateDir(incrementalDir)
                                    .withProject(getProject())
                                    .withDebuggableBuild(isJniDebuggable())
                                    .withJniDebuggableBuild(isJniDebuggable())
                                    .withAcceptedAbis(ImmutableSet.of(split.getFilterName()))
                                    .build()) {
                        ImmutableMap<RelativeFile, FileStatus> nativeLibs =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());
                        pkg.updateNativeLibraries(nativeLibs);

                        ImmutableMap<RelativeFile, FileStatus> androidResources =
                                IncrementalRelativeFileSets.fromZip(output);
                        pkg.updateAndroidResources(androidResources);
                    }
                    return outFile;
                });
        outputScope.save(VariantScope.TaskOutputType.ABI_PACKAGED_SPLIT, outputDirectory);
    }

    private String getApkName(final ApkData apkData) {
        String archivesBaseName = (String) getProject().getProperties().get("archivesBaseName");
        String apkName = archivesBaseName + "-" + apkData.getBaseName();
        return apkName
                + (getSigningConfig() == null ? "-unsigned" : "")
                + SdkConstants.DOT_ANDROID_PACKAGE;
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<PackageSplitAbi> {

        private VariantScope scope;
        private File outputDirectory;
        private FileCollection processedAbiResources;

        public ConfigAction(
                VariantScope scope, File outputDirectory, FileCollection processedAbiResources) {
            this.scope = scope;
            this.outputDirectory = outputDirectory;
            this.processedAbiResources = processedAbiResources;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("package", "SplitAbi");
        }

        @Override
        @NonNull
        public Class<PackageSplitAbi> getType() {
            return PackageSplitAbi.class;
        }

        @Override
        public void execute(@NonNull PackageSplitAbi packageSplitAbiTask) {
            VariantConfiguration config = this.scope.getVariantConfiguration();
            packageSplitAbiTask.processedAbiResources = processedAbiResources;
            packageSplitAbiTask.outputScope = scope.getOutputScope();
            packageSplitAbiTask.signingConfig = config.getSigningConfig();
            packageSplitAbiTask.outputDirectory = outputDirectory;
            packageSplitAbiTask.setAndroidBuilder(this.scope.getGlobalScope().getAndroidBuilder());
            packageSplitAbiTask.setVariantName(config.getFullName());
            packageSplitAbiTask.minSdkVersion = config.getMinSdkVersion();
            packageSplitAbiTask.incrementalDir =
                    scope.getIncrementalDir(packageSplitAbiTask.getName());

            packageSplitAbiTask.aaptOptionsNoCompress =
                    scope.getGlobalScope().getExtension().getAaptOptions().getNoCompress();
            packageSplitAbiTask.jniDebuggable = config.getBuildType().isJniDebuggable();

            packageSplitAbiTask.jniFolders =
                    scope.getTransformManager()
                            .getPipelineOutputAsFileCollection(StreamFilter.NATIVE_LIBS);
            packageSplitAbiTask.jniDebuggable = config.getBuildType().isJniDebuggable();
            packageSplitAbiTask.splits =
                    scope.getVariantData().getFilters(OutputFile.FilterType.ABI);
        }
    }
}
