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

import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;

/**
 * Simple object configuring a work item submitted to the Gradle's {@link WorkerExecutor}.
 *
 * @param <T> is the parameter type encapsulating all data to configure a single task.
 */
public class NoIsolationModeConfigurator<T> {

    private final T parameters;

    public NoIsolationModeConfigurator(T parameters) {
        this.parameters = parameters;
    }

    /**
     * Configures the action to be run in non isolated mode with the unique parameter encapsulating
     * all necessary data to run the work action.
     *
     * @param configuration Gradle's WorkAction configuration object.
     */
    public void configure(WorkerConfiguration configuration) {
        configuration.setIsolationMode(IsolationMode.NONE);
        configuration.setParams(parameters);
    }
}
