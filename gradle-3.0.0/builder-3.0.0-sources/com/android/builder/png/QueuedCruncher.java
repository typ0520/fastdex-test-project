/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.aapt.AaptQueueThreadContext;
import com.android.builder.internal.aapt.QueuedResourceProcessor;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.Task;
import com.android.ide.common.internal.ResourceCompilationException;
import com.android.ide.common.internal.ResourceProcessor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implementation of {@link ResourceProcessor} that queues request and uses a pool of aapt server
 * processes to serve those. Should only be used to process 9 patch images and, if PNG crunching is
 * enabled, to crunch PNG files.
 */
public class QueuedCruncher extends QueuedResourceProcessor {

    // use an enum to ensure singleton.
    private enum Creator {
        INSTANCE;

        @NonNull private final Map<String, QueuedCruncher> sInstances = new ConcurrentHashMap<>();
        @NonNull private final Object sLock = new Object();

        /**
         * Creates a new {@link com.android.builder.png.QueuedCruncher} or return an existing one
         * based on the underlying AAPT executable location.
         *
         * @param aaptLocation the AAPT executable location.
         * @param logger the logger to use
         * @param cruncherProcesses number of cruncher processes to use; {@code 0} to use the
         *     default number
         * @return a new of existing instance of the {@link com.android.builder.png.QueuedCruncher}
         */
        @NonNull
        public QueuedCruncher newCruncher(
                @NonNull String aaptLocation, @NonNull ILogger logger, int cruncherProcesses) {
            synchronized (sLock) {
                logger.verbose("QueuedCruncher is using %1$s%n", aaptLocation);
                if (!sInstances.containsKey(aaptLocation)) {
                    QueuedCruncher queuedCruncher =
                            new QueuedCruncher(aaptLocation, logger, cruncherProcesses);
                    sInstances.put(aaptLocation, queuedCruncher);
                }
                return sInstances.get(aaptLocation);
            }
        }
    }

    private QueuedCruncher(
            @NonNull final String aaptLocation, @NonNull ILogger iLogger, int cruncherProcesses) {
        super(aaptLocation, iLogger, cruncherProcesses);
    }

    @Override
    public ListenableFuture<File> compile(
            int key,
            @NonNull final CompileResourceRequest request,
            @Nullable ProcessOutputHandler processOutputHandler)
            throws ResourceCompilationException {

        final File outputFile = compileOutputFor(request);

        SettableFuture<File> result = SettableFuture.create();
        try {
            final Job<AaptProcess> aaptProcessJob =
                    new AaptQueueThreadContext.QueuedJob(
                            key,
                            "Crunching " + request.getInput().getName(),
                            new Task<AaptProcess>() {
                                @Override
                                public void run(
                                        @NonNull Job<AaptProcess> job,
                                        @NonNull JobContext<AaptProcess> context)
                                        throws IOException {
                                    AaptProcess aapt = context.getPayload();
                                    if (aapt == null) {
                                        logger.error(
                                                null,
                                                "Thread(%1$s) has a null payload",
                                                Thread.currentThread().getName());
                                        return;
                                    }
                                    aapt.crunch(request.getInput(), outputFile, job);
                                }

                                @Override
                                public void finished() {
                                    result.set(outputFile);
                                }

                                @Override
                                public void error(Throwable e) {
                                    result.setException(e);
                                }

                                @Override
                                public String toString() {
                                    return MoreObjects.toStringHelper(this)
                                            .add("from", request.getInput().getAbsolutePath())
                                            .add("to", outputFile.getAbsolutePath())
                                            .toString();
                                }
                            },
                            result);

            synchronized (outstandingJobs) {
                outstandingJobs.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
            }
            processingRequests.push(aaptProcessJob);
        } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            throw new ResourceCompilationException(e);
        }
        return result;
    }

    @NonNull
    private static File compileOutputFor(@NonNull CompileResourceRequest request) {
        // AAPT1 requires explicitly passing the output file instead of an output directory. If we
        // were passed a directory instead of a file, calculate the output.
        if (request.getOutput().isDirectory()) {
            File parentDir = new File(request.getOutput(), request.getFolderName());
            FileUtils.mkdirs(parentDir);
            return new File(parentDir, request.getInput().getName());
        }
        return request.getOutput();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String aaptLocation;
        private ILogger logger;
        // Passing 0 means using the default number of processes.
        private int processesNumber = 0;

        public Builder executablePath(@NonNull String aaptLocation) {
            this.aaptLocation = aaptLocation;
            return this;
        }

        public Builder logger(@NonNull ILogger logger) {
            this.logger = logger;
            return this;
        }

        public Builder numberOfProcesses(int processesNumber) {
            this.processesNumber = processesNumber;
            return this;
        }

        public QueuedCruncher build() {
            return QueuedCruncher.Creator.INSTANCE.newCruncher(
                    aaptLocation, logger, processesNumber);
        }
    }
}
