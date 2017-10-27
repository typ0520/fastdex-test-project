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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class IncrementalVisitor extends ClassVisitor {

    /**
     * Defines the output type from this visitor.
     */
    public enum OutputType {
        /**
         * provide instrumented classes that can be hot swapped at runtime with an override class.
         */
        INSTRUMENT,
        /**
         * provide override classes that be be used to hot swap an instrumented class.
         */
        OVERRIDE
    }

    public static final String RUNTIME_PACKAGE = "com/android/tools/ir/runtime";
    public static final String ABSTRACT_PATCHES_LOADER_IMPL =
            RUNTIME_PACKAGE + "/AbstractPatchesLoaderImpl";
    public static final String APP_PATCHES_LOADER_IMPL = RUNTIME_PACKAGE + "/AppPatchesLoaderImpl";

    protected static final Type INSTANT_RELOAD_EXCEPTION =
            Type.getObjectType(RUNTIME_PACKAGE + "/InstantReloadException");
    protected static final Type RUNTIME_TYPE =
            Type.getObjectType(RUNTIME_PACKAGE + "/AndroidInstantRuntime");
    public static final Type DISABLE_ANNOTATION_TYPE =
            Type.getObjectType("com/android/tools/ir/api/DisableInstantRun");
    public static final Type TARGET_API_TYPE =
            Type.getObjectType("android/annotation/TargetApi");

    protected static final boolean TRACING_ENABLED = Boolean.getBoolean("FDR_TRACING");

    public static final Type CHANGE_TYPE = Type.getObjectType(RUNTIME_PACKAGE + "/IncrementalChange");

    protected String visitedClassName;
    protected String visitedSuperName;
    @NonNull
    protected final ClassNode classNode;
    @NonNull
    protected final List<ClassNode> parentNodes;
    @NonNull
    protected final ILogger logger;

    /**
     * Enumeration describing a method of field access rights.
     */
    protected enum AccessRight {
        PRIVATE, PACKAGE_PRIVATE, PROTECTED, PUBLIC;

        @NonNull
        static AccessRight fromNodeAccess(int nodeAccess) {
            if ((nodeAccess & Opcodes.ACC_PRIVATE) != 0) return PRIVATE;
            if ((nodeAccess & Opcodes.ACC_PROTECTED) != 0) return PROTECTED;
            if ((nodeAccess & Opcodes.ACC_PUBLIC) != 0) return PUBLIC;
            return PACKAGE_PRIVATE;
        }
    }

    public IncrementalVisitor(
            @NonNull ClassNode classNode,
            @NonNull List<ClassNode> parentNodes,
            @NonNull ClassVisitor classVisitor,
            @NonNull ILogger logger) {
        super(Opcodes.ASM5, classVisitor);
        this.classNode = classNode;
        this.parentNodes = parentNodes;
        this.logger = logger;
        this.logger.verbose("%s: Visiting %s", getClass().getSimpleName(), classNode.name);
    }

    @NonNull
    protected static String getRuntimeTypeName(@NonNull Type type) {
        return "L" + type.getInternalName() + ";";
    }

    @Nullable
    FieldNode getFieldByName(@NonNull String fieldName) {
        FieldNode fieldNode = getFieldByNameInClass(fieldName, classNode);
        Iterator<ClassNode> iterator = parentNodes.iterator();
        while(fieldNode == null && iterator.hasNext()) {
            ClassNode parentNode = iterator.next();
            fieldNode = getFieldByNameInClass(fieldName, parentNode);
        }
        return fieldNode;
    }

    @Nullable
    protected static FieldNode getFieldByNameInClass(
            @NonNull String fieldName, @NonNull ClassNode classNode) {
        //noinspection unchecked ASM api.
        List<FieldNode> fields = classNode.fields;
        for (FieldNode field: fields) {
            if (field.name.equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    @Nullable
    protected MethodNode getMethodByName(String methodName, String desc) {
        MethodNode methodNode = getMethodByNameInClass(methodName, desc, classNode);
        Iterator<ClassNode> iterator = parentNodes.iterator();
        while(methodNode == null && iterator.hasNext()) {
            ClassNode parentNode = iterator.next();
            methodNode = getMethodByNameInClass(methodName, desc, parentNode);
        }
        return methodNode;
    }

    @Nullable
    protected static MethodNode getMethodByNameInClass(String methodName, String desc, ClassNode classNode) {
        //noinspection unchecked ASM API
        List<MethodNode> methods = classNode.methods;
        for (MethodNode method : methods) {
            if (method.name.equals(methodName) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    protected static void trace(@NonNull GeneratorAdapter mv, @Nullable String s) {
        mv.push(s);
        mv.invokeStatic(Type.getObjectType(RUNTIME_PACKAGE + "/AndroidInstantRuntime"),
                Method.getMethod("void trace(String)"));
    }

    @SuppressWarnings("unused")
    protected static void trace(@NonNull GeneratorAdapter mv, @Nullable String s1,
            @Nullable String s2) {
        mv.push(s1);
        mv.push(s2);
        mv.invokeStatic(Type.getObjectType(RUNTIME_PACKAGE + "/AndroidInstantRuntime"),
                Method.getMethod("void trace(String, String)"));
    }

    @SuppressWarnings("unused")
    protected static void trace(@NonNull GeneratorAdapter mv, @Nullable String s1,
            @Nullable String s2, @Nullable String s3) {
        mv.push(s1);
        mv.push(s2);
        mv.push(s3);
        mv.invokeStatic(Type.getObjectType(RUNTIME_PACKAGE + "/AndroidInstantRuntime"),
                Method.getMethod("void trace(String, String, String)"));
    }

    protected static void trace(@NonNull GeneratorAdapter mv, @Nullable String s1,
            @Nullable String s2, @Nullable String s3, @Nullable String s4) {
        mv.push(s1);
        mv.push(s2);
        mv.push(s3);
        mv.push(s4);
        mv.invokeStatic(Type.getObjectType(RUNTIME_PACKAGE + "/AndroidInstantRuntime"),
                Method.getMethod("void trace(String, String, String, String)"));
    }

    protected static void trace(@NonNull GeneratorAdapter mv, int argsNumber) {
        StringBuilder methodSignature = new StringBuilder("void trace(String");
        for (int i=0 ; i < argsNumber-1; i++) {
            methodSignature.append(", String");
        }
        methodSignature.append(")");
        mv.invokeStatic(Type.getObjectType(RUNTIME_PACKAGE + "/AndroidInstantRuntime"),
                Method.getMethod(methodSignature.toString()));
    }

    /**
     * Simple Builder interface for common methods between all byte code visitors.
     */
    public interface VisitorBuilder {
        @NonNull
        IncrementalVisitor build(
                @NonNull ClassNode classNode,
                @NonNull List<ClassNode> parentNodes,
                @NonNull ClassVisitor classVisitor,
                @NonNull ILogger logger);

        @NonNull
        String getMangledRelativeClassFilePath(@NonNull String originalClassFilePath);

        @NonNull
        OutputType getOutputType();
    }

    /**
     * Defines when a method access flags are compatible with InstantRun technology.
     *
     * - If the method is a bridge method, we do not enable it for instantReload.
     *   it is most likely only calling a twin method (same name, same parameters).
     * - if the method is abstract or native, we don't add a redirection.
     *
     * @param access the method access flags
     * @return true if the method should be InstantRun enabled, false otherwise.
     */
    protected static boolean isAccessCompatibleWithInstantRun(int access) {
        return (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_BRIDGE | Opcodes.ACC_NATIVE)) == 0;
    }

    @Nullable
    public static File instrumentClass(
            int targetApiLevel,
            @NonNull File inputRootDirectory,
            @NonNull File inputFile,
            @NonNull File outputDirectory,
            @NonNull VisitorBuilder visitorBuilder,
            @NonNull ILogger logger) throws IOException {

        byte[] classBytes;
        String path = FileUtils.relativePath(inputFile, inputRootDirectory);

        // if the class is not eligible for IR, return the non instrumented version or null if
        // the override class is requested.
        if (!isClassEligibleForInstantRun(inputFile)) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                File outputFile = new File(outputDirectory, path);
                Files.createParentDirs(outputFile);
                Files.copy(inputFile, outputFile);
                return outputFile;
            } else {
                return null;
            }
        }
        classBytes = Files.toByteArray(inputFile);
        ClassReader classReader = new ClassReader(classBytes);
        // override the getCommonSuperClass to use the thread context class loader instead of
        // the system classloader. This is useful as ASM needs to load classes from the project
        // which the system classloader does not have visibility upon.
        // TODO: investigate if there is not a simpler way than overriding.
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                Class<?> c, d;
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                try {
                    c = Class.forName(type1.replace('/', '.'), false, classLoader);
                    d = Class.forName(type2.replace('/', '.'), false, classLoader);
                } catch (ClassNotFoundException e) {
                    // This may happen if we're processing class files which reference APIs not
                    // available on the target device. In this case return a dummy value, since this
                    // is ignored during dx compilation.
                    return "instant/run/NoCommonSuperClass";
                } catch (Exception e) {
                    throw new RuntimeException(e);
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
        };

        ClassNode classNode =  AsmUtils.readClass(classReader);

        // when dealing with interface, we just copy the inputFile over without any changes unless
        // this is a package private interface.
        AccessRight accessRight = AccessRight.fromNodeAccess(classNode.access);
        File outputFile = new File(outputDirectory, path);
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                // don't change the name of interfaces.
                Files.createParentDirs(outputFile);
                if (accessRight == AccessRight.PACKAGE_PRIVATE) {
                    classNode.access = classNode.access | Opcodes.ACC_PUBLIC;
                    classNode.accept(classWriter);
                    Files.write(classWriter.toByteArray(), outputFile);
                } else {
                    // just copy the input file over, no change.
                    Files.write(classBytes, outputFile);
                }
                return outputFile;
            } else {
                return null;
            }
        }

        AsmUtils.DirectoryBasedClassReader directoryClassReader =
                new AsmUtils.DirectoryBasedClassReader(getBinaryFolder(inputFile, classNode));

        // if we are targeting a more recent version than the current device, disable instant run
        // for that class.
        List<ClassNode> parentsNodes =
                isClassTargetingNewerPlatform(
                        targetApiLevel, TARGET_API_TYPE, directoryClassReader, classNode, logger)
                        ? ImmutableList.of()
                        : AsmUtils.parseParents(
                                logger, directoryClassReader, classNode, targetApiLevel);
        // if we could not determine the parent hierarchy, disable instant run.
        if (parentsNodes.isEmpty() || isPackageInstantRunDisabled(inputFile)) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                Files.createParentDirs(outputFile);
                Files.write(classBytes, outputFile);
                return outputFile;
            } else {
                return null;
            }
        }

        outputFile = new File(outputDirectory, visitorBuilder.getMangledRelativeClassFilePath(path));
        Files.createParentDirs(outputFile);
        IncrementalVisitor visitor =
                visitorBuilder.build(classNode, parentsNodes, classWriter, logger);

        if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
            /*
             * Classes that do not have a serial version unique identifier, will be updated to
             * contain one. This is accomplished by using the {@link SerialVersionUIDAdder} class
             * visitor that is added when this visitor is created (see the constructor). This way,
             * the serialVersionUID is the same for instrumented and non-instrumented classes. All
             * classes will have a serialVersionUID, so if some of the classes that is extended
             * starts implementing {@link java.io.Serializable}, serialization and deserialization
             * will continue to work correctly.
             */
            classNode.accept(new SerialVersionUIDAdder(visitor));
        } else {
            classNode.accept(visitor);
        }

        Files.write(classWriter.toByteArray(), outputFile);
        return outputFile;
    }

    @NonNull
    private static File getBinaryFolder(@NonNull File inputFile, @NonNull ClassNode classNode) {
        return new File(inputFile.getAbsolutePath().substring(0,
                inputFile.getAbsolutePath().length() - (classNode.name.length() + ".class".length())));
    }

    /**
     * If the passed class is annotated with an annotation type that denotes the target
     * api level like {@link #TARGET_API_TYPE}, and will return true if the passed targetApiLevel
     * is lower than the value of the annotation.
     * @param targetApiLevel the target api level we are instrumenting against.
     * @param targetApiAnnotationType the type of the annotation to look for
     * @param locator a class locator implementation that can locate and load outclasses if any
     * @param classNode the class of interest
     * @param logger to log messages
     * @return true if the class of interest is annotated with targetApiAnnotationType and the
     * annotation value is superior to the passed targetApiLevel.
     * @throws IOException when outer classes cannot be loaded successfully.
     */
    @VisibleForTesting
    static boolean isClassTargetingNewerPlatform(
            int targetApiLevel,
            @NonNull Type targetApiAnnotationType,
            @NonNull AsmUtils.ClassReaderProvider locator,
            @NonNull ClassNode classNode,
            @NonNull ILogger logger) throws IOException {

        List<AnnotationNode> invisibleAnnotations =
                AsmUtils.getInvisibleAnnotationsOnClassOrOuterClasses(locator, classNode, logger);
        for (AnnotationNode classAnnotation : invisibleAnnotations) {
            if (classAnnotation.desc.equals(targetApiAnnotationType.getDescriptor())) {
                int valueIndex = 0;
                List values = classAnnotation.values;
                while (valueIndex < values.size()) {
                    String name = (String) values.get(valueIndex);
                    if (name.equals("value")) {
                        Object value = values.get(valueIndex + 1);
                        return Integer.class.cast(value) > targetApiLevel;
                    }
                    valueIndex = valueIndex + 2;
                }
            }
        }
        return false;
    }

    private static boolean isPackageInstantRunDisabled(@NonNull File inputFile) throws IOException {

        ClassNode packageInfoClass =  AsmUtils.parsePackageInfo(inputFile);
        if (packageInfoClass != null) {
            //noinspection unchecked
            List<AnnotationNode> annotations = packageInfoClass.invisibleAnnotations;
            if (annotations == null) {
                return false;
            }
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return true of the class is eligible to be InstantRun enabled, false otherwise.
     * @param inputFile the input file containing the byte codes.
     * @return true if the class should be instrumented for InstantRun, false otherwise.
     */
    @VisibleForTesting
    static boolean isClassEligibleForInstantRun(@NonNull File inputFile) {

        if (inputFile.getPath().endsWith(SdkConstants.DOT_CLASS)) {
            String fileName = inputFile.getName();
            return !fileName.equals("R" + SdkConstants.DOT_CLASS) && !fileName.startsWith("R$");
        } else {
            return false;
        }
    }
}
