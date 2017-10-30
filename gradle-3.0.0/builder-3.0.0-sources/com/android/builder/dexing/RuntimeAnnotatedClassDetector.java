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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A detector for runtime annotated classes.
 *
 * <p>Used by {@link RuntimeAnnotatedClassCollector}.
 *
 * <p>In legacy multidex, by default all classes annotated with a runtime-retention annotation are
 * kept in the main dex, to avoid issues with reflection.
 *
 * <p>See <a href="http://b.android.com/78144">Issue 78144</a>.
 */
public class RuntimeAnnotatedClassDetector {

    private RuntimeAnnotatedClassDetector() {}

    /**
     * Detects if a given class has runtime visible annotations.
     *
     * @param classDef the bytes of a .class file.
     * @return true if and only if the class has runtime visible annotations
     */
    public static boolean hasRuntimeAnnotations(@NonNull byte[] classDef) {
        HasRuntimeAnnotationsClassVisitor visitor = new HasRuntimeAnnotationsClassVisitor();
        new ClassReader(classDef)
                .accept(
                        visitor,
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return visitor.hasVisibleAnnotation();
    }

    static final class HasRuntimeAnnotationsClassVisitor extends ClassVisitor {
        private boolean hasVisibleAnnotation;

        public HasRuntimeAnnotationsClassVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            hasVisibleAnnotation |= visible;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String desc, String signature, String[] exceptions) {
            if (hasVisibleAnnotation) {
                return null;
            }
            return new MethodVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    hasVisibleAnnotation |= visible;
                    return null;
                }
            };
        }

        @Override
        public FieldVisitor visitField(
                int access, String name, String desc, String signature, Object value) {
            if (hasVisibleAnnotation) {
                return null;
            }
            return new FieldVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    hasVisibleAnnotation |= visible;
                    return null;
                }
            };
        }

        public boolean hasVisibleAnnotation() {
            return hasVisibleAnnotation;
        }
    }
}
