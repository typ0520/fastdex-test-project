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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.testing.ShardedTestCallable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessExecutor;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** A {@link TestRunner} that uses all connected devices to execute tests in parallel. */
public class ShardedTestRunner extends BaseTestRunner {

    @Nullable private final Integer numShards;

    public ShardedTestRunner(
            @Nullable File splitSelectExec,
            @NonNull ProcessExecutor processExecutor,
            @Nullable Integer numShards) {
        super(splitSelectExec, processExecutor);
        this.numShards = numShards;
    }

    @Override
    @NonNull
    protected WaitableExecutor scheduleTests(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull TestData testData,
            @NonNull Map<DeviceConnector, ImmutableList<File>> apksForDevice,
            @NonNull Set<File> helperApks,
            int timeoutInMs,
            @NonNull Collection<String> installOptions,
            @NonNull File resultsDir,
            @NonNull File coverageDir,
            @NonNull ILogger logger) {
        WaitableExecutor executor =
                WaitableExecutor.useNewFixedSizeThreadPool(apksForDevice.keySet().size());

        int numShards;
        if (this.numShards == null) {
            numShards = apksForDevice.size();
        } else {
            numShards = this.numShards;
        }

        AtomicInteger currentShard = new AtomicInteger(-1);
        ShardedTestCallable.ProgressListener progressListener =
                new ShardedTestCallable.ProgressListener(numShards, logger);
        ShardedTestCallable.ShardProvider shardProvider =
                new ShardedTestCallable.ShardProvider() {
                    @Nullable
                    @Override
                    public Integer getNextShard() {
                        int shard = currentShard.incrementAndGet();
                        return shard < numShards ? shard : null;
                    }

                    @Override
                    public int getTotalShards() {
                        return numShards;
                    }
                };
        logger.info("will shard tests into %d shards", numShards);
        for (Map.Entry<DeviceConnector, ImmutableList<File>> runners : apksForDevice.entrySet()) {
            ShardedTestCallable shardedTestCallable =
                    new ShardedTestCallable(
                            runners.getKey(),
                            projectName,
                            variantName,
                            runners.getValue(),
                            testData,
                            resultsDir,
                            coverageDir,
                            timeoutInMs,
                            logger,
                            shardProvider);
            shardedTestCallable.setProgressListener(progressListener);
            executor.execute(shardedTestCallable);
        }
        return executor;
    }
}
