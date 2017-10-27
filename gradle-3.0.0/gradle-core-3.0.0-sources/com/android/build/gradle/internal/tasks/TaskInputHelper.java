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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A wrapper around a file supplier that allows to recall the last value queried from it.
 *
 * It also allows bypassing the supplier during task graph creation (TODO)
 */
public class TaskInputHelper {

    private static AtomicBoolean bypassSupplier = new AtomicBoolean(true);

    public static void disableBypass() {
        bypassSupplier.set(false);
    }

    public static void enableBypass() {
        bypassSupplier.set(true);
    }

    /**
     * Returns a new supplier wrapping the provided one that bypass the get() method until
     * the task graph is resolved.
     *
     * The bypass feature is meant to avoid computing the supplier too early.
     * Gradle will call any annotated getter returning File or Collection&lt;File&gt; during task
     * graph creation. This is because File instances could implement
     * {@link org.gradle.api.Buildable} which contains task dependency information.
     * The bypass allows to return an empty list during task graph creation and to only run the
     * supplier during up-to-date checks when we know the task is going to run. This only works
     * if the the Files do not implement Buildable which is most of the time.
     *
     * The new supplier will also memoize the content, allowing the original supplier to only
     * compute its content once (usually during up-to-date checks), while other uses (in the task
     * action itself) can access the cache.
     *
     * For use inside <code>project.files()</code>, see {@link #bypassFileCallable(Supplier)}.
     *
     * @param supplier the supplier to wrap.
     * @return a new supplier.
     */
    public static Supplier<Collection<File>> bypassFileSupplier(
            @NonNull Supplier<Collection<File>> supplier) {
        return new BypassFileSupplier(supplier);
    }

    /**
     * Returns a new callable wrapping the provided one that bypass the get() method until
     * the task graph is resolved.
     *
     * The bypass feature is meant to avoid computing the supplier too early.
     * Gradle will call any annotated getter returning File or Collection&lt;File&gt; during task
     * graph creation. This is because File instances could implement
     * {@link org.gradle.api.Buildable} which contains task dependency information.
     * The bypass allows to return an empty list during task graph creation and to only run the
     * supplier during up-to-date checks when we know the task is going to run. This only works
     * if the the Files do not implement Buildable which is most of the time.
     *
     * The new Callable will also memoize the content, allowing the original supplier to only
     * compute its content once (usually during up-to-date checks), while other uses (in the task
     * action itself) can access the cache.
     *
     * This should mainly be used to pass callables to <code>project.files()</code>,
     * since internally the implementation checks for instanceof Callable and a supplier won't
     * work in that case.
     *
     * @param supplier the supplier to wrap.
     * @return a new supplier.
     */
    public static Callable<Collection<File>> bypassFileCallable(
            @NonNull Supplier<Collection<File>> supplier) {
        return new BypassFileCallable(supplier);
    }

    /**
     * Returns a new supplier wrapping the provided one that cache the result of the supplier to
     * only run it once.
     *
     * <p>Supplier returning a collection of File should use {@link #bypassFileSupplier(Supplier)}.
     *
     * @param supplier the supplier to wrap.
     * @param <T> the return type for the supplier.
     * @return a new supplier.
     */
    @NonNull
    public static <T> Supplier<T> memoize(@NonNull Supplier<T> supplier) {
        return new MemoizedSupplier<>(supplier);
    }

    private static class BypassFileSupplier extends MemoizedSupplier<Collection<File>> {

        @Override
        public Collection<File> get() {
            if (TaskInputHelper.bypassSupplier.get()) {
                return ImmutableList.of();
            }

            return super.get();
        }

        private BypassFileSupplier(@NonNull Supplier<Collection<File>> supplier) {
            super(supplier);
        }
    }

    private static class MemoizedSupplier<T> implements Supplier<T> {

        @NonNull
        private final Supplier<T> supplier;
        private T lastValue;

        @Override
        public T get() {
            if (lastValue == null) {
                lastValue = supplier.get();
            }
            return lastValue;
        }

        private MemoizedSupplier(@NonNull Supplier<T> supplier) {
            this.supplier = supplier;
        }
    }

    private static class BypassFileCallable extends BypassFileSupplier
            implements Callable<Collection<File>> {

        private BypassFileCallable(@NonNull Supplier<Collection<File>> supplier) {
            super(supplier);
        }

        @Override
        public Collection<File> call() throws Exception {
            return get();
        }
    }
}