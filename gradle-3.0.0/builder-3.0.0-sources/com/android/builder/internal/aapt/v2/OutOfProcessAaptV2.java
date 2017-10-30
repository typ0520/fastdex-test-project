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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AbstractProcessExecutionAapt;
import com.android.builder.png.QueuedCruncher;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.tools.aapt2.Aapt2RenamingConventions;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import java.io.File;

/**
 * Implementation of {@link com.android.builder.internal.aapt.Aapt} that uses out-of-process
 * execution of {@code aapt2}.
 *
 * <p>Deprecated now, use {@link com.android.builder.internal.aapt.v2.AaptV2Jni} instead.
 */
@Deprecated
public class OutOfProcessAaptV2 extends AbstractProcessExecutionAapt {

    /**
     * Buildtools version for which {@code aapt} can run in server mode and, therefore,
     * {@link QueuedCruncher} can be used.
     */
    private static final Revision VERSION_FOR_SERVER_AAPT = new Revision(22, 0, 0);

    /**
     * Build tools.
     */
    @NonNull
    private final BuildToolInfo mBuildToolInfo;

    /**
     * Directory where to store intermediate files.
     */
    @NonNull
    private final File mIntermediateDir;

    /**
     * Creates a new entry point to the original {@code aapt}.
     *
     * @param processExecutor the executor for external processes
     * @param processOutputHandler the handler to process the executed process' output
     * @param buildToolInfo the build tools to use
     * @param intermediateDir directory where to store intermediate files
     * @param logger logger to use
     */
    public OutOfProcessAaptV2(
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull File intermediateDir,
            @NonNull ILogger logger) {
        super(processExecutor, processOutputHandler);

        Preconditions.checkArgument(
                BuildToolInfo.PathId.AAPT2.isPresentIn(buildToolInfo.getRevision()),
                "Aapt2 requires newer build tools");
        Preconditions.checkArgument(
                intermediateDir.isDirectory(), "!intermediateDir.isDirectory()");

        mBuildToolInfo = buildToolInfo;
        mIntermediateDir = intermediateDir;
    }

    @Nullable
    @Override
    protected CompileInvocation makeCompileProcessBuilder(@NonNull CompileResourceRequest request)
            throws AaptException {
        Preconditions.checkArgument(request.getInput().isFile(), "!file.isFile()");
        Preconditions.checkArgument(request.getOutput().isDirectory(), "!output.isDirectory()");

        return new CompileInvocation(
                new ProcessInfoBuilder()
                        .setExecutable(getAapt2ExecutablePath())
                        .addArgs("compile")
                        .addArgs(AaptV2CommandBuilder.makeCompile(request)),
                new File(
                        request.getOutput(),
                        Aapt2RenamingConventions.compilationRename(request.getInput())));
    }

    @NonNull
    @Override
    protected ProcessInfoBuilder makePackageProcessBuilder(@NonNull AaptPackageConfig config)
            throws AaptException {
        return new ProcessInfoBuilder()
                .setExecutable(getAapt2ExecutablePath())
                .addArgs("link")
                .addArgs(AaptV2CommandBuilder.makeLink(config, mIntermediateDir));
    }

    /**
     * Obtains the path for the {@code aapt} executable.
     *
     * @return the path
     */
    @NonNull
    private String getAapt2ExecutablePath() {
        String aapt2 = mBuildToolInfo.getPath(BuildToolInfo.PathId.AAPT2);
        if (aapt2 == null || !new File(aapt2).isFile()) {
            throw new IllegalStateException("aapt2 is missing on '" + aapt2 + "'");
        }

        return aapt2;
    }

    @Override
    public void close() {
        // since we don't batch, we are done.
    }

    @Override
    @NonNull
    public File compileOutputFor(@NonNull CompileResourceRequest request) {
        return new File(
                request.getOutput(),
                Aapt2RenamingConventions.compilationRename(request.getInput()));
    }
}
