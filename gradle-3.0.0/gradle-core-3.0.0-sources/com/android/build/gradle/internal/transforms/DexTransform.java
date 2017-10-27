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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexingType;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.gradle.api.file.FileCollection;

/**
 * Dexing as a transform.
 *
 * <p>In case pre-dexing is not run (see {@link PreDexTransform} docs when we do not run it), this
 * consumes all the available class streams and creates a DEX file (or more in the case of
 * multi-dex). In case of mono-dex with enabled pre-dexing, this transform will consume all DEX
 * files, and merge them into final DEX.
 *
 * <p>Please note that this transform will <strong>not</strong> run in case of native multidex with
 * enabled pre-dexing. Reason is that the {@link PreDexTransform} will convert all classes streams,
 * and we can package those DEX files.
 */
public class DexTransform extends Transform {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(DexTransform.class);

    @NonNull private final DexOptions dexOptions;

    @NonNull private final DexingType dexingType;

    private boolean preDexEnabled;

    @Nullable private final FileCollection mainDexListFile;

    @NonNull private final TargetInfo targetInfo;
    @NonNull private final DexByteCodeConverter dexByteCodeConverter;
    @NonNull private final ErrorReporter errorReporter;
    private final int minSdkVersion;

    public DexTransform(
            @NonNull DexOptions dexOptions,
            @NonNull DexingType dexingType,
            boolean preDexEnabled,
            @Nullable FileCollection mainDexListFile,
            @NonNull TargetInfo targetInfo,
            @NonNull DexByteCodeConverter dexByteCodeConverter,
            @NonNull ErrorReporter errorReporter,
            int minSdkVersion) {
        this.dexOptions = dexOptions;
        this.dexingType = dexingType;
        this.preDexEnabled = preDexEnabled;
        this.mainDexListFile = mainDexListFile;
        this.targetInfo = targetInfo;
        this.dexByteCodeConverter = dexByteCodeConverter;
        this.errorReporter = errorReporter;
        this.minSdkVersion = minSdkVersion;
    }

    @NonNull
    @Override
    public String getName() {
        return "dex";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        if (!preDexEnabled) {
            // we will take all classes and convert to DEX
            return TransformManager.CONTENT_CLASS;
        } else {
            // consume DEX files and merge them
            return TransformManager.CONTENT_DEX;
        }
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
    public Collection<SecondaryFile> getSecondaryFiles() {
        if (mainDexListFile != null) {
            return ImmutableList.of(SecondaryFile.nonIncremental(mainDexListFile));
        }

        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            // ATTENTION: if you add something here, consider adding the value to DexKey - it needs
            // to be saved if affects how dx is invoked.
            Map<String, Object> params = Maps.newHashMapWithExpectedSize(8);

            params.put("optimize", true);
            params.put("predex", preDexEnabled);
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("dexing-mode", dexingType.name());
            params.put("java-max-heap-size", dexOptions.getJavaMaxHeapSize());
            params.put(
                    "additional-parameters",
                    Iterables.toString(dexOptions.getAdditionalParameters()));

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
        return false;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, IOException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider,
                "Missing output object for transform " + getName());

        if (!dexOptions.getKeepRuntimeAnnotatedClasses() && mainDexListFile == null) {
            logger.info("DexOptions.keepRuntimeAnnotatedClasses has no affect in native multidex.");
        }

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);

        outputProvider.deleteAll();

        try {
            // these are either classes that should be converted directly to DEX, or DEX(s) to merge
            Collection<File> transformInputs =
                    TransformInputUtil.getAllFiles(transformInvocation.getInputs());

            File outputDir =
                    outputProvider.getContentLocation(
                            "main",
                            getOutputTypes(),
                            TransformManager.SCOPE_FULL_PROJECT,
                            Format.DIRECTORY);

            // this deletes and creates the dir for the output
            FileUtils.cleanOutputDir(outputDir);

            File mainDexList = null;
            if (mainDexListFile != null && dexingType == DexingType.LEGACY_MULTIDEX) {
                mainDexList = mainDexListFile.getSingleFile();
            }

            dexByteCodeConverter.convertByteCode(
                    transformInputs,
                    outputDir,
                    dexingType.isMultiDex(),
                    mainDexList,
                    dexOptions,
                    outputHandler,
                    minSdkVersion);
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }
}
