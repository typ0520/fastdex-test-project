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

package com.android.build.gradle.internal.transforms;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassWriter;

/**
 * Transform that slices the project's classes into approximately 10 slices for
 * {@link Scope#PROJECT} and {@link Scope#SUB_PROJECTS}.
 *
 * <p>Dependencies are not processed by the Slicer but will be dex'ed separately.
 */
public class InstantRunSlicer extends Transform {

    @VisibleForTesting
    static final String PACKAGE_FOR_GUARD_CLASS = "com/android/tools/ir/dummy";

    // since we use the last digit of the FQCN hashcode() as the bucket, 10 is the appropriate
    // number of slices.
    public static final int NUMBER_OF_SLICES_FOR_PROJECT_CLASSES = 10;

    private final ILogger logger;

    @NonNull
    private final InstantRunVariantScope variantScope;

    public InstantRunSlicer(@NonNull Logger logger, @NonNull InstantRunVariantScope variantScope) {
        this.logger = new LoggerWrapper(logger);
        this.variantScope = variantScope;
    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunSlicer";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws IOException, TransformException, InterruptedException {

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        boolean isIncremental = transformInvocation.isIncremental();
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        if (outputProvider == null) {
            logger.error(null /* throwable */, "null TransformOutputProvider for InstantRunSlicer");
            return;
        }

        Slices slices = new Slices();

        if (isIncremental) {
            processCodeChanges(inputs, outputProvider, slices);
        } else {
            slice(inputs, outputProvider, slices);
        }
    }

    /**
     * Combine all {@link Scope#PROJECT} and {@link Scope#SUB_PROJECTS} inputs into slices, ignore
     * all other inputs.
     *
     * @param inputs the transform's input
     * @param outputProvider the transform's output provider to create streams
     * @throws IOException if the files cannot be copied
     * @throws TransformException never thrown
     * @throws InterruptedException never thrown.
     */
    private static void slice(@NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider outputProvider,
            @NonNull Slices slices)
            throws IOException, TransformException, InterruptedException {

        // first pass, gather all input files, organize per package.
        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                File inputDir = directoryInput.getFile();
                for (File file : Files.fileTreeTraverser()
                        .breadthFirstTraversal(directoryInput.getFile())) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String packagePath = FileUtils
                            .relativePossiblyNonExistingPath(file.getParentFile(), inputDir);
                    slices.addElement(packagePath, file);
                }
            }
        }

        // now produces the output streams for each slice.
        slices.writeTo(outputProvider);
    }

    private void processCodeChanges(
            @NonNull final Collection<TransformInput> inputs,
            @NonNull final TransformOutputProvider outputProvider,
            @NonNull final Slices slices)
            throws TransformException, InterruptedException, IOException {

        // process all files
        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                for (Map.Entry<File, Status> changedFile : directoryInput.getChangedFiles()
                        .entrySet()) {
                    // get the output stream for this file.
                    File fileToProcess = changedFile.getKey();
                    Status status = changedFile.getValue();
                    File sliceOutputLocation = getOutputStreamForFile(
                            outputProvider, directoryInput, fileToProcess, slices);

                    // add the buildID timestamp to the slice out directory so we force the
                    // dex task to rerun, even if no .class files appear to have changed. This
                    // can happen when doing a lot of hot swapping with changes undoing
                    // themselves resulting in a state that was equal to the last restart state.
                    // In theory, it would not require to rebuild but it will confuse Android
                    // Studio is there is nothing to push so just be safe and rebuild.
                    if (fileToProcess.isFile()) {

                        if (!sliceOutputLocation.exists() && !sliceOutputLocation.mkdirs()) {
                            throw new IOException(
                                    "Cannot create folder " + sliceOutputLocation);
                        }
                        Files.write(
                                String.valueOf(
                                        variantScope.getInstantRunBuildContext().getBuildId()),
                                new File(sliceOutputLocation, "buildId.txt"),
                                Charsets.UTF_8);
                        logger.verbose("Writing buildId in %s because of %s",
                                sliceOutputLocation.getAbsolutePath(),
                                changedFile.toString());
                    }


                    String relativePath = FileUtils.relativePossiblyNonExistingPath(
                            fileToProcess, directoryInput.getFile());

                    File outputFile = new File(sliceOutputLocation, relativePath);
                    switch (status) {
                        case ADDED:
                        case CHANGED:
                            if (fileToProcess.isFile()) {
                                Files.createParentDirs(outputFile);
                                Files.copy(fileToProcess, outputFile);
                                logger.verbose("Copied %s to %s", fileToProcess, outputFile);
                            }
                            break;
                        case REMOVED:
                            // the outputFile may not exist as the fileToProcess was an intermediary
                            // folder
                            if (outputFile.exists()) {
                                if (outputFile.isDirectory()) {
                                    FileUtils.deleteDirectoryContents(outputFile);
                                }
                                if (!outputFile.delete()) {
                                    throw new TransformException(
                                            String.format("Cannot delete file %1$s",
                                                    outputFile.getAbsolutePath()));
                                }
                                logger.verbose("Deleted %s", outputFile);
                            }
                            break;
                        default:
                            throw new TransformException("Unhandled status " + status);

                    }
                }
            }
        }
    }

    private static File getOutputStreamForFile(
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull DirectoryInput input,
            @NonNull File file,
            @NonNull Slices slices) {

        String relativePackagePath = FileUtils.relativePossiblyNonExistingPath(file.getParentFile(),
                input.getFile());

        Slice slice = slices.getSliceFor(new Slice.SlicedElement(relativePackagePath, file));
        return transformOutputProvider.getContentLocation(slice.name,
                TransformManager.CONTENT_CLASS,
                Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS),
                Format.DIRECTORY);
    }

    private static class Slices {
        @NonNull
        private final List<Slice> slices = new ArrayList<>();

        private Slices() {
            for (int i=0; i <NUMBER_OF_SLICES_FOR_PROJECT_CLASSES; i++) {
                Slice newSlice = new Slice("slice_" + i, i);
                slices.add(newSlice);
            }
        }

        private void addElement(@NonNull String packagePath, @NonNull File file) {
            Slice.SlicedElement slicedElement = new Slice.SlicedElement(packagePath, file);
            Slice slice = getSliceFor(slicedElement);
            slice.add(slicedElement);
        }

        private void writeTo(@NonNull TransformOutputProvider outputProvider) throws IOException {
            for (Slice slice : slices) {
                slice.writeTo(outputProvider);
            }
        }

        private Slice getSliceFor(Slice.SlicedElement slicedElement) {
            return slices.get(slicedElement.getHashBucket());
        }
    }

    private static class Slice {

        private static class SlicedElement {
            @NonNull
            private final String packagePath;
            @NonNull
            private final File slicedFile;

            private SlicedElement(@NonNull String packagePath, @NonNull File slicedFile) {
                this.packagePath = packagePath;
                this.slicedFile = slicedFile;
            }

            /**
             * Returns the bucket number in which this {@link SlicedElement} belongs.
             * @return an integer between 0 and {@link #NUMBER_OF_SLICES_FOR_PROJECT_CLASSES}
             * exclusive that will be used to bucket this item.
             */
            public int getHashBucket() {
                String hashTarget = Strings.isNullOrEmpty(packagePath)
                        ? slicedFile.getName()
                        : packagePath;
                return Math.abs(hashTarget.hashCode() % NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);
            }

            @Override
            public String toString() {
                return packagePath + slicedFile.getName();
            }
        }

        @NonNull
        private final String name;
        private final int hashBucket;
        private final List<SlicedElement> slicedElements;

        private Slice(@NonNull String name, int hashBucket) {
            this.name = name;
            this.hashBucket = hashBucket;
            slicedElements = new ArrayList<>();
        }

        private void add(@NonNull SlicedElement slicedElement) {
            if (hashBucket != slicedElement.getHashBucket()) {
                throw new RuntimeException("Wrong bucket for " + slicedElement);
            }
            slicedElements.add(slicedElement);
        }

        private void writeTo(@NonNull TransformOutputProvider outputProvider) throws IOException {

            File sliceOutputLocation = outputProvider.getContentLocation(name,
                    TransformManager.CONTENT_CLASS,
                    Sets.immutableEnumSet(Scope.PROJECT, Scope.SUB_PROJECTS),
                    Format.DIRECTORY);

            FileUtils.cleanOutputDir(sliceOutputLocation);

            // always write our dummy guard class, nobody will ever delete this file which mean
            // the slice will continue existing even it there is no other .class file in it.
            createGuardClass(name, sliceOutputLocation);

            // now copy all the files into its new location.
            for (Slice.SlicedElement slicedElement : slicedElements) {
                File outputFile = new File(sliceOutputLocation, new File(slicedElement.packagePath,
                        slicedElement.slicedFile.getName()).getPath());
                Files.createParentDirs(outputFile);
                Files.copy(slicedElement.slicedFile, outputFile);
            }
        }
    }

    private static void createGuardClass(@NonNull String name, @NonNull File outputDir)
            throws IOException {

        ClassWriter cw = new ClassWriter(0);

        File packageDir = new File(outputDir, PACKAGE_FOR_GUARD_CLASS);
        File outputFile = new File(packageDir, name + ".class");
        Files.createParentDirs(outputFile);

        // use package separator below which is always /
        String appInfoOwner = PACKAGE_FOR_GUARD_CLASS + '/' + name;
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, appInfoOwner, null, "java/lang/Object", null);
        cw.visitEnd();

        Files.write(cw.toByteArray(), outputFile);
    }
}
