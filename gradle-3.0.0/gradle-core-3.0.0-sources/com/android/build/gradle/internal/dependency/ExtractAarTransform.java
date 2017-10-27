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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.utils.FileUtils.mkdirs;

import com.android.SdkConstants;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/** Transform that extracts an AAR file into a folder. */
public class ExtractAarTransform extends ArtifactTransform {

    private static final String LIBS_PREFIX = SdkConstants.LIBS_FOLDER + '/';
    private static final int LIBS_PREFIX_LENGTH = LIBS_PREFIX.length();
    private static final int JARS_PREFIX_LENGTH = FD_JARS.length() + 1; // for jars/

    @Inject
    public ExtractAarTransform() {}

    @Override
    public List<File> transform(File input) {
        File outputDir = getOutputDirectory();

        mkdirs(outputDir);

        StringBuilder sb = new StringBuilder(20); // enough for libs/classes.jar at least.
        sb.append(FD_JARS).append(File.separatorChar);

        try (InputStream fis = new BufferedInputStream(new FileInputStream(input));
                ZipInputStream zis = new ZipInputStream(fis)) {
            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    String name = entry.getName();

                    // do not take directories
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String path = name;

                    // relocate root Jar files.
                    if (path.equals(FN_CLASSES_JAR) || path.equals(FN_LINT_JAR)) {
                        sb.setLength(JARS_PREFIX_LENGTH);
                        sb.append(path);
                        path = sb.toString();

                    } else if (path.startsWith(LIBS_PREFIX)) {
                        sb.setLength(JARS_PREFIX_LENGTH);

                        // in case we have libs/classes.jar we are going to rename them, due an
                        // issue in Gradle
                        // FIXME stop doing this once this is fixed in gradle. b/65298222
                        String path2 = path.substring(LIBS_PREFIX_LENGTH);
                        if (FN_CLASSES_JAR.equals(path2)) {
                            sb.append(LIBS_PREFIX).append("classes-2" + DOT_JAR);
                        } else if (FN_LINT_JAR.equals(path2)) {
                            sb.append(LIBS_PREFIX).append("lint-2" + DOT_JAR);
                        } else {
                            sb.append(LIBS_PREFIX).append(path2);
                        }
                        path = sb.toString();
                    }

                    File outputFile = new File(outputDir, path.replace('/', File.separatorChar));
                    mkdirs(outputFile.getParentFile());

                    try (OutputStream outputStream =
                            new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        ByteStreams.copy(zis, outputStream);
                        outputStream.flush();
                    }
                } finally {
                    zis.closeEntry();
                }
            }

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        // verify that we have a classes.jar, if we don't just create an empty one.
        File classesJar = new File(new File(outputDir, FD_JARS), FN_CLASSES_JAR);
        if (!classesJar.exists()) {
            try {
                Files.createParentDirs(classesJar);
                JarOutputStream jarOutputStream =
                        new JarOutputStream(
                                new BufferedOutputStream(new FileOutputStream(classesJar)),
                                new Manifest());
                jarOutputStream.close();
            } catch (IOException e) {
                throw new RuntimeException("Cannot create missing classes.jar", e);
            }
        }

        return ImmutableList.of(outputDir);
    }
}
