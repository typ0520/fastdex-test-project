/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache
License, Version 2.0 (the
"License");
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

import static com.android.build.api.transform.QualifiedContent.Scope;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.utils.ExceptionRunnable;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * When running Desugar, we need to make sure stack frames information is valid in the class files.
 * This is due to fact that Desugar may load classes in the JVM, and if stack frame information is
 * invalid for bytecode 1.7 and above, {@link VerifyError} is thrown. Also, if stack frames are
 * broken, ASM might be unable to read those classes.
 *
 * <p>This transform will load all class files from all external jars, and will use ASM to
 * recalculate the stack frames information. In order to obtain new stack frames, types need to be
 * resolved.
 *
 * <p>This transform requires external libraries as inputs, and all other scope types are
 * referenced. Reason is that loading a class from an external jar, might depend on loading a class
 * that could be located in any of the referenced scopes. In case we are unable to resolve types,
 * content of the original class file will be copied to the the output as we do not know upfront if
 * Desugar will actually load that type.
 */
public class FixStackFramesTransform extends Transform {

    /** ASM class writer that uses specified class loader to resolve types. */
    private static class FixFramesVisitor extends ClassWriter {

        @NonNull private final URLClassLoader classLoader;

        public FixFramesVisitor(int flags, @NonNull URLClassLoader classLoader) {
            super(flags);
            this.classLoader = classLoader;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            Class<?> c;
            Class<?> d;
            ClassLoader classLoader = this.classLoader;
            try {
                c = Class.forName(type1.replace('/', '.'), false, classLoader);
                d = Class.forName(type2.replace('/', '.'), false, classLoader);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format(
                                "Unable to find common supper type for %s and %s.", type1, type2),
                        e);
            }
            if (c.isAssignableFrom(d)) {
                return type1;
            }
            if (d.isAssignableFrom(c)) {
                return type2;
            }
            if (c.isInterface() || d.isInterface()) {
                return "java/lang/Object";
            } else {
                do {
                    c = c.getSuperclass();
                } while (!c.isAssignableFrom(d));
                return c.getName().replace('.', '/');
            }
        }
    }

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(FixStackFramesTransform.class);
    private static final FileTime ZERO = FileTime.fromMillis(0);

    @NonNull private final Supplier<List<File>> androidJarClasspath;
    @NonNull private final List<Path> compilationBootclasspath;
    @Nullable private final FileCache userCache;
    @NonNull private final WaitableExecutor waitableExecutor;
    @Nullable private URLClassLoader classLoader = null;

    public FixStackFramesTransform(
            @NonNull Supplier<List<File>> androidJarClasspath,
            @NonNull String compilationBootclasspath,
            @Nullable FileCache userCache) {
        this.androidJarClasspath = androidJarClasspath;
        this.compilationBootclasspath = PathUtils.getClassPathItems(compilationBootclasspath);
        this.userCache = userCache;
        this.waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();
    }

    @NonNull
    @Override
    public String getName() {
        return "stackFramesFixer";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        return Sets.immutableEnumSet(Scope.EXTERNAL_LIBRARIES);
    }

    @NonNull
    @Override
    public Set<? super Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(
                Scope.PROJECT, Scope.SUB_PROJECTS, Scope.PROVIDED_ONLY, Scope.TESTED_CODE);
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> files = ImmutableList.builder();
        androidJarClasspath.get().forEach(file -> files.add(SecondaryFile.nonIncremental(file)));

        compilationBootclasspath.forEach(
                file -> files.add(SecondaryFile.nonIncremental(file.toFile())));

        return files.build();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @NonNull
    private URLClassLoader getClassLoader(@NonNull TransformInvocation invocation)
            throws MalformedURLException {
        if (classLoader == null) {
            ImmutableList.Builder<URL> urls = new ImmutableList.Builder<>();
            for (File file : androidJarClasspath.get()) {
                urls.add(file.toURI().toURL());
            }
            for (Path bootClasspath : this.compilationBootclasspath) {
                if (Files.exists(bootClasspath)) {
                    urls.add(bootClasspath.toUri().toURL());
                }
            }
            for (TransformInput inputs :
                    Iterables.concat(invocation.getInputs(), invocation.getReferencedInputs())) {
                for (DirectoryInput directoryInput : inputs.getDirectoryInputs()) {
                    if (directoryInput.getFile().isDirectory()) {
                        urls.add(directoryInput.getFile().toURI().toURL());
                    }
                }
                for (JarInput jarInput : inputs.getJarInputs()) {
                    if (jarInput.getFile().isFile()) {
                        urls.add(jarInput.getFile().toURI().toURL());
                    }
                }
            }

            ImmutableList<URL> allUrls = urls.build();
            URL[] classLoaderUrls = allUrls.toArray(new URL[allUrls.size()]);
            classLoader = new URLClassLoader(classLoaderUrls);
        }
        return classLoader;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        TransformOutputProvider outputProvider =
                Preconditions.checkNotNull(transformInvocation.getOutputProvider());

        boolean incremental = transformInvocation.isIncremental();

        try {
            for (TransformInput input : transformInvocation.getInputs()) {
                for (JarInput jarInput : input.getJarInputs()) {
                    Status status = jarInput.getStatus();
                    if (incremental && status == Status.NOTCHANGED) {
                        continue;
                    }

                    File output =
                            outputProvider.getContentLocation(
                                    jarInput.getName(),
                                    jarInput.getContentTypes(),
                                    jarInput.getScopes(),
                                    Format.JAR);

                    Files.deleteIfExists(output.toPath());
                    if (!incremental || status == Status.ADDED || status == Status.CHANGED) {
                        processJar(jarInput.getFile(), output, transformInvocation);
                    }
                }
            }

            waitableExecutor.waitForTasksWithQuickFail(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new TransformException(e);
        } finally {
            if (classLoader != null) {
                classLoader.close();
            }
        }
    }

    private void processJar(
            @NonNull File input, @NonNull File output, @NonNull TransformInvocation invocation) {
        waitableExecutor.execute(
                () -> {
                    ExceptionRunnable fileCreator = createFile(input, output, invocation);
                    if (userCache != null) {
                        FileCache.Inputs key =
                                new FileCache.Inputs.Builder(FileCache.Command.FIX_STACK_FRAMES)
                                        .putFile(
                                                "file",
                                                input,
                                                FileCache.FileProperties.PATH_SIZE_TIMESTAMP)
                                        .build();
                        userCache.createFile(output, key, fileCreator);
                    } else {
                        fileCreator.run();
                    }
                    return null;
                });
    }

    @NonNull
    private ExceptionRunnable createFile(
            @NonNull File input, @NonNull File output, @NonNull TransformInvocation invocation) {
        return () -> {
            try (ZipFile inputZip = new ZipFile(input);
                    ZipOutputStream outputZip =
                            new ZipOutputStream(
                                    new BufferedOutputStream(
                                            Files.newOutputStream(output.toPath())))) {
                Enumeration<? extends ZipEntry> inEntries = inputZip.entries();
                while (inEntries.hasMoreElements()) {
                    ZipEntry entry = inEntries.nextElement();
                    InputStream originalFile =
                            new BufferedInputStream(inputZip.getInputStream(entry));
                    ZipEntry outEntry = new ZipEntry(entry.getName());
                    byte[] newEntryContent;
                    if (!entry.getName().endsWith(SdkConstants.DOT_CLASS)) {
                        // just copy it
                        newEntryContent = ByteStreams.toByteArray(originalFile);
                    } else {
                        newEntryContent = getFixedClass(originalFile, getClassLoader(invocation));
                    }
                    CRC32 crc32 = new CRC32();
                    crc32.update(newEntryContent);
                    outEntry.setCrc(crc32.getValue());
                    outEntry.setMethod(ZipEntry.STORED);
                    outEntry.setSize(newEntryContent.length);
                    outEntry.setCompressedSize(newEntryContent.length);
                    outEntry.setLastAccessTime(ZERO);
                    outEntry.setLastModifiedTime(ZERO);
                    outEntry.setCreationTime(ZERO);

                    outputZip.putNextEntry(outEntry);
                    outputZip.write(newEntryContent);
                    outputZip.closeEntry();
                }
            }
        };
    }

    @NonNull
    private static byte[] getFixedClass(
            @NonNull InputStream originalFile, @NonNull URLClassLoader classLoader)
            throws IOException {
        byte[] bytes = ByteStreams.toByteArray(originalFile);
        try {
            ClassReader classReader = new ClassReader(bytes);
            ClassWriter classWriter = new FixFramesVisitor(ClassWriter.COMPUTE_FRAMES, classLoader);
            classReader.accept(classWriter, ClassReader.SKIP_FRAMES);
            return classWriter.toByteArray();
        } catch (Throwable t) {
            // we could not fix it, just copy the original and log the exception
            logger.verbose(t.getMessage());
            return bytes;
        }
    }
}
