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

package com.android.build.gradle.internal.tasks.databinding;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;

/**
 * This transform merges the data binding related data from external artifacts into a folder which
 * is then passed into the data binding annotation processor.
 */
public class DataBindingMergeArtifactsTransform extends Transform {
    @NonNull
    private final ILogger logger;
    private final File outFolder;
    public DataBindingMergeArtifactsTransform(@NonNull Logger logger, @NonNull File outFolder) {
        this.logger = new LoggerWrapper(logger);
        this.outFolder = outFolder;
    }

    @NonNull
    @Override
    public String getName() {
        return "dataBindingMergeArtifacts";
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return Collections.singleton(outFolder);
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        Collection<TransformInput> inputs = transformInvocation.getReferencedInputs();
        //noinspection ResultOfMethodCallIgnored
        outFolder.mkdirs();
        if (transformInvocation.isIncremental()) {
            incrementalUpdate(inputs);
        } else {
            fullCopy(inputs);
        }
    }

    private void incrementalUpdate(@NonNull Collection<TransformInput> inputs) {
        inputs.forEach(input -> input.getDirectoryInputs().forEach(directoryInput -> {
            directoryInput.getChangedFiles().forEach((file, status) -> {
                if (isResource(file.getName())) {
                    switch (status) {
                        case NOTCHANGED:
                            // Ignore
                            break;
                        case ADDED:
                        case CHANGED:
                            try {
                                FileUtils.copyFile(file, new File(outFolder, file.getName()));
                            } catch (IOException e) {
                                logger.error(e, "Cannot copy data binding artifacts from "
                                        + "dependency.");
                            }
                            break;
                        case REMOVED:
                            FileUtils.deleteQuietly(new File(outFolder, file.getName()));
                            break;
                    }
                }
            });
        }));
        inputs.forEach(input -> input.getJarInputs().forEach(jarInput -> {
            switch (jarInput.getStatus()) {
                case NOTCHANGED:
                    // Ignore
                    break;
                case ADDED:
                case CHANGED:
                    try {
                        extractBinFilesFromJar(jarInput.getFile());
                    } catch (IOException e) {
                        logger.error(e, "Cannot extract data binding from input jar ");
                    }
                    break;
                case REMOVED:
                    File jarOutFolder = getOutFolderForJarFile(jarInput.getFile());
                    FileUtils.deleteQuietly(jarOutFolder);
                    break;
            }
        }));
    }

    private void fullCopy(Collection<TransformInput> inputs) throws IOException {
        FileUtils.deleteQuietly(outFolder);
        FileUtils.forceMkdir(outFolder);
        for (TransformInput input : inputs) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                File dataBindingDir = dirInput.getFile();
                if (!dataBindingDir.exists()) {
                    continue;
                }
                File artifactFolder = new File(dataBindingDir,
                        DataBindingBuilder.INCREMENTAL_BIN_AAR_DIR);
                if (!artifactFolder.exists()) {
                    continue;
                }
                //noinspection ConstantConditions
                for (String artifactName : artifactFolder.list()) {
                    if (isResource(artifactName)) {
                        FileUtils.copyFile(new File(artifactFolder, artifactName),
                                new File(outFolder, artifactName));
                    }
                }
            }
            for(JarInput jarInput : input.getJarInputs()) {
                File jarFile = jarInput.getFile();
                extractBinFilesFromJar(jarFile);
            }
        }
    }

    private static boolean isResource(String fileName) {
        for (String ext : DataBindingBuilder.RESOURCE_FILE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.DATA_BINDING_ARTIFACT;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        //noinspection unchecked
        return ImmutableSet.of();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    private void extractBinFilesFromJar(File jarFile) throws IOException {
        File jarOutFolder = getOutFolderForJarFile(jarFile);
        FileUtils.deleteQuietly(jarOutFolder);
        FileUtils.forceMkdir(jarOutFolder);

        try (Closer localCloser = Closer.create()) {
            FileInputStream fis = localCloser.register(new FileInputStream(jarFile));
            ZipInputStream zis = localCloser.register(new ZipInputStream(fis));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();

                if (!isResource(name)) {
                    continue;
                }
                // get rid of the path. We don't need it since the file name includes the domain
                name = new File(name).getName();
                File out = new File(jarOutFolder, name);
                //noinspection ResultOfMethodCallIgnored
                FileOutputStream fos = localCloser.register(new FileOutputStream(out));
                ByteStreams.copy(zis, fos);
                zis.closeEntry();
            }
        }
    }

    @NonNull
    private File getOutFolderForJarFile(File jarFile) {
        return new File(outFolder, getJarFilePrefix(jarFile));
    }

    /**
     * Files exported from jars are exported into a certain folder so that we can rebuild them
     * when the related jar file changes.
     */
    @NonNull
    private static String getJarFilePrefix(@NonNull File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        // add a hash of the original file path.
        String input = inputFile.getAbsolutePath();
        HashFunction hashFunction = Hashing.sha1();
        HashCode hashCode = hashFunction.hashString(input, Charsets.UTF_16LE);

        return name + "-" + hashCode.toString();
    }
}
