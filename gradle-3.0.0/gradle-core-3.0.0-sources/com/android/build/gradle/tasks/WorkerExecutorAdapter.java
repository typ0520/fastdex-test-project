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

package com.android.build.gradle.tasks;

import com.android.ide.common.workers.WorkerExecutorFacade;
import java.io.Serializable;
import org.gradle.workers.WorkerExecutor;

/**
 * Simple implementation of {@link WorkerExecutorFacade} that uses a Gradle {@link WorkerExecutor}
 * to submit new work actions.
 *
 * @param <T> is the parameter type encapsulating all necessary information to run the work action.
 */
public class WorkerExecutorAdapter<T extends Serializable> implements WorkerExecutorFacade<T> {

    private final WorkerExecutor workerExecutor;
    private final Class<? extends Runnable> workActionClass;

    /**
     * Creates a new adapter using a {@link WorkerExecutor} instance and a work action {@link Class}
     * that should be instantiated for each work submission.
     *
     * @param workerExecutor instance of WorkerExecutor.
     * @param workActionClass action type.
     */
    public WorkerExecutorAdapter(
            WorkerExecutor workerExecutor, Class<? extends Runnable> workActionClass) {
        this.workerExecutor = workerExecutor;
        this.workActionClass = workActionClass;
    }

    @Override
    public void submit(T parameter) {
        workerExecutor.submit(
                workActionClass, new NoIsolationModeConfigurator<>(parameter)::configure);
    }

    @Override
    public void await() {
        workerExecutor.await();
    }
}
