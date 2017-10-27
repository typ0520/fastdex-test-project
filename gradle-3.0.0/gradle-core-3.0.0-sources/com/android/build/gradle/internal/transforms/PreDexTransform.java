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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.DexingType;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Pre-dexing transform. This will consume {@link TransformManager#CONTENT_CLASS}, and for each of
 * the inputs, corresponding DEX will be produced. It runs if all of the following conditions hold:
 * <ul>
 *     <li>variant is native multidex or mono-dex
 *     <li>users have not explicitly disabled pre-dexing
 *     <li>minification is turned off
 * </ul>
 * or we run in instant run mode.
 *
 * <p>This transform is incremental. Only streams with changed files will be pre-dexed again. Build
 * cache {@link FileCache}, if available, is used to store DEX files of external libraries.
 */
public class PreDexTransform extends Transform {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(PreDexTransform.class);

    @NonNull private final DexOptions dexOptions;

    @NonNull private final AndroidBuilder androidBuilder;

    @Nullable private final FileCache buildCache;

    @NonNull private final DexingType dexingType;

    private final int minSdkVersion;

    public PreDexTransform(
            @NonNull DexOptions dexOptions,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCache buildCache,
            @NonNull DexingType dexingType,
            int minSdkVersion) {
        this.dexOptions = dexOptions;
        this.androidBuilder = androidBuilder;
        this.buildCache = buildCache;
        this.dexingType = dexingType;
        this.minSdkVersion = minSdkVersion;
    }

    @NonNull
    @Override
    public String getName() {
        return "preDex";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            // ATTENTION: if you add something here, consider adding the value to DexKey - it needs
            // to be saved if affects how dx is invoked.

            Map<String, Object> params = Maps.newHashMapWithExpectedSize(7);

            params.put("optimize", true);
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("multidex-mode", dexingType.name());
            params.put("java-max-heap-size", dexOptions.getJavaMaxHeapSize());
            params.put(
                    "additional-parameters",
                    Iterables.toString(dexOptions.getAdditionalParameters()));

            TargetInfo targetInfo = androidBuilder.getTargetInfo();
            Preconditions.checkState(
                    targetInfo != null,
                    "androidBuilder.targetInfo required for task '%s'.",
                    getName());
            BuildToolInfo buildTools = targetInfo.getBuildTools();
            params.put("build-tools", buildTools.getRevision().toString());
            params.put("min-sdk-version", minSdkVersion);

            return params;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, IOException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider, "Missing output provider.");

        List<JarInput> jarInputs = Lists.newArrayList();
        List<DirectoryInput> directoryInputs = Lists.newArrayList();
        for (TransformInput input : transformInvocation.getInputs()) {
            jarInputs.addAll(input.getJarInputs());
            directoryInputs.addAll(input.getDirectoryInputs());
        }

        logger.verbose("Task is incremental : %b ", transformInvocation.isIncremental());
        logger.verbose("JarInputs %s", Joiner.on(",").join(jarInputs));
        logger.verbose("DirInputs %s", Joiner.on(",").join(directoryInputs));

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        androidBuilder.getErrorReporter());

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        try {
            // hash to detect duplicate jars (due to issue with library and tests)
            final Set<String> hashes = Sets.newHashSet();
            // input files to output file map
            final Map<File, File> inputFiles = Maps.newHashMap();
            // stuff to delete. Might be folders.
            final List<File> deletedFiles = Lists.newArrayList();
            Set<File> externalLibJarFiles = Sets.newHashSet();

            // first gather the different inputs to be dexed separately.
            for (DirectoryInput directoryInput : directoryInputs) {
                File rootFolder = directoryInput.getFile();
                // The incremental mode only detect file level changes.
                // It does not handle removed root folders. However the transform
                // task will add the TransformInput right after it's removed so that it
                // can be detected by the transform.
                if (!rootFolder.exists()) {
                    // if the root folder is gone we need to remove the previous
                    // output
                    File preDexedFile = getPreDexFile(outputProvider, directoryInput);
                    if (preDexedFile.exists()) {
                        deletedFiles.add(preDexedFile);
                    }
                } else if (!transformInvocation.isIncremental()
                        || !directoryInput.getChangedFiles().isEmpty()) {
                    // add the folder for re-dexing only if we're not in incremental
                    // mode or if it contains changed files.
                    logger.verbose(
                            "Changed file for %s are %s",
                            directoryInput.getFile().getAbsolutePath(),
                            Joiner.on(",").join(directoryInput.getChangedFiles().entrySet()));
                    File preDexFile = getPreDexFile(outputProvider, directoryInput);
                    inputFiles.put(rootFolder, preDexFile);
                }
            }

            for (JarInput jarInput : jarInputs) {
                switch (jarInput.getStatus()) {
                    case NOTCHANGED:
                        if (transformInvocation.isIncremental()) {
                            break;
                        }
                        // intended fall-through
                    case CHANGED:
                    case ADDED:
                        {
                            File preDexFile = getPreDexFile(outputProvider, jarInput);
                            inputFiles.put(jarInput.getFile(), preDexFile);
                            if (jarInput.getScopes()
                                    .equals(Collections.singleton(Scope.EXTERNAL_LIBRARIES))) {
                                externalLibJarFiles.add(jarInput.getFile());
                            }
                            break;
                        }
                    case REMOVED:
                        {
                            File preDexedFile = getPreDexFile(outputProvider, jarInput);
                            if (preDexedFile.exists()) {
                                deletedFiles.add(preDexedFile);
                            }
                            break;
                        }
                }
            }

            logger.verbose("inputFiles : %s", Joiner.on(",").join(inputFiles.keySet()));
            WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

            for (Map.Entry<File, File> entry : inputFiles.entrySet()) {
                FileCache usedBuildCache =
                        getBuildCache(
                                entry.getKey(),
                                externalLibJarFiles.contains(entry.getKey()),
                                buildCache);
                Callable<Void> action =
                        new PreDexCallable(
                                entry.getKey(),
                                entry.getValue(),
                                hashes,
                                outputHandler,
                                usedBuildCache,
                                dexingType,
                                dexOptions,
                                androidBuilder,
                                minSdkVersion);
                logger.verbose("Adding PreDexCallable for %s : %s", entry.getKey(), action);
                executor.execute(action);
            }

            for (final File file : deletedFiles) {
                executor.execute(
                        () -> {
                            FileUtils.deletePath(file);
                            return null;
                        });
            }

            executor.waitForTasksWithQuickFail(false);
            logger.verbose("Done with all dexing");
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }

    /**
     * Returns the build cache if it should be used for the predex-library task, and {@code null}
     * otherwise.
     */
    @Nullable
    static FileCache getBuildCache(
            @NonNull File inputFile, boolean isExternalLib, @Nullable FileCache buildCache) {
        // We use the build cache only when it is enabled and the input file is a (non-snapshot)
        // external-library jar file
        if (buildCache == null || !isExternalLib) {
            return null;
        }
        // After the check above, here the build cache should be enabled and the input file is an
        // external-library jar file. We now check whether it is a snapshot version or not (to
        // address http://b.android.com/228623).
        // Note that the current check is based on the file path; if later on there is a more
        // reliable way to verify whether an input file is a snapshot, we should replace this check
        // with that.
        if (inputFile.getPath().contains("-SNAPSHOT")) {
            return null;
        } else {
            return buildCache;
        }
    }

    @NonNull
    private File getPreDexFile(
            @NonNull TransformOutputProvider output, @NonNull QualifiedContent qualifiedContent) {
        File contentLocation =
                output.getContentLocation(
                        qualifiedContent.getName(),
                        TransformManager.CONTENT_DEX,
                        qualifiedContent.getScopes(),
                        dexingType.isMultiDex() ? Format.DIRECTORY : Format.JAR);
        if (dexingType.isMultiDex()) {
            FileUtils.mkdirs(contentLocation);
        } else {
            FileUtils.mkdirs(contentLocation.getParentFile());
        }
        return contentLocation;
    }

    @NonNull
    static String getInstantRunFileName(@NonNull File inputFile) {
        if (inputFile.isDirectory()) {
            return inputFile.getName();
        } else {
            return inputFile.getName().replace(".", "_");
        }
    }

    @NonNull
    static String getFilename(@NonNull File inputFile, @NonNull DexingType dexingType) {
        // If multidex is enabled, this name will be used for a folder and classes*.dex files will
        // inside of it.
        String suffix = dexingType.isMultiDex() ? "" : SdkConstants.DOT_JAR;
        return FileUtils.getDirectoryNameForJar(inputFile) + suffix;
    }
}
