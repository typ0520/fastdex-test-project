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

package com.android.build.gradle.shrinker;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.shrinker.parser.BytecodeVersion;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

/**
 * {@link ClassVisitor} that skips class members which are not reachable. It also filters the list
 * of implemented interfaces and rewrites generic signatures not to include references to "dropped"
 * types. This is done by replacing the referenced type with java/lang/Object: the same as ProGuard
 * does.
 */
public class RewriteOutputVisitor extends ClassVisitor {
    @NonNull private final Set<String> mMembers;
    @NonNull private final Predicate<String> mClassKeptPredicate;
    @NonNull private final Remapper mRemapper;
    @Nullable private final BytecodeVersion mBytecodeVersion;

    public RewriteOutputVisitor(
            @NonNull Set<String> members,
            @NonNull Predicate<String> classKeptPredicate,
            @Nullable BytecodeVersion bytecodeVersion,
            @NonNull ClassVisitor cv) {
        super(Opcodes.ASM5, cv);

        // Make sure all the information here (i.e. all changes we make to bytecode) are reflected
        // in IncrementalShrinker.State.
        mMembers = members;
        mClassKeptPredicate = classKeptPredicate;
        mBytecodeVersion = bytecodeVersion;
        mRemapper = new ToObjectRemapper();
    }

    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        List<String> interfacesToKeep = Lists.newArrayList();
        for (String iface : interfaces) {
            if (mClassKeptPredicate.test(iface)) {
                interfacesToKeep.add(iface);
            }
        }

        // Check if we want to override the output bytecode version.
        if (mBytecodeVersion != null) {
            version = mBytecodeVersion.getBytes();
        }

        signature = mRemapper.mapSignature(signature, false);

        super.visit(
                version,
                access,
                name,
                signature,
                superName,
                Iterables.toArray(interfacesToKeep, String.class));
    }

    @Override
    public FieldVisitor visitField(
            int access, String name, String desc, String signature, Object value) {
        signature = mRemapper.mapSignature(signature, true);

        if (mMembers.contains(name + ":" + desc)) {
            return super.visitField(access, name, desc, signature, value);
        } else {
            return null;
        }
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        signature = mRemapper.mapSignature(signature, false);

        if (!mMembers.contains(name + ":" + desc)) {
            return null;
        } else {
            return new MethodVisitor(
                    Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {
                @Override
                public void visitLocalVariable(
                        String name,
                        String desc,
                        String signature,
                        Label start,
                        Label end,
                        int index) {
                    signature = mRemapper.mapSignature(signature, true);
                    super.visitLocalVariable(name, desc, signature, start, end, index);
                }
            };
        }
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        // Remove constant pool references to removed classes.
        if (mClassKeptPredicate.test(name)) {
            super.visitInnerClass(name, outerName, innerName, access);
        }
    }

    private class ToObjectRemapper extends Remapper {

        @Override
        public String map(String type) {
            if (mClassKeptPredicate.test(type)) {
                return type;
            } else {
                return "java/lang/Object";
            }
        }
    }
}
