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

package com.android.build.gradle.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.ApkInfo;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

/**
 * Checks that the manifest file has not changed since the last instant run build.
 */
public class CheckManifestInInstantRunMode extends DefaultAndroidTask {

    private static final Logger LOG = Logging.getLogger(CheckManifestInInstantRunMode.class);

    private InstantRunBuildContext buildContext;
    private File instantRunSupportDir;
    private OutputScope outputScope;
    private FileCollection instantRunManifests;
    private FileCollection processedRes;

    @InputFiles
    FileCollection getInstantRunManifests() {
        return instantRunManifests;
    }

    @InputFiles
    FileCollection getProcessedRes() {
        return processedRes;
    }

    @TaskAction
    public void checkManifestChanges() throws IOException {

        // If we are NOT instant run mode, this is an error, this task should not be running.
        if (!buildContext.isInInstantRunMode()) {
            LOG.warn("CheckManifestInInstantRunMode configured in non instant run build,"
                    + " please file a bug.");
            return;
        }

        if (instantRunManifests.getFiles().isEmpty()) {
            String message =
                    "No instant run specific merged manifests in InstantRun mode, "
                            + "please file a bug and disable InstantRun.";
            LOG.error(message);
            throw new RuntimeException(message);
        }

        if (instantRunManifests.getFiles().size() > 1) {
            String message =
                    "Full Split are not supported in InstantRun mode, "
                            + "please disable InstantRun";
            LOG.error(message);
            throw new RuntimeException(message);
        }

        // always do both, we should make sure that we are not keeping stale data for the previous
        // instance.
        // Cannot call .getLastValue() since it is not declared as an Input which
        // would call .get() before the task run.

        Collection<BuildOutput> manifestsOutputs =
                BuildOutputs.load(
                        VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                        instantRunManifests);
        Collection<BuildOutput> processedResOutputs =
                BuildOutputs.load(VariantScope.TaskOutputType.PROCESSED_RES, processedRes);

        for (BuildOutput mergedManifest : manifestsOutputs) {

            ApkInfo apkInfo = mergedManifest.getApkInfo();
            ApkData apkData = outputScope.getSplit(apkInfo.getFilters());
            if (apkData == null
                    || !apkData.isEnabled()
                    || apkData.getType() == VariantOutput.OutputType.SPLIT) {
                continue;
            }

            File manifestFile = mergedManifest.getOutputFile();
            LOG.info("CheckManifestInInstantRunMode : Merged manifest %1$s", manifestFile);
            runManifestChangeVerifier(buildContext, instantRunSupportDir, manifestFile);

            // Change THIS to not assume MAIN, time to add some API to the split scope that will
            // get MAIN, or UNIVERSAL or in case of FULL SPLIT, use the commented code to select
            // the right one. then change the code above to use the same logic to get the manifest
            // file.
            BuildOutput output =
                    OutputScope.getOutput(
                            processedResOutputs,
                            TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                            apkData);
            if (output == null) {
                throw new RuntimeException(
                        "Cannot find processed resources for "
                                + apkData
                                + " split in "
                                + Joiner.on(",")
                                        .join(
                                                outputScope.getOutputs(
                                                        VariantScope.TaskOutputType
                                                                .PROCESSED_RES)));
            }
            File resourcesApk = output.getOutputFile();

            // Cannot call .getLastValue() since it is not declared as an Input which
            // would call .get() before the task run.
            LOG.info("CheckManifestInInstantRunMode : Resource APK %1$s", resourcesApk);
            if (resourcesApk.exists()) {
                runManifestBinaryChangeVerifier(buildContext, instantRunSupportDir, resourcesApk);
            }
        }
    }

    @VisibleForTesting
    static void runManifestChangeVerifier(
            InstantRunBuildContext buildContext,
            File instantRunSupportDir,
            @NonNull File manifestFileToPackage)
            throws IOException {
        File previousManifestFile = new File(instantRunSupportDir, "manifest.xml");

        if (previousManifestFile.exists()) {
            String currentManifest =
                    Files.asCharSource(manifestFileToPackage, Charsets.UTF_8).read();
            String previousManifest =
                    Files.asCharSource(previousManifestFile, Charsets.UTF_8).read();
            if (!currentManifest.equals(previousManifest)) {
                // TODO: Deeper comparison, call out just a version change.
                buildContext.setVerifierStatus(
                        InstantRunVerifierStatus.MANIFEST_FILE_CHANGE);
                Files.copy(manifestFileToPackage, previousManifestFile);
            }
        } else {
            Files.createParentDirs(previousManifestFile);
            Files.copy(manifestFileToPackage, previousManifestFile);
            // we don't have a back up of the manifest file, better be safe and force the APK build.
            buildContext.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
        }
    }

    @VisibleForTesting
    static void runManifestBinaryChangeVerifier(
            InstantRunBuildContext buildContext,
            File instantRunSupportDir,
            @NonNull File resOutBaseNameFile)
            throws IOException {
        // get the new manifest file CRC
        String currentIterationCRC = null;
        try (JarFile jarFile = new JarFile(resOutBaseNameFile)) {
            ZipEntry entry = jarFile.getEntry(SdkConstants.ANDROID_MANIFEST_XML);
            if (entry != null) {
                currentIterationCRC = String.valueOf(entry.getCrc());
            }
        }

        File crcFile = new File(instantRunSupportDir, "manifest.crc");
        // check the manifest file binary format.
        if (crcFile.exists() && currentIterationCRC != null) {
            // compare its content with the new binary file crc.
            String previousIterationCRC = Files.readFirstLine(crcFile, Charsets.UTF_8);
            if (!currentIterationCRC.equals(previousIterationCRC)) {
                buildContext.setVerifierStatus(
                        InstantRunVerifierStatus.BINARY_MANIFEST_FILE_CHANGE);
            }
        } else {
            // we don't have a back up of the crc file, better be safe and force the APK build.
            buildContext.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
        }

        if (currentIterationCRC != null) {
            // write the new manifest file CRC.
            Files.createParentDirs(crcFile);
            Files.write(currentIterationCRC, crcFile, Charsets.UTF_8);
        }
    }

    public static class ConfigAction implements TaskConfigAction<CheckManifestInInstantRunMode> {

        @NonNull
        protected final TransformVariantScope transformVariantScope;
        @NonNull
        protected final InstantRunVariantScope instantRunVariantScope;
        @NonNull protected final FileCollection instantRunMergedManifests;
        @NonNull protected final FileCollection processedResources;

        public ConfigAction(
                @NonNull TransformVariantScope transformVariantScope,
                @NonNull InstantRunVariantScope instantRunVariantScope,
                @NonNull FileCollection instantRunMergedManifests,
                @NonNull FileCollection processedResources) {
            this.transformVariantScope = transformVariantScope;
            this.instantRunVariantScope = instantRunVariantScope;
            this.instantRunMergedManifests = instantRunMergedManifests;
            this.processedResources = processedResources;
        }

        @NonNull
        @Override
        public String getName() {
            return transformVariantScope.getTaskName("checkManifestChanges");
        }

        @NonNull
        @Override
        public Class<CheckManifestInInstantRunMode> getType() {
            return CheckManifestInInstantRunMode.class;
        }

        @Override
        public void execute(@NonNull CheckManifestInInstantRunMode task) {

            task.instantRunManifests = instantRunMergedManifests;
            task.processedRes = processedResources;
            task.outputScope = transformVariantScope.getOutputScope();
            task.buildContext = instantRunVariantScope.getInstantRunBuildContext();
            task.instantRunSupportDir =
                    new File(instantRunVariantScope.getInstantRunSupportDir(), "manifestChecker");
            task.setVariantName(transformVariantScope.getFullVariantName());
        }
    }
}
