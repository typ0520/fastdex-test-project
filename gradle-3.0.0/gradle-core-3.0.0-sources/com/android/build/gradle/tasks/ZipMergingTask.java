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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.google.common.collect.Sets;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Task to merge the res/classes intermediate jars from a library into a single one */
@CacheableTask
public class ZipMergingTask extends DefaultAndroidTask {

    private final byte[] buffer = new byte[8192];

    private FileCollection inputFiles;

    private File outputFile;

    @VisibleForTesting
    void init(FileCollection inputFiles, File outputFile) {
        this.inputFiles = inputFiles;
        this.outputFile = outputFile;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getInputFiles() {
        return inputFiles;
    }

    @TaskAction
    public void merge() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                ZipOutputStream zos = new ZipOutputStream(bos)) {

            Set<String> entries = Sets.newHashSet();

            for (File inputFile : inputFiles) {
                try (FileInputStream fis = new FileInputStream(inputFile);
                        ZipInputStream zis = new ZipInputStream(fis)) {

                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            continue;
                        }

                        String entryName = entry.getName();
                        if (entries.contains(entryName)) {
                            // non class files can be duplicated between res and classes jar
                            // due to annotation processor or other compiler (kotlin) generating
                            // resources
                            continue;
                        } else {
                            entries.add(entryName);
                        }

                        zos.putNextEntry(entry);

                        // read the content of the entry from the input stream, and write it into
                        // the archive.
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            zos.write(buffer, 0, count);
                        }

                        // close the entries for this file
                        zos.closeEntry();
                        zis.closeEntry();
                    }
                }
            }
        }
    }

    public static class ConfigAction implements TaskConfigAction<ZipMergingTask> {

        private VariantScope scope;

        private File outputFile;

        public ConfigAction(VariantScope scope, File outputFile) {
            this.scope = scope;
            this.outputFile = outputFile;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("createFullJar");
        }

        @NonNull
        @Override
        public Class<ZipMergingTask> getType() {
            return ZipMergingTask.class;
        }

        @Override
        public void execute(@NonNull ZipMergingTask task) {
            task.init(
                    scope.getOutput(TaskOutputType.LIBRARY_CLASSES)
                            .plus(scope.getOutput(TaskOutputType.LIBRARY_JAVA_RES)),
                    outputFile);
            task.setVariantName(scope.getFullVariantName());
        }
    }
}
