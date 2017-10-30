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
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptQueueThreadContext;
import com.android.builder.internal.aapt.QueuedResourceProcessor;
import com.android.builder.png.AaptProcess;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.Task;
import com.android.ide.common.internal.ResourceCompilationException;
import com.android.ide.common.internal.ResourceProcessor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.tools.aapt2.Aapt2RenamingConventions;
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
 * Implementation of {@link ResourceProcessor} that queues request and uses a pool of aapt2 server
 * processes to serve those.
 */
public class Aapt2QueuedResourceProcessor extends QueuedResourceProcessor {

    // use an enum to ensure singleton.
    private enum Creator {
        INSTANCE;

        private final Map<String, Aapt2QueuedResourceProcessor> sInstances =
                new ConcurrentHashMap<>();
        private final Object sLock = new Object();

        /**
         * Creates a new {@link Aapt2QueuedResourceProcessor} or returns an existing one based on
         * the underlying AAPT executable location.
         *
         * @param aaptLocation the AAPT2 executable location.
         * @param logger the logger to use
         * @param processesNumber number of processes to use; {@code 0} to use the default number
         * @return a new of existing instance of the {@link Aapt2QueuedResourceProcessor}
         */
        @NonNull
        public Aapt2QueuedResourceProcessor newProcessor(
                @NonNull String aaptLocation, @NonNull ILogger logger, int processesNumber) {
            synchronized (sLock) {
                logger.verbose("Aapt2QueuedResourceProcessor is using %1$s%n", aaptLocation);

                if (!sInstances.containsKey(aaptLocation)) {
                    Aapt2QueuedResourceProcessor processor =
                            new Aapt2QueuedResourceProcessor(aaptLocation, logger, processesNumber);

                    sInstances.put(aaptLocation, processor);
                }
                return sInstances.get(aaptLocation);
            }
        }

        public void invalidateProcessor(@NonNull String aaptLocation) {
            sInstances.remove(aaptLocation);
        }
    }

    private Aapt2QueuedResourceProcessor(
            @NonNull String aaptLocation, @NonNull ILogger iLogger, int processesNumber) {
        super(aaptLocation, iLogger, processesNumber);
    }

    @Override
    public ListenableFuture<File> compile(
            int key,
            @NonNull CompileResourceRequest request,
            @Nullable ProcessOutputHandler processOutputHandler)
            throws ResourceCompilationException {

        SettableFuture<File> result = SettableFuture.create();

        try {
            final Job<AaptProcess> aaptProcessJob =
                    new AaptQueueThreadContext.QueuedJob(
                            key,
                            "Compiling " + request.getInput().getName(),
                            new Task<AaptProcess>() {
                                @Override
                                public void run(
                                        @NonNull Job<AaptProcess> job,
                                        @NonNull JobContext<AaptProcess> context)
                                        throws IOException {
                                    AaptProcess aapt = context.getPayload();
                                    if (aapt == null) {
                                        logger.error(
                                                null /* throwable */,
                                                "Thread(%1$s) has a null payload",
                                                Thread.currentThread().getName());
                                        return;
                                    }
                                    aapt.compile(request, job, processOutputHandler);
                                }

                                @Override
                                public void finished() {
                                    result.set(
                                            new File(
                                                    request.getOutput(),
                                                    Aapt2RenamingConventions.compilationRename(
                                                            request.getInput())));
                                }

                                @Override
                                public void error(Throwable e) {
                                    result.setException(e);
                                }

                                @Override
                                public String toString() {
                                    return MoreObjects.toStringHelper(this)
                                            .add("from", request.getInput().getAbsolutePath())
                                            .add("to", request.getOutput().getAbsolutePath())
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

    public ListenableFuture<File> link(
            int key,
            @NonNull AaptPackageConfig config,
            @NonNull File intermediateDir,
            @Nullable ProcessOutputHandler processOutputHandler)
            throws ResourceCompilationException {
        SettableFuture<File> result = SettableFuture.create();

        try {
            final Job<AaptProcess> aaptProcessJob =
                    new AaptQueueThreadContext.QueuedJob(
                            key,
                            "Linking",
                            new Task<AaptProcess>() {
                                @Override
                                public void run(
                                        @NonNull Job<AaptProcess> job,
                                        @NonNull JobContext<AaptProcess> context)
                                        throws IOException {
                                    AaptProcess aapt = context.getPayload();
                                    if (aapt == null) {
                                        logger.error(
                                                null /* throwable */,
                                                "Thread(%1$s) has a null payload",
                                                Thread.currentThread().getName());
                                        return;
                                    }
                                    aapt.link(config, intermediateDir, job, processOutputHandler);
                                }

                                @Override
                                public void finished() {
                                    result.set(null);
                                }

                                @Override
                                public void error(Throwable e) {
                                    result.setException(e);
                                }

                                @Override
                                public String toString() {
                                    return MoreObjects.toStringHelper(this)
                                            .add("config", config.toString())
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

    public static void invalidateProcess(@NonNull String aaptLocation) {
        Creator.INSTANCE.invalidateProcessor(aaptLocation);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String aaptLocation;
        private ILogger logger;
        // Passing 0 means using the default number of processes.
        private int processesNumber = 0;

        public Builder executablePath(String aaptLocation) {
            this.aaptLocation = aaptLocation;
            return this;
        }

        public Builder logger(ILogger logger) {
            this.logger = logger;
            return this;
        }

        public Builder numberOfProcesses(int processesNumber) {
            this.processesNumber = processesNumber;
            return this;
        }

        public Aapt2QueuedResourceProcessor build() {
            return Aapt2QueuedResourceProcessor.Creator.INSTANCE.newProcessor(
                    aaptLocation, logger, processesNumber);
        }
    }
}
