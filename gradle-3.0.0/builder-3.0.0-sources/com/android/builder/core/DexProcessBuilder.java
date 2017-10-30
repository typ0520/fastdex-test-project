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

package com.android.builder.core;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessEnvBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A builder to create a dex-specific ProcessInfoBuilder
 */
public class DexProcessBuilder extends ProcessEnvBuilder<DexProcessBuilder> {

    static final Revision DX_OUT_OF_PROCESS_MIN_SDK_SUPPORT = new Revision(26, 0, 0, 2);

    @NonNull private final File outputFile;
    private boolean verbose = false;
    private boolean multiDex = false;
    @Nullable private File mainDexList = null;
    @NonNull private Set<File> inputs = Sets.newHashSet();
    private int minSdkVersion;

    /** Returns if specifying min sdk version is supported by dx in the build tools. */
    public static boolean isMinSdkVersionSupported(@NonNull BuildToolInfo buildToolInfo) {
        return buildToolInfo.getRevision().compareTo(DX_OUT_OF_PROCESS_MIN_SDK_SUPPORT) >= 0;
    }

    public DexProcessBuilder(@NonNull File outputFile) {
        this.outputFile = outputFile;
    }

    @NonNull
    public DexProcessBuilder setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    @NonNull
    public DexProcessBuilder setMultiDex(boolean multiDex) {
        this.multiDex = multiDex;
        return this;
    }

    @NonNull
    public DexProcessBuilder setMainDexList(@Nullable File mainDexList) {
        this.mainDexList = mainDexList;
        return this;
    }

    @NonNull
    public DexProcessBuilder addInput(@NonNull File input) {
        inputs.add(input);
        return this;
    }

    @NonNull
    public DexProcessBuilder addInputs(@NonNull Collection<File> inputs) {
        this.inputs.addAll(inputs);
        return this;
    }

    public int getMinSdkVersion() {
        return minSdkVersion;
    }

    @NonNull
    public DexProcessBuilder setMinSdkVersion(int minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
        return this;
    }

    @NonNull
    public File getOutputFile() {
        return outputFile;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isMultiDex() {
        return multiDex;
    }

    @Nullable
    public File getMainDexList() {
        return mainDexList;
    }

    @NonNull
    public Set<File> getInputs() {
        return inputs;
    }

    @NonNull
    public JavaProcessInfo build(
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull DexOptions dexOptions) throws ProcessException {

        checkState(buildToolInfo.getRevision().compareTo(AndroidBuilder.MIN_BUILD_TOOLS_REV) >= 0);

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.addEnvironments(mEnvironment);

        String dx = buildToolInfo.getPath(BuildToolInfo.PathId.DX_JAR);
        if (dx == null || !new File(dx).isFile()) {
            throw new IllegalStateException("dx.jar is missing");
        }

        builder.setClasspath(dx);
        builder.setMain("com.android.dx.command.Main");

        if (dexOptions.getJavaMaxHeapSize() != null) {
            builder.addJvmArg("-Xmx" + dexOptions.getJavaMaxHeapSize());
        } else {
            builder.addJvmArg("-Xmx1024M");
        }

        builder.addArgs("--dex");

        if (verbose) {
            builder.addArgs("--verbose");
        }

        if (dexOptions.getJumboMode()) {
            builder.addArgs("--force-jumbo");
        }

        Integer threadCount = dexOptions.getThreadCount();
        if (threadCount == null) {
            builder.addArgs("--num-threads=4");
        } else {
            builder.addArgs("--num-threads=" + threadCount);
        }

        if (multiDex) {
            builder.addArgs("--multi-dex");

            if (mainDexList != null) {
                builder.addArgs("--main-dex-list", mainDexList.getAbsolutePath());
            }
        }

        for (String arg : dexOptions.getAdditionalParameters()) {
            builder.addArgs(arg);
        }

        builder.addArgs("--output", outputFile.getAbsolutePath());

        if (isMinSdkVersionSupported(buildToolInfo)) {
            builder.addArgs("--min-sdk-version", Integer.toString(getMinSdkVersion()));
        }

        // input
        builder.addArgs(getFilesToAdd());

        return builder.createJavaProcess();
    }

    @NonNull
    public List<String> getFilesToAdd()
            throws ProcessException {
        // remove non-existing files.
        Set<File> existingFiles = Sets.filter(inputs, input -> input != null && input.exists());

        if (existingFiles.isEmpty()) {
            throw new ProcessException("No files to pass to dex.");
        }

        Collection<File> files = existingFiles;

        // convert to String-based paths.
        List<String> filePathList = Lists.newArrayListWithCapacity(files.size());
        for (File f : files) {
            filePathList.add(f.getAbsolutePath());
        }

        return filePathList;
    }
}
