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
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.SigningConfig;
import com.android.ide.common.build.ApkData;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Package each split resources into a specific signed apk file. */
public class PackageSplitRes extends BaseTask {

    private SigningConfig signingConfig;
    private File incrementalDir;
    private OutputScope outputScope;
    public FileCollection processedResources;
    public File splitResApkOutputDirectory;

    @InputFiles
    public FileCollection getProcessedResources() {
        return processedResources;
    }

    @OutputDirectory
    public File getSplitResApkOutputDirectory() {
        return splitResApkOutputDirectory;
    }

    @Nested
    @Optional
    public SigningConfig getSigningConfig() {
        return signingConfig;
    }

    @TaskAction
    protected void doFullTaskAction() throws IOException {

        outputScope.parallelForEachOutput(
                BuildOutputs.load(
                        VariantScope.TaskOutputType.DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                        processedResources),
                VariantScope.TaskOutputType.DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                VariantScope.TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                (split, output) -> {
                    if (output == null) {
                        throw new RuntimeException("Cannot find processed resources for " + split);
                    }
                    File outFile =
                            new File(
                                    splitResApkOutputDirectory,
                                    PackageSplitRes.this.getOutputFileNameForSplit(
                                            split, signingConfig != null));
                    File intDir =
                            new File(incrementalDir, FileUtils.join(split.getFilterName(), "tmp"));
                    try {
                        FileUtils.cleanOutputDir(intDir);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    try (IncrementalPackager pkg =
                            new IncrementalPackagerBuilder()
                                    .withSigning(signingConfig)
                                    .withOutputFile(outFile)
                                    .withProject(PackageSplitRes.this.getProject())
                                    .withIntermediateDir(intDir)
                                    .build()) {
                        pkg.updateAndroidResources(IncrementalRelativeFileSets.fromZip(output));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return outFile;
                });
        outputScope.save(
                VariantScope.TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                splitResApkOutputDirectory);
    }

    public String getOutputFileNameForSplit(final ApkData apkData, boolean isSigned) {
        String archivesBaseName = (String) getProject().getProperties().get("archivesBaseName");
        String apkName = archivesBaseName + "-" + apkData.getBaseName();
        return apkName + (isSigned ? "-unsigned" : "") + SdkConstants.DOT_ANDROID_PACKAGE;
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<PackageSplitRes> {

        private VariantScope scope;
        private File outputDirectory;

        public ConfigAction(VariantScope scope, File outputDirectory) {
            this.scope = scope;
            this.outputDirectory = outputDirectory;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("package", "SplitResources");
        }

        @Override
        @NonNull
        public Class<PackageSplitRes> getType() {
            return PackageSplitRes.class;
        }

        @Override
        public void execute(@NonNull PackageSplitRes packageSplitResourcesTask) {
            BaseVariantData variantData = scope.getVariantData();
            final VariantConfiguration config = variantData.getVariantConfiguration();

            packageSplitResourcesTask.outputScope = scope.getOutputScope();
            packageSplitResourcesTask.processedResources =
                    scope.getOutput(VariantScope.TaskOutputType.PROCESSED_RES);
            packageSplitResourcesTask.signingConfig = config.getSigningConfig();
            packageSplitResourcesTask.splitResApkOutputDirectory = outputDirectory;
            packageSplitResourcesTask.incrementalDir = scope.getIncrementalDir(getName());
            packageSplitResourcesTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            packageSplitResourcesTask.setVariantName(config.getFullName());
        }
    }
}
