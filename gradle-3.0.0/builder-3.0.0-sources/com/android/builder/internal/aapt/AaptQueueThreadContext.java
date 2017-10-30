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
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.QueueThreadContext;
import com.android.builder.tasks.Task;
import com.android.utils.ILogger;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Class used for notification of AAPT queue events, creation, task running and destruction. */
public class AaptQueueThreadContext implements QueueThreadContext<AaptProcess> {
    @NonNull protected final Map<String, AaptProcess> aaptProcesses = new ConcurrentHashMap<>();
    @NonNull private final ILogger logger;
    @NonNull protected final String aaptLocation;
    @NonNull private final Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> outstandingJobs;
    @NonNull private final Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> doneJobs;

    public AaptQueueThreadContext(
            @NonNull ILogger logger,
            @NonNull String aaptLocation,
            @NonNull Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> outstandingJobs,
            @NonNull Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> doneJobs) {
        this.logger = logger;
        this.aaptLocation = aaptLocation;
        this.outstandingJobs = outstandingJobs;
        this.doneJobs = doneJobs;
    }

    @Override
    public boolean creation(@NonNull Thread t) throws IOException, InterruptedException {
        try {
            AaptProcess aaptProcess = new AaptProcess.Builder(aaptLocation, logger).start();
            boolean ready = aaptProcess.waitForReadyOrFail();
            if (ready) {
                aaptProcesses.put(t.getName(), aaptProcess);
            }
            return ready;
        } catch (InterruptedException e) {
            logger.error(e, "Cannot start slave process");
            throw e;
        }
    }

    @Override
    public void runTask(@NonNull Job<AaptProcess> job) throws Exception {
        job.runTask(new JobContext<>(aaptProcesses.get(Thread.currentThread().getName())));
        outstandingJobs.get(((QueuedJob) job).key).remove(job);

        synchronized (doneJobs) {
            ConcurrentLinkedQueue<Job<AaptProcess>> jobs =
                    doneJobs.computeIfAbsent(
                            ((QueuedJob) job).key, k -> new ConcurrentLinkedQueue<>());

            jobs.add(job);
        }
    }

    @Override
    public void destruction(@NonNull Thread t) throws IOException, InterruptedException {
        AaptProcess aaptProcess = aaptProcesses.get(Thread.currentThread().getName());
        if (aaptProcess == null) {
            return;
        }
        try {
            aaptProcess.shutdown();
        } finally {
            aaptProcesses.remove(t.getName());
        }
        logger.verbose(
                "Thread(%1$s): Process(%2$d), after shutdown queue_size=%3$d",
                Thread.currentThread().getName(), aaptProcess.hashCode(), aaptProcesses.size());

    }

    @Override
    public void shutdown() {
        if (aaptProcesses.isEmpty()) {
            return;
        }

        logger.warning("Process list not empty");
        // Go through all left aapt processes and close them.
        for (Map.Entry<String, AaptProcess> aaptProcessEntry : aaptProcesses.entrySet()) {
            logger.warning("Thread(%1$s): queue not cleaned", aaptProcessEntry.getKey());
            try {
                aaptProcessEntry.getValue().shutdown();
            } catch (Exception e) {
                logger.error(e, "while shutting down" + aaptProcessEntry.getKey());
            }
        }
        // Clean the map at the end.
        aaptProcesses.clear();
    }

    public static final class QueuedJob extends Job<AaptProcess> {

        protected final int key;

        public QueuedJob(
                int key,
                String jobTile,
                Task<AaptProcess> task,
                ListenableFuture<File> resultFuture) {
            super(jobTile, task, resultFuture);
            this.key = key;
        }
    }
}
