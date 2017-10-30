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

package com.android.builder.internal.aapt;

import com.android.annotations.NonNull;
import com.android.builder.png.AaptProcess;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.QueueThreadContext;
import com.android.builder.tasks.WorkQueue;
import com.android.ide.common.internal.ResourceProcessor;
import com.android.utils.ILogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@link ResourceProcessor} that queues request and uses a pool of aapt server
 * processes to serve those.
 */
public abstract class QueuedResourceProcessor implements ResourceProcessor {
    /** Maximum number of concurrent processes to launch. */
    private static final int MAX_DEFAULT_NUMBER_DAEMON_PROCESSES = 8;
    /** Number of concurrent processes to launch. */
    protected static final int DEFAULT_NUMBER_DAEMON_PROCESSES =
            Integer.min(
                    MAX_DEFAULT_NUMBER_DAEMON_PROCESSES,
                    Runtime.getRuntime().availableProcessors());

    @NonNull protected final String aaptLocation;
    @NonNull protected final ILogger logger;
    // Queue responsible for handling all passed jobs with a pool of worker threads.
    @NonNull protected final WorkQueue<AaptProcess> processingRequests;
    // list of outstanding jobs.
    @NonNull
    protected final Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> outstandingJobs =
            new ConcurrentHashMap<>();
    // list of finished jobs.
    @NonNull
    protected final Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> doneJobs =
            new ConcurrentHashMap<>();
    // ref count of active users, if it drops to zero, that means there are no more active users
    // and the queue should be shutdown.
    @NonNull protected final AtomicInteger refCount = new AtomicInteger(0);

    // per process unique key provider to remember which users enlisted which requests.
    @NonNull protected final AtomicInteger keyProvider = new AtomicInteger(0);

    protected QueuedResourceProcessor(
            @NonNull final String aaptLocation, @NonNull ILogger iLogger, int processesNumber) {
        this.aaptLocation = aaptLocation;
        this.logger = iLogger;

        QueueThreadContext<AaptProcess> queueThreadContext =
                new AaptQueueThreadContext(logger, aaptLocation, outstandingJobs, doneJobs);

        int processToUse;
        if (processesNumber > 0) {
            processToUse = processesNumber;
        } else {
            processToUse = DEFAULT_NUMBER_DAEMON_PROCESSES;
        }

        processingRequests =
                new WorkQueue<>(
                        logger, queueThreadContext, "queued-resource-processor", processToUse, 0);
    }

    protected void waitForAll(int key) throws InterruptedException {
        Job<AaptProcess> aaptProcessJob;
        boolean hasExceptions = false;

        // Some jobs could be still waiting to start. Wait for them to finish and check for any
        // issues.
        while ((aaptProcessJob = outstandingJobs.get(key).poll()) != null) {
            logger.verbose(
                    "Thread(%1$s) : wait for {%2$s)",
                    Thread.currentThread().getName(), aaptProcessJob.toString());
            try {
                aaptProcessJob.awaitRethrowExceptions();
            } catch (ExecutionException e) {
                logger.verbose(
                        "Exception while processing job : "
                                + aaptProcessJob.toString()
                                + " : "
                                + e.getCause());
                hasExceptions = true;
            }
        }

        // Some jobs could have started before this method was called, so wait for them to finish
        // (some are probably already done) and check for any issues.
        while ((aaptProcessJob = doneJobs.get(key).poll()) != null) {
            try {
                aaptProcessJob.awaitRethrowExceptions();
            } catch (ExecutionException e) {
                logger.verbose(
                        "Exception while processing job : "
                                + aaptProcessJob.toString()
                                + " : "
                                + e.getCause());
                hasExceptions = true;
            }
        }
        if (hasExceptions) {
            throw new RuntimeException("Some file processing failed, see logs for details");
        }
    }

    @Override
    public synchronized int start() {
        // increment our reference count.
        refCount.incrementAndGet();

        // get a unique key for the lifetime of this process.
        int key = keyProvider.incrementAndGet();
        outstandingJobs.put(key, new ConcurrentLinkedQueue<>());
        doneJobs.put(key, new ConcurrentLinkedQueue<>());
        return key;
    }

    @Override
    public synchronized void end(int key) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            waitForAll(key);
            outstandingJobs.get(key).clear();
        } finally {
            // even if we have failures, we need to shutdown property the sub processes.
            if (refCount.decrementAndGet() == 0) {
                try {
                    processingRequests.shutdown();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    logger.warning(
                            "Error while shutting down queued resource proecssor queue : %s",
                            e.getMessage());
                }
                logger.verbose(
                        "Shutdown finished in %1$dms", System.currentTimeMillis() - startTime);
            }
        }
    }
}
