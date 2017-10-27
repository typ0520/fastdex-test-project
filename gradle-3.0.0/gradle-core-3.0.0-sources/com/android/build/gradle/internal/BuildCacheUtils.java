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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.utils.FileCache;
import com.android.prefs.AndroidLocation;
import java.io.File;
import java.util.function.Function;
import java.util.function.Supplier;
import org.gradle.api.Project;

/**
 * Class that contains utility methods for working with the build cache.
 */
public final class BuildCacheUtils {

    @NonNull public static final String BUILD_CACHE_TROUBLESHOOTING_MESSAGE =
            "To troubleshoot the issue or learn how to disable the build cache,"
                    + " go to https://d.android.com/r/tools/build-cache.html.\n"
                    + "If you are unable to fix the issue,"
                    + " please file a bug at https://d.android.com/studio/report-bugs.html.";

    /**
     * Returns a {@link FileCache} instance representing the build cache if the build cache is
     * enabled, or null if it is disabled. If enabled, the build cache directory is set to a
     * user-defined directory, or a default directory if the user-defined directory is not provided.
     *
     * @throws RuntimeException if the ".android" directory does not exist or the build cache cannot
     *     be created
     */
    @Nullable
    public static FileCache createBuildCacheIfEnabled(
            @NonNull Project project, @NonNull ProjectOptions projectOptions) {
        return createBuildCacheIfEnabled(project.getRootProject()::file, projectOptions);
    }

    @Nullable
    @VisibleForTesting
    static FileCache createBuildCacheIfEnabled(
            @NonNull Function<Object, File> rootProjectFile,
            @NonNull ProjectOptions projectOptions) {
        // Use a default directory if the user-defined directory is not provided
        Supplier<File> defaultBuildCacheDirSupplier = () -> {
            try {
                return new File(AndroidLocation.getFolder(), "build-cache");
            } catch (AndroidLocation.AndroidLocationException e) {
                throw new RuntimeException(e);
            }
        };

        return doCreateBuildCacheIfEnabled(
                rootProjectFile, projectOptions, defaultBuildCacheDirSupplier);
    }

    @Nullable
    @VisibleForTesting
    static FileCache doCreateBuildCacheIfEnabled(
            @NonNull Function<Object, File> rootProjectFile,
            @NonNull ProjectOptions projectOptions,
            @NonNull Supplier<File> defaultBuildCacheDirSupplier) {
        if (projectOptions.get(BooleanOption.ENABLE_BUILD_CACHE)) {
            String buildCacheDirOverride = projectOptions.get(StringOption.BUILD_CACHE_DIR);
            return FileCache.getInstanceWithMultiProcessLocking(
                    buildCacheDirOverride != null
                            ? rootProjectFile.apply(buildCacheDirOverride)
                            : defaultBuildCacheDirSupplier.get());
        } else {
            return null;
        }
    }
}
