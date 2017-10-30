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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AbstractAapt;
import com.android.ide.common.internal.ResourceCompilationException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.sdklib.BuildToolInfo;
import com.android.tools.aapt2.Aapt2Exception;
import com.android.tools.aapt2.Aapt2RenamingConventions;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link com.android.builder.internal.aapt.Aapt} that uses out-of-process
 * execution of {@code aapt2}. It queues request and uses a pool of AAPT2 server/daemon processes to
 * serve them.
 */
public class QueueableAapt2 extends AbstractAapt {

    private static final long AUTO_THREAD_SHUTDOWN_MS = 250;

    @NonNull private final Aapt2QueuedResourceProcessor aapt;
    @NonNull private final Executor executor;
    @NonNull private final File intermediateDir;
    @NonNull private final Integer requestKey;
    @Nullable private final ProcessOutputHandler processOutputHandler;

    /**
     * Creates a new entry point to the original {@code aapt2}.
     *
     * @param processOutputHandler the handler to process the executed process' output
     * @param buildToolInfo the build tools to use
     * @param intermediateDir directory where to store intermediate files
     * @param logger logger to use
     */
    public QueueableAapt2(
            @Nullable ProcessOutputHandler processOutputHandler,
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull File intermediateDir,
            @NonNull ILogger logger,
            int numberOfProcesses) {
        this(
                processOutputHandler,
                getAapt2ExecutablePath(buildToolInfo),
                intermediateDir,
                logger,
                numberOfProcesses);
    }

    @VisibleForTesting
    QueueableAapt2(
            @Nullable ProcessOutputHandler processOutputHandler,
            @NonNull String aapt2ExecutablePath,
            @NonNull File intermediateDir,
            @NonNull ILogger logger,
            int numberOfProcesses) {
        Preconditions.checkArgument(
                intermediateDir.isDirectory(),
                "Intermediate directory needs to be a directory.\nintermediateDir: %s",
                intermediateDir.getAbsolutePath());

        this.intermediateDir = intermediateDir;
        this.processOutputHandler = processOutputHandler;

        this.executor =
                new ThreadPoolExecutor(
                        0, // Core threads
                        1, // Maximum threads
                        AUTO_THREAD_SHUTDOWN_MS,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>());

        this.aapt =
                Aapt2QueuedResourceProcessor.builder()
                        .executablePath(aapt2ExecutablePath)
                        .logger(logger)
                        .numberOfProcesses(numberOfProcesses)
                        .build();

        requestKey = aapt.start();
    }

    @Override
    public void close() throws IOException {
        try {
            aapt.end(requestKey);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    @Override
    public Future<File> compile(@NonNull CompileResourceRequest request) throws Exception {
        // TODO(imorlowska): move verification to CompileResourceRequest.
        Preconditions.checkArgument(
                request.getInput().isFile(),
                "Input file needs to be a normal file.\nInput file: %s",
                request.getInput().getAbsolutePath());
        Preconditions.checkArgument(
                request.getOutput().isDirectory(),
                "Output for resource compilation needs to be a directory.\nOutput: %s",
                request.getOutput().getAbsolutePath());

        SettableFuture<File> actualResult = SettableFuture.create();
        ListenableFuture<File> futureResult;

        try {
            futureResult = aapt.compile(requestKey, request, processOutputHandler);
        } catch (ResourceCompilationException e) {
            throw new Aapt2Exception(
                    String.format("Failed to compile file %s", request.getInput()), e);
        }

        futureResult.addListener(
                () -> {
                    try {
                        actualResult.set(futureResult.get());
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                        actualResult.setException(e);
                    } catch (ExecutionException e) {
                        actualResult.setException(e);
                    }
                },
                executor);

        return actualResult;
    }

    @NonNull
    @Override
    protected ListenableFuture<Void> makeValidatedPackage(@NonNull AaptPackageConfig config)
            throws AaptException {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        ListenableFuture<File> futureResult;

        try {
            futureResult = aapt.link(requestKey, config, intermediateDir, processOutputHandler);
        } catch (Exception e) {
            throw new AaptException("Failed to link", e);
        }

        futureResult.addListener(
                () -> {
                    try {
                        // Just wait for the job to finish, result is Void
                        futureResult.get();
                        actualResult.set(null);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                        actualResult.setException(e);
                    } catch (ExecutionException e) {
                        actualResult.setException(e);
                    }
                },
                executor);

        return actualResult;
    }

    @Override
    @NonNull
    public File compileOutputFor(@NonNull CompileResourceRequest request) {
        return new File(
                request.getOutput(),
                Aapt2RenamingConventions.compilationRename(request.getInput()));
    }

    private static String getAapt2ExecutablePath(BuildToolInfo buildToolInfo) {
        Preconditions.checkArgument(
                BuildToolInfo.PathId.DAEMON_AAPT2.isPresentIn(buildToolInfo.getRevision()),
                "Aapt2 with daemon mode requires newer build tools.\n"
                        + "Current version %s, minimum required %s.",
                buildToolInfo.getRevision(),
                BuildToolInfo.PathId.DAEMON_AAPT2.getMinRevision());
        String aapt2 = buildToolInfo.getPath(BuildToolInfo.PathId.DAEMON_AAPT2);
        if (aapt2 == null || !new File(aapt2).isFile()) {
            throw new IllegalStateException("aapt2 is missing on '" + aapt2 + "'");
        }
        return aapt2;
    }
}
