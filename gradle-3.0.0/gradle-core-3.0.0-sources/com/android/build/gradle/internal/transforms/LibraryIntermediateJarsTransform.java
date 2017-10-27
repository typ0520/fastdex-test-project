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

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES;
import static com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.tasks.annotations.TypedefRemover;
import com.android.builder.packaging.ZipEntryFilter;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A Transforms that takes the project/project local streams for CLASSES and RESOURCES,
 * and processes and outputs fine grained jars that can be consumed by other projects.
 *
 * This typically tries to output the following jars:
 * - main jar (class only)
 * - local jars (class only)
 * - java resources (both scopes).
 *
 * If the input contains both scopes, then the output will only be in the main jar.
 *
 * Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the given folder.
 */
public class LibraryIntermediateJarsTransform extends LibraryBaseTransform {

    private static final Pattern CLASS_PATTERN = Pattern.compile(".*\\.class$");
    private static final Pattern META_INF_PATTERN = Pattern.compile("^META-INF/.*$");
    @NonNull
    private final File resJarLocation;

    public LibraryIntermediateJarsTransform(
            @NonNull File mainClassLocation,
            @NonNull File resJarLocation,
            @Nullable File typedefRecipe,
            @NonNull String packageName,
            boolean packageBuildConfig) {
        super(mainClassLocation, null, typedefRecipe, packageName, packageBuildConfig);
        this.resJarLocation = resJarLocation;
    }

    @NonNull
    @Override
    public String getName() {
        return "prepareIntermediateJars";
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of(mainClassLocation, resJarLocation);
    }

    @Override
    public boolean isIncremental() {
        // TODO used mainly to detect differences between the 3 outputs. Could be improved with incremental support inside each output.
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws TransformException, InterruptedException, IOException {
        if (typedefRecipe != null && !typedefRecipe.exists()) {
            throw new IllegalStateException("Type def recipe not found: " + typedefRecipe);
        }
        final boolean incrementalDisabled = !invocation.isIncremental();
        List<Pattern> excludePatterns = computeExcludeList();

        // first look for what inputs we have. There shouldn't be that many inputs so it should
        // be quick and it'll allow us to minimize jar merging if we don't have to.
        boolean mainClassInputChanged = incrementalDisabled;
        List<QualifiedContent> mainClassInputs = new ArrayList<>();
        boolean resJarInputChanged = incrementalDisabled;
        List<QualifiedContent> resJarInputs = new ArrayList<>();

        for (TransformInput input : invocation.getReferencedInputs()) {
            for (JarInput jarInput : input.getJarInputs()) {
                final boolean changed = jarInput.getStatus() != Status.NOTCHANGED;

                // handle res and java separately, as we'll go through all the inputs anyway
                // and if they're jars will just look inside for either.
                if (jarInput.getContentTypes().contains(RESOURCES)) {
                    resJarInputs.add(jarInput);
                    resJarInputChanged |= changed;
                }

                if (jarInput.getContentTypes().contains(CLASSES)) {
                    mainClassInputs.add(jarInput);
                    mainClassInputChanged |= changed;
                }
            }

            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                final boolean changed = !dirInput.getChangedFiles().isEmpty();

                // handle res and java separately, as we'll go through all the inputs anyway
                // and if they're jars will just look inside for either.
                if (dirInput.getContentTypes().contains(RESOURCES)) {
                    resJarInputs.add(dirInput);
                    resJarInputChanged |= changed;
                }

                if (dirInput.getContentTypes().contains(CLASSES)) {
                    mainClassInputs.add(dirInput);
                    mainClassInputChanged |= changed;
                }
            }
        }

        WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

        if (mainClassInputChanged) {
            executor.execute(() -> {
                handleMainClass(mainClassInputs, excludePatterns);
                return null;
            });
        }

        if (resJarInputChanged) {
            executor.execute(() -> {
                handleMainRes(resJarInputs);
                return null;
            });
        }

        executor.waitForTasksWithQuickFail(true);
    }

    private void handleMainClass(
            @NonNull List<QualifiedContent> mainClassInputs,
            @NonNull List<Pattern> excludePatterns) throws IOException {
        FileUtils.deleteIfExists(mainClassLocation);
        FileUtils.mkdirs(mainClassLocation.getParentFile());

        // Include both the classes and META-INF files in there as it can be used during
        // compilation. For instance, META-INF pattern will include files like service loaders
        // for annotation processors, or kotlin modules. Both of them are needed by the consuming
        // compiler.
        final ZipEntryFilter filter =
                archivePath ->
                        (CLASS_PATTERN.matcher(archivePath).matches()
                                        || META_INF_PATTERN.matcher(archivePath).matches())
                                && checkEntry(excludePatterns, archivePath);
        TypedefRemover typedefRemover =
                typedefRecipe != null ? new TypedefRemover().setTypedefFile(typedefRecipe) : null;

        handleJarOutput(mainClassInputs, mainClassLocation, filter, typedefRemover);
    }

    private void handleMainRes(
            @NonNull List<QualifiedContent> resJarInputs) throws IOException {
        FileUtils.deleteIfExists(resJarLocation);
        FileUtils.mkdirs(resJarLocation.getParentFile());

        // Only remove the classes files here. Because the main class jar (above) is only consumed
        // as a jar of classes, we need to include the META-INF files here as well so that
        // the file find their way in the APK.
        final ZipEntryFilter filter = archivePath -> !CLASS_PATTERN.matcher(archivePath).matches();

        handleJarOutput(resJarInputs, resJarLocation, filter, null);
    }

    private static void handleJarOutput(
            @NonNull List<QualifiedContent> inputs,
            @NonNull File toFile,
            @Nullable ZipEntryFilter filter,
            @Nullable TypedefRemover typedefRemover)
            throws IOException {
        if (inputs.size() == 1) {
            QualifiedContent content = inputs.get(0);

            if (content instanceof JarInput) {
                copyJarWithContentFilter(content.getFile(), toFile, filter);
            } else {
                jarFolderToLocation(content.getFile(), toFile, filter, typedefRemover);
            }
        } else {
            mergeInputsToLocation(inputs, toFile, true, filter, typedefRemover);
        }
    }
}
