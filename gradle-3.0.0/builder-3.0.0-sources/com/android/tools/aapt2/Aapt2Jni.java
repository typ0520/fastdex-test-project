/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.aapt2;

import com.android.annotations.NonNull;
import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

/**
 * {@code aapt2} JNI interface. When instansiated, this class will load the native library
 * containing the {@code aapt2} native code. The native library is loaded from a resource.
 *
 * <p>To use this class simply create a new instance and invoke one of the public static methods.
 * {@code aapt2} is thread-safe ish.
 */
public final class Aapt2Jni {

    @GuardedBy("Aapt2Jni.class")
    private static boolean loaded;

    public interface Creator {
        void create(@NonNull Path location) throws IOException;
    }

    public interface Cache {
        @NonNull
        Path getCachedDirectory(@NonNull HashCode hashCode, @NonNull Creator creator)
                throws IOException;
    }

    public Aapt2Jni(@NonNull Cache cache) throws IOException {
        loadJni(cache);
    }

    public static synchronized void loadJni(@NonNull Cache cache) throws IOException {
        if (loaded) {
            return;
        }

        Aapt2JniPlatform platform = Aapt2JniPlatform.getCurrentPlatform();

        Path libs = cache.getCachedDirectory(platform.getCacheKey(), platform::writeToDirectory);

        for (Path path : platform.getFiles(libs)) {
            System.load(path.toString());
        }

        try {
            ping();
        } catch (Exception e) {
            throw new Aapt2Exception("Failed to load AAPT2 jni binding", e);
        }
        loaded = true;
    }

    /**
     * Invokes {@code aapt2} to perform resource compilation.
     *
     * @param arguments arguments for compilation (see {@code Compile.cpp})
     */
    @SuppressWarnings("MethodMayBeStatic") // Depends on JNI loading, which is done at construction.
    @CheckReturnValue
    public Aapt2Result compile(@Nonnull List<String> arguments) {
        Aapt2Result.Builder builder = Aapt2Result.builder();
        int returnCode = nativeCompile(arguments, builder);
        return builder.setReturnCode(returnCode).build();
    }

    /**
     * Invokes {@code aapt2} to perform linking.
     *
     * @param arguments arguments for linking (see {@code Link.cpp})
     */
    @SuppressWarnings("MethodMayBeStatic") // Depends on JNI loading, which is done at construction.
    @CheckReturnValue
    public Aapt2Result link(@Nonnull List<String> arguments) {
        Aapt2Result.Builder builder = Aapt2Result.builder();
        int returnCode = nativeLink(arguments, builder);
        return builder.setReturnCode(returnCode).build();
    }

    /**
     * JNI call for a method that does nothing, but allows checking whether the shared library has
     * been loaded.
     *
     * <p>Even if this class is loaded multiple times with multiple class loaders we want to load
     * the shared library only once to avoid increasing the memory footprint. If this method can be
     * called successfully, then we know we the library has already been loaded.
     */
    private static native void ping();

    /**
     * JNI call.
     *
     * @param arguments arguments for compilation (see {@code Compile.cpp})
     */
    private static native int nativeCompile(
            @Nonnull List<String> arguments, @Nonnull Aapt2JniLogCallback diagnostics);

    /**
     * JNI call.
     *
     * @param arguments arguments for linking (see {@code Link.cpp})
     */
    private static native int nativeLink(
            @Nonnull List<String> arguments, @Nonnull Aapt2JniLogCallback diagnostics);

}
