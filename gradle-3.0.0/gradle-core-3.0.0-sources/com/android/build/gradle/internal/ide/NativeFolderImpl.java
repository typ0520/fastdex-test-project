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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.NativeFolder;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link NativeFolder}
 */
@Immutable
public final class NativeFolderImpl implements NativeFolder, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final File folderPath;
    @NonNull
    private final Map<String, String> perLanguageSettings;
    @Nullable
    private final File workingDirectory;


    public NativeFolderImpl(
            @NonNull File folderPath,
            @NonNull Map<String, String> perLanguageSettings,
            @Nullable File workingDirectory) {
        this.folderPath = folderPath;
        this.perLanguageSettings = perLanguageSettings;
        this.workingDirectory = workingDirectory;
    }

    @Override
    @NonNull
    public File getFolderPath() {
        return folderPath;
    }

    @Override
    @NonNull
    public Map<String, String> getPerLanguageSettings() {
        return perLanguageSettings;
    }

    @Override
    @Nullable
    public File getWorkingDirectory() {
        return workingDirectory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NativeFolderImpl that = (NativeFolderImpl) o;
        return Objects.equals(folderPath, that.folderPath) &&
                Objects.equals(perLanguageSettings, that.perLanguageSettings) &&
                Objects.equals(workingDirectory, that.workingDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folderPath, perLanguageSettings, workingDirectory);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("folderPath", folderPath)
                .add("perLanguageSettings", perLanguageSettings)
                .add("workingDirectory", workingDirectory)
                .toString();
    }
}
