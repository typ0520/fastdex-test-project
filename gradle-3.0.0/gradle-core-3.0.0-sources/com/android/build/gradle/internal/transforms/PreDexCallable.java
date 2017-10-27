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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.ExceptionRunnable;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Callable helper class used to invoke dx for pre-dexing.
 */
class PreDexCallable implements Callable<Void> {

    /**
     * Input parameters to be provided by the client when using {@link FileCache}.
     *
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory. This enum class lists the input parameters that are
     * used in {@link PreDexCallable}.
     */
    private enum FileCacheInputParams {

        /** The input file. */
        FILE,

        /** Revision of the build tools. */
        BUILD_TOOLS_REVISION,

        /** Whether jumbo mode is enabled. */
        JUMBO_MODE,

        /** Whether optimize is enabled. */
        OPTIMIZE,

        /** Whether multi-dex is enabled. */
        MULTI_DEX,

        /** List of additional parameters. */
        ADDITIONAL_PARAMETERS,

        /** Min sdk version passed to dx. */
        MIN_SDK_VERSION,
    }

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(PreDexCallable.class);

    @NonNull private final File from;
    @NonNull private final File to;
    @NonNull private final Set<String> hashes;
    @NonNull private final ProcessOutputHandler outputHandler;
    @Nullable private final FileCache buildCache;
    @NonNull private final DexingType dexingType;
    @NonNull private final DexOptions dexOptions;
    @NonNull private final AndroidBuilder androidBuilder;
    private final int minSdkVersion;

    public PreDexCallable(
            @NonNull File from,
            @NonNull File to,
            @NonNull Set<String> hashes,
            @NonNull ProcessOutputHandler outputHandler,
            @Nullable FileCache buildCache,
            @NonNull DexingType dexingType,
            @NonNull DexOptions dexOptions,
            @NonNull AndroidBuilder androidBuilder,
            int minSdkVersion) {
        this.from = from;
        this.to = to;
        this.hashes = hashes;
        this.outputHandler = outputHandler;
        this.buildCache = buildCache;
        this.dexingType = dexingType;
        this.dexOptions = dexOptions;
        this.androidBuilder = androidBuilder;
        this.minSdkVersion = minSdkVersion;
    }

    @Override
    public Void call() throws Exception {
        logger.verbose("predex called for %s", from);
        // TODO remove once we can properly add a library as a dependency of its test.
        String hash = getFileHash(from);

        synchronized (hashes) {
            if (hashes.contains(hash)) {
                logger.verbose("Input with the same hash exists. Pre-dexing skipped.");
                return null;
            }

            hashes.add(hash);
        }

        ExceptionRunnable preDexLibraryAction =
                () -> {
                    FileUtils.deletePath(to);
                    Files.createParentDirs(to);
                    if (dexingType.isMultiDex()) {
                        FileUtils.mkdirs(to);
                    }
                    androidBuilder.preDexLibrary(
                            from,
                            to,
                            dexingType.isMultiDex(),
                            dexOptions,
                            outputHandler,
                            minSdkVersion);
                };

        // If the build cache is used, run pre-dexing using the cache
        if (buildCache != null) {
            FileCache.Inputs buildCacheInputs =
                    getBuildCacheInputs(
                            from,
                            androidBuilder.getTargetInfo().getBuildTools().getRevision(),
                            dexOptions,
                            dexingType.isMultiDex(),
                            minSdkVersion);
            FileCache.QueryResult result;
            try {
                result = buildCache.createFile(to, buildCacheInputs, preDexLibraryAction);
            } catch (ExecutionException exception) {
                throw new RuntimeException(
                        String.format(
                                "Unable to pre-dex '%1$s' to '%2$s'",
                                from.getAbsolutePath(),
                                to.getAbsolutePath()),
                        exception);
            } catch (Exception exception) {
                throw new RuntimeException(
                        String.format(
                                "Unable to pre-dex '%1$s' to '%2$s' using the build cache at"
                                        + " '%3$s'.\n"
                                        + "%4$s",
                                from.getAbsolutePath(),
                                to.getAbsolutePath(),
                                buildCache.getCacheDirectory().getAbsolutePath(),
                                BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE),
                        exception);
            }
            if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
                Verify.verifyNotNull(result.getCauseOfCorruption());
                logger.verbose(
                        "The build cache at '%1$s' contained an invalid cache entry.\n"
                                + "Cause: %2$s\n"
                                + "We have recreated the cache entry.\n"
                                + "%3$s",
                        buildCache.getCacheDirectory().getAbsolutePath(),
                        Throwables.getStackTraceAsString(result.getCauseOfCorruption()),
                        BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE);
            }
        } else {
            preDexLibraryAction.run();
        }
        return null;
    }

    /**
     * Returns a {@link FileCache.Inputs} object computed from the given parameters for the
     * predex-library task to use the build cache.
     */
    @NonNull
    private static FileCache.Inputs getBuildCacheInputs(
            @NonNull File inputFile,
            @NonNull Revision buildToolsRevision,
            @NonNull DexOptions dexOptions,
            boolean multiDex,
            int minSdkVersion)
            throws IOException {
        // To use the cache, we need to specify all the inputs that affect the outcome of a pre-dex
        // (see DxDexKey for an exhaustive list of these inputs)
        FileCache.Inputs.Builder buildCacheInputs =
                new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY);

        buildCacheInputs
                .putFile(
                        FileCacheInputParams.FILE.name(),
                        inputFile,
                        FileCache.FileProperties.PATH_HASH)
                .putString(
                        FileCacheInputParams.BUILD_TOOLS_REVISION.name(),
                        buildToolsRevision.toString())
                .putBoolean(FileCacheInputParams.JUMBO_MODE.name(), dexOptions.getJumboMode())
                .putBoolean(FileCacheInputParams.OPTIMIZE.name(), true)
                .putBoolean(FileCacheInputParams.MULTI_DEX.name(), multiDex)
                .putLong(FileCacheInputParams.MIN_SDK_VERSION.name(), minSdkVersion);

        List<String> additionalParams = dexOptions.getAdditionalParameters();
        for (int i = 0; i < additionalParams.size(); i++) {
            buildCacheInputs.putString(
                    FileCacheInputParams.ADDITIONAL_PARAMETERS.name() + "[" + i + "]",
                    additionalParams.get(i));
        }

        return buildCacheInputs.build();
    }


    /**
     * Returns the hash of a file.
     *
     * <p>If the file is a folder, it's a hash of its path. If the file is a file, then it's a hash
     * of the file itself.
     *
     * @param file the file to hash
     */
    @NonNull
    private static String getFileHash(@NonNull File file) throws IOException {
        HashCode hashCode;
        HashFunction hashFunction = Hashing.sha1();
        if (file.isDirectory()) {
            hashCode = hashFunction.hashString(file.getPath(), Charsets.UTF_16LE);
        } else {
            hashCode = Files.hash(file, hashFunction);
        }

        return hashCode.toString();
    }
}
