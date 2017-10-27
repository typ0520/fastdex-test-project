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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

/**
 * ASM related utilities methods.
 */
public class AsmUtils {

    /**
     * Abstraction for a provider for {@link ClassReader} instances for a class name.
     */
    public interface ClassReaderProvider {

        /**
         * load class bytes and initialize a {@link ClassReader} with it.
         * @param className the requested class to be loaded.
         * @param logger to log messages.
         * @return a {@link ClassReader} with the class' bytes or null if the class file cannot
         * be located.
         * @throws IOException when locating/reading the class file.
         */
        @Nullable
        ClassReader loadClassBytes(@NonNull String className, @NonNull ILogger logger)
                throws IOException;
    }

    public static class DirectoryBasedClassReader implements ClassReaderProvider {

        private final File binaryFolder;

        public DirectoryBasedClassReader(File binaryFolder) {
            this.binaryFolder = binaryFolder;
        }

        @Override
        @Nullable
        public ClassReader loadClassBytes(@NonNull String className, @NonNull ILogger logger) {
            File outerClassFile = new File(binaryFolder, className + ".class");
            if (outerClassFile.exists()) {
                logger.verbose("Parsing %s", outerClassFile);
                try(InputStream outerClassInputStream =
                            new BufferedInputStream(new FileInputStream(outerClassFile))) {
                    return new ClassReader(outerClassInputStream);
                } catch (IOException e) {
                    logger.error(e, "Cannot parse %s", className);
                }
            }
            return null;
        }
    }

    public static class JarBasedClassReader implements ClassReaderProvider {

        private final File file;

        public JarBasedClassReader(File file) {
            this.file = file;
        }

        @Nullable
        @Override
        public ClassReader loadClassBytes(@NonNull String className, @NonNull ILogger logger)
                throws IOException {
            try (JarFile jarFile = new JarFile(file)) {
                ZipEntry entry = jarFile.getEntry(className.replace(".", "/") + ".class");
                if (entry != null) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        return new ClassReader(is);
                    }
                }
            }
            return null;
        }
    }

    @NonNull
    @VisibleForTesting
    public static List<AnnotationNode> getInvisibleAnnotationsOnClassOrOuterClasses(
            @NonNull ClassReaderProvider classReader,
            @NonNull ClassNode classNode,
            @NonNull ILogger logger) throws IOException {

        ImmutableList.Builder<AnnotationNode> listBuilder = ImmutableList.builder();
        do {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> invisibleAnnotations = classNode.invisibleAnnotations;
            if (invisibleAnnotations != null) {
                listBuilder.addAll(invisibleAnnotations);
            }
            String outerClassName = getOuterClassName(classNode);
            classNode = outerClassName != null
                    ? readClass(classReader, outerClassName, logger)
                    : null;
        } while (classNode != null);
        return listBuilder.build();
    }

    @Nullable
    public static ClassNode readClass(@NonNull ClassReaderProvider classReaderProvider,
            @NonNull String className, @NonNull ILogger logger) throws IOException {
        ClassReader classReader = classReaderProvider.loadClassBytes(className, logger);
        return classReader != null ? readClass(classReader) : null;
    }

    @NonNull
    public static ClassNode readClass(@NonNull ClassReader classReader) {
        ClassNode node = new ClassNode();
        classReader.accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    @NonNull
    public static List<ClassNode> parseParents(
            @NonNull ILogger logger,
            @NonNull ClassReaderProvider classBytesReader,
            @NonNull ClassNode classNode,
            int targetApi) throws IOException {
        List<ClassNode> parentNodes = new ArrayList<>();

        String currentParentName = classNode.superName;

        while (currentParentName != null) {
            ClassNode parentNode = readClass(classBytesReader, currentParentName, logger);
            if (parentNode != null) {
                parentNodes.add(parentNode);
                currentParentName = parentNode.superName;
            } else {
                // May need method information from outside of the current project. The thread's
                // context class loader is configured by the caller (InstantRunTransform) to contain
                // all app's dependencies.
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    parentNode = readClass(contextClassLoader, currentParentName);
                    parentNodes.add(parentNode);
                    currentParentName = parentNode.superName;
                } catch (IOException e) {
                    // Could not locate parent class. This is as far as we can go locating parents.
                    logger.warning(e.getMessage());
                    logger.warning("IncrementalVisitor parseParents could not locate %1$s "
                                    + "which is an ancestor of project class %2$s.\n"
                                    + "%2$s is not eligible for hot swap. \n"
                                    + "If the class targets a more recent platform than %3$d,"
                                    + " add a @TargetApi annotation to silence this warning.",
                            currentParentName, classNode.name, targetApi);
                    return ImmutableList.of();
                }
            }
        }
        return parentNodes;
    }

    @NonNull
    public static ClassNode readClass(ClassLoader classLoader, String className)
            throws IOException {
       try (InputStream is = classLoader.getResourceAsStream(className + ".class")) {
           if (is == null) {
               throw new IOException("Failed to find byte code for " + className);
           }

           ClassReader parentClassReader = new ClassReader(is);
           ClassNode node = new ClassNode();
           parentClassReader.accept(node, ClassReader.EXPAND_FRAMES);
           return node;
        }
    }

    @Nullable
    public static ClassNode parsePackageInfo(
            @NonNull File inputFile) throws IOException {

        File packageFolder = inputFile.getParentFile();
        File packageInfoClass = new File(packageFolder, "package-info.class");
        if (packageInfoClass.exists()) {
            try (InputStream reader = new BufferedInputStream(new FileInputStream(packageInfoClass))) {
                ClassReader classReader = new ClassReader(reader);
                return readClass(classReader);
            }
        }
        return null;
    }

    @Nullable
    public static String getOuterClassName(@NonNull ClassNode classNode) {
        if (classNode.outerClass != null) {
            return classNode.outerClass;
        }
        if (classNode.innerClasses != null) {
            @SuppressWarnings("unchecked")
            List<InnerClassNode> innerClassNodes = (List<InnerClassNode>) classNode.innerClasses;
            for (InnerClassNode innerClassNode : innerClassNodes) {
                if (innerClassNode.name.equals(classNode.name)) {
                    return innerClassNode.outerName;
                }
            }
        }
        return null;
    }
}
