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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.TaskFactory;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Task;

/**
 * Handle for a {@link Task} that may not yet been created.
 * For tasks created using {@code ModelMap<Task>}, they are usually not actually created until
 * Gradle decides those tasks needs to be executed.  This class contains information about those
 * tasks and allow dependencies to be specified.
 */
public class AndroidTask<T extends Task> {
    @NonNull
    private String name;
    @NonNull
    private final Class<T> taskType;

    public AndroidTask(@NonNull String name, @NonNull Class<T> taskType) {
        this.name = name;
        this.taskType = taskType;
    }

    /**
     * The name of Task this represents.
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * The type of Task this represents.
     */
    @NonNull
    public Class<T> getTaskType() {
        return taskType;
    }

    /**
     * Add dependency on another AndroidTask.
     * @param taskFactory TaskFactory used to configure the task for dependencies.
     * @param other The task that this depends on.
     */
    public void dependsOn(final TaskFactory taskFactory, final AndroidTask<?> other) {
        taskFactory.named(name, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(other.name);
            }
        });
    }

    /**
     * Add dependency on objects.
     * This method adds dependencies on any objects accepted by {@link Task#dependsOn} and is
     * needed for compatibility until all tasks are trasitioned to AndroidTask.
     * @param taskFactory TaskFactory used to configure the task for dependencies.
     * @param dependency Object accepted by {@link Task#dependsOn}.
     */
    public void dependsOn(final TaskFactory taskFactory, final Object dependency) {
        Preconditions.checkNotNull(dependency);
        taskFactory.named(name, new Action<Task>() {
            @Override
            public void execute(Task task) {
                if (dependency instanceof AndroidTask) {
                    task.dependsOn(((AndroidTask) dependency).getName());
                } else {
                    task.dependsOn(dependency);
                }
            }
        });
    }

    /**
     * Add dependency on objects.
     * This method adds dependencies on any objects accepted by {@link Task#dependsOn} and is
     * needed for compatibility until all tasks are trasitioned to AndroidTask.
     * @param taskFactory TaskFactory used to configure the task for dependencies.
     * @param dependency1 Object accepted by {@link Task#dependsOn}.
     * @param dependency2 Object accepted by {@link Task#dependsOn}.
     */
    public void dependsOn(
            final TaskFactory taskFactory,
            final Object dependency1,
            final Object dependency2) {
        Preconditions.checkNotNull(dependency1);
        Preconditions.checkNotNull(dependency2);

        taskFactory.named(name, new Action<Task>() {
            @Override
            public void execute(Task task) {
                if (dependency1 instanceof AndroidTask) {
                    task.dependsOn(((AndroidTask) dependency1).getName());
                } else {
                    task.dependsOn(dependency1);
                }

                if (dependency2 instanceof AndroidTask) {
                    task.dependsOn(((AndroidTask) dependency2).getName());
                } else {
                    task.dependsOn(dependency2);
                }

            }
        });
    }

    /**
     * Add dependency on objects.
     * This method adds dependencies on any objects accepted by {@link Task#dependsOn} and is
     * needed for compatibility until all tasks are trasitioned to AndroidTask.
     * @param taskFactory TaskFactory used to configure the task for dependencies.
     * @param dependencies Objects accepted by {@link Task#dependsOn}.
     */
    public void dependsOn(final TaskFactory taskFactory, final Object... dependencies) {
        for (Object dependency : dependencies) {
            Preconditions.checkNotNull(dependency);
        }
        taskFactory.named(name, new Action<Task>() {
            @Override
            public void execute(Task task) {
                for (Object dependency : dependencies) {
                    if (dependency instanceof AndroidTask) {
                        task.dependsOn(((AndroidTask) dependency).getName());
                    } else {
                        task.dependsOn(dependency);
                    }
                }
            }
        });
    }

    /**
     * Add dependency on other objects if the object is not null.
     * This method adds dependencies on any objects accepted by {@link Task#dependsOn} and is
     * needed for compatibility until all tasks are trasitioned to AndroidTask.
     * @param taskFactory TaskFactory used to configure the task for dependencies.
     * @param dependencies Objects accepted by {@link Task#dependsOn}.
     */
    public void optionalDependsOn(final TaskFactory taskFactory, final Object... dependencies) {
        for (Object dependency : dependencies) {
            if (dependency != null) {
                if (dependency instanceof AndroidTask) {
                    dependsOn(taskFactory, ((AndroidTask) dependency).getName());
                } else {
                    dependsOn(taskFactory, dependency);
                }
            }
        }
    }


    public void optionalDependsOn(final TaskFactory taskFactory, @NonNull List<?> dependencies) {
        for (Object dependency : dependencies) {
            if (dependency != null) {
                dependsOn(taskFactory, dependency);
            }
        }
    }

    /**
     * Add a configuration action for this task.
     * @param taskFactory TaskFactory used to configure the task.
     * @param configAction An Action to be executed.
     */
    public void configure(TaskFactory taskFactory, Action<? super T> configAction) {
        //noinspection unchecked
        taskFactory.named(name, (Action<? super Task>) configAction);
    }

    /**
     * Potentially instantiates and return the task. Should only be called once the task is
     * configured.
     * @param taskFactory the factory for tasks
     * @return the task instance.
     */
    @SuppressWarnings("unchecked")
    public T get(TaskFactory taskFactory) {
        return (T) taskFactory.named(name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("taskType", taskType)
                .toString();
    }
}
