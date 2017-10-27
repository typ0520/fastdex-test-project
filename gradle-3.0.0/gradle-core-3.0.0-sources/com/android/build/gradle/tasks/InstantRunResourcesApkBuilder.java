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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Task for create a split apk per packaged resources.
 *
 * <p>Right now, there is only one packaged resources file in InstantRun mode, but we could decide
 * to slice the resources in the future.
 */
public class InstantRunResourcesApkBuilder extends BaseTask {

    @VisibleForTesting public static final String APK_FILE_NAME = "resources";

    private AndroidBuilder androidBuilder;
    private InstantRunBuildContext instantRunBuildContext;
    private File outputDirectory;
    private CoreSigningConfig signingConf;
    private File supportDirectory;

    private FileCollection resources;

    private OutputScope outputScope;
    private TaskOutputHolder.TaskOutputType resInputType;

    @Nested
    @Optional
    CoreSigningConfig getSigningConf() {
        return signingConf;
    }

    @Input
    String getResInputType() {
        return resInputType.name();
    }

    @InputFiles
    public FileCollection getResourcesFile() {
        return resources;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    protected void doFullTaskAction() throws IOException {

        Collection<BuildOutput> buildOutputs = BuildOutputs.load(resInputType, resources);

        outputScope.parallelForEachOutput(
                buildOutputs,
                resInputType,
                TaskOutputHolder.TaskOutputType.INSTANT_RUN_PACKAGED_RESOURCES,
                (apkData, input) -> {
                    try {
                        if (input == null) {
                            return null;
                        }
                        final File outputFile =
                                new File(
                                        outputDirectory,
                                        mangleApkName(apkData) + SdkConstants.DOT_ANDROID_PACKAGE);
                        Files.createParentDirs(outputFile);

                        // packageCodeSplitApk uses a temporary directory for incremental runs.
                        // Since we don't
                        // do incremental builds here, make sure it gets an empty directory.
                        File tempDir =
                                new File(supportDirectory, "package_" + mangleApkName(apkData));

                        FileUtils.cleanOutputDir(tempDir);

                        androidBuilder.packageCodeSplitApk(
                                input,
                                ImmutableSet.of(),
                                signingConf,
                                outputFile,
                                tempDir,
                                ApkCreatorFactories.fromProjectProperties(getProject(), true));
                        instantRunBuildContext.addChangedFile(FileType.SPLIT, outputFile);
                        return outputFile;

                    } catch (KeytoolException | PackagerException e) {
                        throw new IOException("Exception while creating resources split APK", e);
                    }
                });
    }

    static String mangleApkName(ApkData apkData) {
        return APK_FILE_NAME + "-" + apkData.getBaseName();
    }

    public static class ConfigAction implements TaskConfigAction<InstantRunResourcesApkBuilder> {

        protected final PackagingScope packagingScope;
        private final FileCollection resources;
        private final TaskOutputHolder.TaskOutputType resInputType;

        public ConfigAction(
                @NonNull TaskOutputHolder.TaskOutputType resInputType,
                @NonNull FileCollection resources,
                @NonNull PackagingScope scope) {
            this.resInputType = resInputType;
            this.resources = resources;
            this.packagingScope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return packagingScope.getTaskName("processInstantRun", "ResourcesApk");
        }

        @NonNull
        @Override
        public Class<InstantRunResourcesApkBuilder> getType() {
            return InstantRunResourcesApkBuilder.class;
        }

        @Override
        public void execute(@NonNull InstantRunResourcesApkBuilder resourcesApkBuilder) {
            resourcesApkBuilder.setVariantName(packagingScope.getFullVariantName());
            resourcesApkBuilder.resInputType = resInputType;
            resourcesApkBuilder.outputScope = packagingScope.getOutputScope();
            resourcesApkBuilder.supportDirectory =
                    packagingScope.getIncrementalDir("instant-run-resources");
            resourcesApkBuilder.androidBuilder = packagingScope.getAndroidBuilder();
            resourcesApkBuilder.signingConf = packagingScope.getSigningConfig();
            resourcesApkBuilder.instantRunBuildContext = packagingScope.getInstantRunBuildContext();
            resourcesApkBuilder.resources = resources;
            resourcesApkBuilder.outputDirectory = packagingScope.getInstantRunResourceApkFolder();
        }
    }
}
