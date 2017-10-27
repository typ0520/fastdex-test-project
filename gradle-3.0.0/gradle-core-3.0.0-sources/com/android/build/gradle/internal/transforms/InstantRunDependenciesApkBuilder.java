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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * Transform to generate the dependencies APK when deploying instant-run enabled application in
 * multi-apk mode.
 *
 * <p>In this context, the transform will consume all external dependencies and package them in a
 * single split apk file.
 */
public class InstantRunDependenciesApkBuilder extends InstantRunSplitApkBuilder {

    private static final String APK_FILE_NAME = "dependencies";

    public InstantRunDependenciesApkBuilder(
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
            @NonNull File aaptIntermediateDirectory) {
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
    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunDependenciesApk";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.EXTERNAL_LIBRARIES);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }


    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {

        ImmutableSet.Builder<File> dexFiles = ImmutableSet.builder();
        for (TransformInput transformInput : transformInvocation.getInputs()) {
            for (JarInput jarInput : transformInput.getJarInputs()) {
                logger.error("InstantRunDependenciesApkBuilder received a jar file "
                        + jarInput.getFile().getAbsolutePath());
            }
            // we are not really an incremental transform. All we need to know is that at least
            // one of our file has changed in any way and trigger a full rebuild of the
            // dependencies split apk.
            boolean anyChangeOfInterest = transformInput.getDirectoryInputs().stream().anyMatch(
                    directoryInput -> !directoryInput.getChangedFiles().isEmpty());

            // if we are in incremental mode, and not change interest us, just return.
            if (transformInvocation.isIncremental() && !anyChangeOfInterest) {
                return;
            }

            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                File[] files = directoryInput.getFile().listFiles();
                if (files != null) {
                    dexFiles.add(files);
                }
            }
        }
        ImmutableSet<File> listOfDexes = dexFiles.build();
        if (listOfDexes.isEmpty()) {
            return;
        }
        try {
            generateSplitApk(new DexFiles(listOfDexes, APK_FILE_NAME));
        } catch (Exception e) {
            logger.error("Error while generating dependencies split APK", e);
            throw new TransformException(e);
        }
    }

}
