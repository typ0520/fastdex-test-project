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

package com.android.build.gradle.tasks;

import static com.android.SdkConstants.DOT_ZIP;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.InstantAppOutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.ApplicationId;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Task to bundle a bundle of feature APKs. */
public class BundleInstantApp extends DefaultAndroidTask {

    @TaskAction
    public void taskAction() throws IOException {
        // FIXME: Make this task incremental.
        FileUtils.mkdirs(bundleDirectory);

        File bundleFile = new File(bundleDirectory, bundleName);
        FileUtils.deleteIfExists(bundleFile);

        // FIXME: Use ZFile to compress in parallel.
        try (ZipOutputStream zipOutputStream =
                new ZipOutputStream(new FileOutputStream(bundleFile))) {
            for (File apkDirectory : apkDirectories) {
                Collection<BuildOutput> buildOutputs = BuildOutputs.load(apkDirectory);
                for (BuildOutput buildOutput : buildOutputs) {
                    if (buildOutput.getType() == TaskOutputHolder.TaskOutputType.APK) {
                        File apkFile = buildOutput.getOutputFile();
                        try (FileInputStream fileInputStream = new FileInputStream(apkFile)) {
                            byte[] inputBuffer = IOUtils.toByteArray(fileInputStream);
                            zipOutputStream.putNextEntry(new ZipEntry(apkFile.getName()));
                            zipOutputStream.write(inputBuffer, 0, inputBuffer.length);
                            zipOutputStream.closeEntry();
                        }
                    }
                }
            }
        }

        // Write the json output.
        InstantAppOutputScope instantAppOutputScope =
                new InstantAppOutputScope(
                        ApplicationId.load(applicationId.getSingleFile()).getApplicationId(),
                        bundleFile,
                        apkDirectories.getFiles().stream().collect(Collectors.toList()));
        instantAppOutputScope.save(bundleDirectory);
    }

    @OutputDirectory
    @NonNull
    public File getBundleDirectory() {
        return bundleDirectory;
    }

    @Input
    @NonNull
    public String getBundleName() {
        return bundleName;
    }

    @InputFiles
    @NonNull
    public FileCollection getApplicationId() {
        return applicationId;
    }

    @InputFiles
    @NonNull
    public FileCollection getApkDirectories() {
        return apkDirectories;
    }

    private File bundleDirectory;
    private String bundleName;
    private FileCollection applicationId;
    private FileCollection apkDirectories;

    public static class ConfigAction implements TaskConfigAction<BundleInstantApp> {

        public ConfigAction(@NonNull VariantScope scope, @NonNull File bundleDirectory) {
            this.scope = scope;
            this.bundleDirectory = bundleDirectory;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("package", "InstantAppBundle");
        }

        @NonNull
        @Override
        public Class<BundleInstantApp> getType() {
            return BundleInstantApp.class;
        }

        @Override
        public void execute(@NonNull BundleInstantApp bundleInstantApp) {
            bundleInstantApp.setVariantName(scope.getFullVariantName());
            bundleInstantApp.bundleDirectory = bundleDirectory;
            bundleInstantApp.bundleName =
                    scope.getGlobalScope().getProjectBaseName()
                            + "-"
                            + scope.getVariantConfiguration().getBaseName()
                            + DOT_ZIP;
            bundleInstantApp.applicationId =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION);
            bundleInstantApp.apkDirectories =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.APK);
        }

        private final VariantScope scope;
        private final File bundleDirectory;
    }
}
