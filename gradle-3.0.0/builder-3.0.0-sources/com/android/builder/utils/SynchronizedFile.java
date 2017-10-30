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

package com.android.builder.utils;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import com.android.ide.common.util.ReadWriteProcessLock;
import com.android.ide.common.util.ReadWriteThreadLock;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutionException;

/**
 * A file whose access from multiple threads or processes is synchronized.
 *
 * <p>When multiple threads or processes access the same file, they would require some form of
 * synchronization. This class provides a simple way for the clients to add synchronization
 * capability to a file without having to work with low-level details involving single-process or
 * multi-process locking.
 *
 * <p>Synchronization can take effect for threads within the same process or across different
 * processes. The client can configure this locking scope when constructing a {@code
 * SynchronizedFile}. If the file is never accessed by more than one process at a time, the client
 * should configure the file with {@code SINGLE_PROCESS} locking scope since there will be less
 * synchronization overhead. However, if the file may be accessed by more than one process at a
 * time, the client must configure the file with {@code MULTI_PROCESS} locking scope.
 *
 * <p>In any case, synchronization takes effect only for the same file (i.e., threads/processes
 * accessing different files are not synchronized). Also, the client must access the file via {@code
 * SynchronizedFile}'s API; otherwise, the previous concurrency guarantees will not hold.
 *
 * <p>Two files are considered the same if they refer to the same physical file. There could be
 * multiple instances of {@code SynchronizedFile} for the same physical file, and as long as they
 * refer to the same physical file, access to them will be synchronized.
 *
 * <p>Internally, this class normalizes the file's path to detect same physical files via equals().
 * Therefore, the client does not need to normalize the file's path when constructing a {@code
 * SynchronizedFile}.
 *
 * <p>Once the {@code SynchronizedFile} is constructed, the client can read or write to the file as
 * follows.
 *
 * <pre>{@code
 * boolean fileExists = synchronizedFile.read(file -> file.exists());
 * boolean result = synchronizedFile.write(file -> { Files.touch(file); return true; });
 * }</pre>
 *
 * <p>Multiple threads/processes can read the same file at the same time. However, once a
 * thread/process starts writing to the file, the other threads/processes will block. This behavior
 * is similar to a {@link java.util.concurrent.locks.ReadWriteLock}.
 *
 * <p>Additionally, this class provides the {@link #createIfAbsent(ExceptionConsumer)} method for
 * the client to safely create the file if it does not yet exist.
 *
 * <p>Note that we often use the term "process", although the term "JVM" would be more correct since
 * there could exist multiple JVMs in a process.
 *
 * <p>This class is thread-safe.
 */
@Immutable
public final class SynchronizedFile {

    /** The scope of the locking facility. */
    @VisibleForTesting
    enum LockingScope {

        /**
         * Synchronization takes effect for threads both within the same process and across
         * different processes.
         */
        MULTI_PROCESS,

        /**
         * Synchronization takes effect for threads within the same process but not for threads
         * across different processes.
         */
        SINGLE_PROCESS
    }

    /** The type of the locking facility. */
    @VisibleForTesting
    enum LockingType {

        /** Shared lock used for reading. */
        SHARED,

        /** Exclusive lock used for writing/deleting. */
        EXCLUSIVE
    }

    @NonNull private static final String LOCK_FILE_EXTENSION = ".lock";

    @NonNull private final File fileToSynchronize;

    @NonNull private final LockingScope lockingScope;

    private SynchronizedFile(@NonNull File fileToSynchronize, @NonNull LockingScope lockingScope) {
        this.fileToSynchronize = fileToSynchronize;
        this.lockingScope = lockingScope;
    }

    /**
     * Returns a {@code SynchronizedFile} instance where synchronization takes effect for threads
     * both within the same process and across different processes.
     *
     * <p>Inter-process synchronization is provided via {@link ReadWriteProcessLock}, which requires
     * a lock file to be created. This lock file is different from the file being synchronized and
     * will be placed next to that file. Note that currently it is not possible for the underlying
     * locking mechanism to delete these lock files. The client may delete the lock files only when
     * the locking mechanism is no longer in use.
     *
     * <p>This method will normalize the file's path first, so the client does not need to normalize
     * the file's path in advance.
     *
     * <p>The file being synchronized may or may not already exist. However, in order for the lock
     * file to be created, the parent directory of the file being synchronized must exist when this
     * method is called.
     *
     * <p>Note: If the file is never accessed by more than one process at a time, the client should
     * use the {@link #getInstanceWithSingleProcessLocking(File)} method instead since there will be
     * less synchronization overhead.
     *
     * @param fileToSynchronize the file whose access will be synchronized; it may not yet exist,
     *     but its parent directory must exist
     * @see #getInstanceWithSingleProcessLocking(File)
     * @throws IllegalArgumentException if the parent directory of file being synchronized does not
     *     exist, or a regular file with the same path as the lock file accidentally exists next to
     *     the file being synchronized
     */
    @NonNull
    public static SynchronizedFile getInstanceWithMultiProcessLocking(
            @NonNull File fileToSynchronize) {
        // Normalize the file path first
        try {
            fileToSynchronize = fileToSynchronize.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Preconditions.checkArgument(
                FileUtils.parentDirExists(fileToSynchronize),
                "Parent directory of " + fileToSynchronize.getAbsolutePath() + " does not exist");

        File lockFile =
                new File(
                        fileToSynchronize.getParent(),
                        fileToSynchronize.getName() + LOCK_FILE_EXTENSION);
        Preconditions.checkArgument(
                !lockFile.exists() || (lockFile.isFile() && lockFile.length() == 0),
                "Unexpected lock file found: "
                        + lockFile.getAbsolutePath()
                        + " with length="
                        + lockFile.length());

        return new SynchronizedFile(fileToSynchronize, LockingScope.MULTI_PROCESS);
    }

    /**
     * Returns a {@code SynchronizedFile} instance where synchronization takes effect for threads
     * within the same process but not for threads across different processes.
     *
     * <p>This method will normalize the file's path first, so the client does not need to normalize
     * the file's path in advance.
     *
     * <p>Note: If the file may be accessed by more than one process at a time, the client must use
     * the {@link #getInstanceWithMultiProcessLocking(File)} method instead.
     *
     * @param fileToSynchronize the file whose access should be synchronized, which may not yet
     *     exist
     * @see #getInstanceWithMultiProcessLocking(File)
     */
    @NonNull
    public static SynchronizedFile getInstanceWithSingleProcessLocking(
            @NonNull File fileToSynchronize) {
        // Normalize the file path first
        try {
            fileToSynchronize = fileToSynchronize.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new SynchronizedFile(fileToSynchronize, LockingScope.SINGLE_PROCESS);
    }

    /**
     * Executes an action that reads the file with a SHARED lock.
     *
     * @param action the action that will read the file
     * @return the result of the action
     * @throws ExecutionException if an exception occurred during the execution of the action
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     action
     */
    public <V> V read(@NonNull ExceptionFunction<File, V> action) throws ExecutionException {
        if (lockingScope == LockingScope.MULTI_PROCESS) {
            return doActionWithMultiProcessLocking(LockingType.SHARED, action);
        } else {
            return doActionWithSingleProcessLocking(LockingType.SHARED, action);
        }
    }

    /**
     * Executes an action that writes to (or deletes) the file with an EXCLUSIVE lock.
     *
     * @param action the action that will write to (or delete) the file
     * @return the result of the action
     * @throws ExecutionException if an exception occurred during the execution of the action
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     action
     */
    public <V> V write(@NonNull ExceptionFunction<File, V> action) throws ExecutionException {
        if (lockingScope == LockingScope.MULTI_PROCESS) {
            return doActionWithMultiProcessLocking(LockingType.EXCLUSIVE, action);
        } else {
            return doActionWithSingleProcessLocking(LockingType.EXCLUSIVE, action);
        }
    }

    /** Executes an action that accesses the file with multi-process locking. */
    private <V> V doActionWithMultiProcessLocking(
            @NonNull LockingType lockingType, @NonNull ExceptionFunction<File, V> action)
            throws ExecutionException {
        // Use a lock file that is unique to the (physical) file being synchronized (note that
        // fileToSynchronize has already been normalized earlier)
        File lockFile =
                new File(
                        fileToSynchronize.getParent(),
                        fileToSynchronize.getName() + LOCK_FILE_EXTENSION);

        // ReadWriteProcessLock does not require the lock file to be normalized since internally it
        // will normalize it. Plus, fileToSynchronize, and therefore lockFile, are already
        // normalized, so we don't need to normalize it here.
        // Also, the parent directory of fileToSynchronize and lockFile should already exist, as
        // required by SynchronizedFile#getInstanceWithMultiProcessLocking(File).
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile);
        ReadWriteProcessLock.Lock lock =
                lockingType == LockingType.SHARED
                        ? readWriteProcessLock.readLock()
                        : readWriteProcessLock.writeLock();
        try {
            lock.lock();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return action.accept(fileToSynchronize);
        } catch (Exception exception) {
            throw new ExecutionException(exception);
        } finally {
            try {
                lock.unlock();
            } catch (IOException e) {
                //noinspection ThrowFromFinallyBlock
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Executes an action that accesses the file with single-process locking. */
    private <V> V doActionWithSingleProcessLocking(
            @NonNull LockingType lockingType, @NonNull ExceptionFunction<File, V> action)
            throws ExecutionException {
        // ReadWriteThreadLock requires the lock file to be normalized. However, since
        // fileToSynchronize is already normalized, we don't need to normalize it here.
        ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(fileToSynchronize);
        ReadWriteThreadLock.Lock lock =
                lockingType == LockingType.SHARED
                        ? readWriteThreadLock.readLock()
                        : readWriteThreadLock.writeLock();
        lock.lock();
        try {
            return action.accept(fileToSynchronize);
        } catch (Exception exception) {
            throw new ExecutionException(exception);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes an action that creates the file if it does not yet exist.
     *
     * <p>This method throws a {@link RuntimeException} if the file does not exist but the action
     * does not create the file.
     *
     * <p>WARNING: It is not guaranteed that the file must exist after this method is executed,
     * since another thread/process might delete it after this method returns (known as the TOCTTOU
     * problem). Therefore, if a client wants to use this method to make sure the file exists for a
     * subsequent action, it must also make sure that no intervening thread/process may be deleting
     * the file after it is created.
     *
     * @param action the action that will create the file
     * @throws ExecutionException if an exception occurred during the execution of the action
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     action
     */
    public void createIfAbsent(@NonNull ExceptionConsumer<File> action) throws ExecutionException {
        boolean fileExists;
        try {
            fileExists = read(File::exists);
        } catch (ExecutionException exception) {
            // We don't throw ExecutionException directly here since the exception does not come
            // from the action given to this method.
            throw new RuntimeException(exception);
        }

        if (!fileExists) {
            try {
                write(
                        file -> {
                            // Check the file's existence again as it might have been changed by
                            // another thread/process since the last time we checked it.
                            if (!file.exists()) {
                                try {
                                    action.accept(file);
                                } catch (Exception exception) {
                                    throw new ActionExecutionException(exception);
                                }
                                Preconditions.checkState(
                                        file.exists(),
                                        "File "
                                                + file.getAbsolutePath()
                                                + " should have been created but has not");
                            }
                            return null;
                        });
            } catch (ExecutionException exception) {
                // We need to figure out whether the exception comes from the action given to this
                // method. If so, we rethrow the ExecutionException; otherwise, we rethrow the
                // exception as a RuntimeException (as documented in the javadoc of this method).
                for (Throwable exceptionInCausalChain : Throwables.getCausalChain(exception)) {
                    if (exceptionInCausalChain instanceof ActionExecutionException) {
                        throw exception;
                    }
                }
                throw new RuntimeException(exception);
            }
        }
    }

    /**
     * Checked exception thrown when an action aborts due to an {@link Exception}. This class is a
     * subclass of {@link ExecutionException} and is used to distinguish itself from other execution
     * exceptions thrown elsewhere.
     */
    @Immutable
    private static final class ActionExecutionException extends ExecutionException {

        public ActionExecutionException(@NonNull Exception exception) {
            super(exception);
        }
    }
}
