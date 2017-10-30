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

package com.android.builder.files;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.io.File;


/**
 * Representation of a file with respect to a base directory. A {@link RelativeFile} contains
 * information on the file, the base directory and the relative path from the base directory to
 * the file. The relative path is kept in OS independent form with sub directories separated by
 * slashes.
 *
 * <p>Neither the file nor the base need to exist. They are treated as abstract paths.
 */
public class RelativeFile {

    /**
     * The base directory.
     */
    @NonNull
    private final File base;

    /**
     * The file.
     */
    @NonNull
    private final File file;

    /**
     * The OS independent path from base to file, including the file name in the end.
     */
    @NonNull
    private final String osIndependentRelativePath;

    /**
     * Creates a new relative file.
     *
     * @param base the base directory
     * @param file the file, must not be the same as the base directory and must be located inside
     * {@code base}
     */
    public RelativeFile(@NonNull File base, @NonNull File file) {
        Preconditions.checkArgument(!base.equals(file), "base.equals(file)");

        this.base = base;
        this.file = file;

        String relativePath = FileUtils. relativePossiblyNonExistingPath(file, base);

        osIndependentRelativePath = FileUtils.toSystemIndependentPath(relativePath);
    }

    /**
     * Obtains the base directory.
     *
     * @return the base directory as provided when created the object
     */
    @NonNull
    public File getBase() {
        return base;
    }

    /**
     * Obtains the file.
     *
     * @return the file as provided when created the object
     */
    @NonNull
    public File getFile() {
        return file;
    }

    /**
     * Obtains the OS independent path. The general contract of the normalized relative path is that
     * by replacing the slashes by file separators in the relative path and appending it to the
     * base directory's path, the resulting path is the file's path
     *
     * @return the normalized path, separated by slashes; directories have a terminating slash
     */
    @NonNull
    public String getOsIndependentRelativePath() {
        return osIndependentRelativePath;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(base, file);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RelativeFile)) {
            return false;
        }

        RelativeFile rf = (RelativeFile) obj;
        return Objects.equal(base, rf.base) && Objects.equal(file, rf.file);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("base", base)
                .add("path", osIndependentRelativePath)
                .toString();
    }
}
