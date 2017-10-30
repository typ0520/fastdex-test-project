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

package com.android.builder.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableSortedMap;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Jar Merger class. */
public class JarMerger implements Closeable {

    public interface Transformer {
        /**
         * Transforms the given file.
         *
         * @param entryPath the path within the jar file
         * @param input an input stream of the contents of the file
         * @return a new input stream if the file is transformed in some way, the same input stream
         *     if the file is to be kept as is and null if the file should not be packaged.
         */
        @Nullable
        InputStream filter(@NonNull String entryPath, @NonNull InputStream input);
    }

    public static final FileTime ZERO_TIME = FileTime.fromMillis(0);

    private final byte[] buffer = new byte[8192];

    @NonNull private final JarOutputStream jarOutputStream;

    @Nullable private final ZipEntryFilter filter;

    public JarMerger(@NonNull Path jarFile) throws IOException {
        this(jarFile, null);
    }

    public JarMerger(@NonNull Path jarFile, @Nullable ZipEntryFilter filter) throws IOException {
        this.filter = filter;
        Files.createDirectories(jarFile.getParent());
        jarOutputStream =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarFile)));
    }

    public void addDirectory(@NonNull Path directory) throws IOException {
        addDirectory(directory, filter, null);
    }

    public void addDirectory(
            @NonNull Path directory,
            @Nullable ZipEntryFilter filterOverride,
            @Nullable Transformer transformer)
            throws IOException {
        ImmutableSortedMap.Builder<String, Path> candidateFiles = ImmutableSortedMap.naturalOrder();
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        String entryPath =
                                PathUtils.toSystemIndependentPath(directory.relativize(file));
                        try {
                            if (filterOverride != null && !filterOverride.checkEntry(entryPath)) {
                                return FileVisitResult.CONTINUE;
                            }
                        } catch (ZipAbortException e) {
                            throw new IOException(e);
                        }
                        candidateFiles.put(entryPath, file);
                        return FileVisitResult.CONTINUE;
                    }
                });
        ImmutableSortedMap<String, Path> sortedFiles = candidateFiles.build();
        for (Map.Entry<String, Path> entry : sortedFiles.entrySet()) {
            String entryPath = entry.getKey();
            try (InputStream is = new BufferedInputStream(Files.newInputStream(entry.getValue()))) {
                if (transformer != null) {
                    @Nullable InputStream is2 = transformer.filter(entryPath, is);
                    if (is2 != null) {
                        write(new JarEntry(entryPath), is2);
                    }
                } else {
                    write(new JarEntry(entryPath), is);
                }
            }
        }
    }

    public void addJar(@NonNull Path file) throws IOException {
        addJar(file, filter);
    }

    public void addJar(@NonNull Path file, @Nullable ZipEntryFilter filterOverride)
            throws IOException {
        try (ZipInputStream zis =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {

            // loop on the entries of the jar file package and put them in the final jar
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // do not take directories
                if (entry.isDirectory()) {
                    continue;
                }

                // Filter out files, e.g. META-INF folder, not classes.
                String name = entry.getName();
                try {
                    if (filterOverride != null && !filterOverride.checkEntry(name)) {
                        continue;
                    }
                } catch (ZipAbortException e) {
                    throw new IOException(e);
                }

                // Preserve the STORED method of the input entry, otherwise create a new entry so
                // that the compressed len is recomputed.
                JarEntry newEntry =
                        entry.getMethod() == ZipEntry.STORED
                                ? new JarEntry(entry)
                                : new JarEntry(name);

                // read the content of the entry from the input stream, and write it into the
                // archive.
                write(newEntry, zis);
            }
        }
    }

    @Override
    public void close() throws IOException {
        jarOutputStream.close();
    }

    private void write(@NonNull JarEntry entry, @NonNull InputStream from) throws IOException {
        entry.setLastModifiedTime(ZERO_TIME);
        entry.setLastAccessTime(ZERO_TIME);
        entry.setCreationTime(ZERO_TIME);
        jarOutputStream.putNextEntry(entry);
        int count;
        while ((count = from.read(buffer)) != -1) {
            jarOutputStream.write(buffer, 0, count);
        }
        jarOutputStream.closeEntry();
    }
}
