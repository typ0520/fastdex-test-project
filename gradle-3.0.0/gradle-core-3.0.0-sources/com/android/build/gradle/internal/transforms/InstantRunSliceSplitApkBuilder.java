/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * Tasks to generate M+ style pure splits APKs with dex files.
 */
public class InstantRunSliceSplitApkBuilder extends InstantRunSplitApkBuilder {

    private final WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();
    private final boolean runSerially;

    public InstantRunSliceSplitApkBuilder(
            @NonNull Logger logger,
            @NonNull Project project,
            @NonNull InstantRunBuildContext buildContext,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCache fileCache,
            @NonNull PackagingScope packagingScope,
            @Nullable CoreSigningConfig signingConf,
            @NonNull AaptGeneration aaptGeneration,
            @NonNull AaptOptions aaptOptions,
            @NonNull File outputDirectory,
            @NonNull File supportDirectory,
            @NonNull File aaptIntermediateDirectory,
            @Nullable Boolean runAapt2Serially) {
        super(
                logger,
                project,
                buildContext,
                androidBuilder,
                fileCache,
                packagingScope,
                signingConf,
                aaptGeneration,
                aaptOptions,
                outputDirectory,
                supportDirectory,
                aaptIntermediateDirectory);
        runSerially = runAapt2Serially == null
                ? SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS
                : runAapt2Serially;

    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunSlicesApk";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.SUB_PROJECTS);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {

        // this will hold the list of split APKs to build.
        List<DexFiles> splitsToBuild = new ArrayList<>();
        if (transformInvocation.isIncremental()) {
            for (TransformInput transformInput : transformInvocation.getInputs()) {
                for (JarInput jarInput : transformInput.getJarInputs()) {
                    logger.error("InstantRunDependenciesApkBuilder received a jar file "
                            + jarInput.getFile().getAbsolutePath());
                }

                for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                    for (Map.Entry<File, Status> fileEntry : directoryInput
                            .getChangedFiles()
                            .entrySet()) {

                        // we might have several dex files in that slice, so we take the parent
                        File inputFolder = fileEntry.getKey().getParentFile();
                        switch (fileEntry.getValue()) {
                            case NOTCHANGED:
                                break;
                            case ADDED:
                            case CHANGED:
                                File[] dexFiles = inputFolder.listFiles();
                                if (dexFiles != null) {
                                    try {
                                        splitsToBuild.add(
                                                new DexFiles(dexFiles, directoryInput.getName()));
                                    } catch (Exception e) {
                                        throw new TransformException(e);
                                    }
                                }
                                break;
                            case REMOVED:
                                DexFiles dexFile = new DexFiles(
                                        ImmutableSet.of(),
                                        inputFolder.getName());

                                String outputFileName = dexFile.encodeName() + "_unaligned.apk";
                                //noinspection ResultOfMethodCallIgnored
                                new File(outputDirectory, outputFileName).delete();
                                outputFileName = dexFile.encodeName() + ".apk";
                                //noinspection ResultOfMethodCallIgnored
                                new File(outputDirectory, outputFileName).delete();
                                break;
                            default:
                                throw new TransformException(String.format(
                                        "Unhandled status %1$s for %2$s",
                                        fileEntry.getValue(),
                                        fileEntry.getKey().getAbsolutePath()));
                        }
                    }
                }
            }

        } else {
            FileUtils.cleanOutputDir(outputDirectory);
            for (TransformInput transformInput : transformInvocation.getInputs()) {
                for (JarInput jarInput : transformInput.getJarInputs()) {
                    logger.error("InstantRunDependenciesApkBuilder received a jar file "
                            + jarInput.getFile().getAbsolutePath());
                }
                for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                    File[] files = directoryInput.getFile().listFiles();
                    if (files == null) {
                        continue;
                    }
                    try {
                        splitsToBuild.add(
                                new DexFiles(ImmutableSet.copyOf(files), directoryInput.getName()));
                    } catch (Exception e) {
                        throw new TransformException(e);
                    }
                }
            }
        }

        logger.debug("Invoking aapt2 serially : {} ", runSerially);

        // now build the APKs in parallel
        splitsToBuild.forEach(split -> {
            try {
                if (runSerially) {
                    generateSplitApk(split);
                } else {
                    executor.execute(() -> generateSplitApk(split));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        if (!runSerially) {
            executor.waitForTasksWithQuickFail(true /* cancelRemaining */);
        }
    }
}
