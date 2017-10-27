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

package com.android.build.gradle.shrinker.parser;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.EnumSet;
import org.objectweb.asm.Opcodes;

/** Modifier part of a ProGuard class specification. */
public class ModifierSpecification implements Matcher<ModifierSpecification.MemberModifier> {

    public enum ModifierTarget {
        FIELD,
        METHOD,
        CLASS,
    }

    public static class MemberModifier {

        public final int modifier;
        public final ModifierTarget modifierTarget;

        public MemberModifier(ModifierTarget modifierTarget, int modifier) {
            this.modifier = modifier;
            this.modifierTarget = modifierTarget;
        }
    }

    public enum AccessFlag {
        PUBLIC(Opcodes.ACC_PUBLIC),
        PRIVATE(Opcodes.ACC_PRIVATE),
        PROTECTED(Opcodes.ACC_PROTECTED);

        private final int value;

        AccessFlag(int value) {
            this.value = value;
        }
    }

    public enum Modifier {
        STATIC(Opcodes.ACC_STATIC),
        FINAL(Opcodes.ACC_FINAL),
        SUPER(Opcodes.ACC_SUPER),
        SYNCHRONIZED(Opcodes.ACC_SYNCHRONIZED),
        VOLATILE(Opcodes.ACC_VOLATILE),
        BRIDGE(Opcodes.ACC_BRIDGE),
        TRANSIENT(Opcodes.ACC_TRANSIENT),
        VARARGS(Opcodes.ACC_VARARGS),
        NATIVE(Opcodes.ACC_NATIVE),
        INTERFACE(Opcodes.ACC_INTERFACE),
        ABSTRACT(Opcodes.ACC_ABSTRACT),
        STRICTFP(Opcodes.ACC_STRICT),
        SYNTHETIC(Opcodes.ACC_SYNTHETIC),
        ANNOTATION(Opcodes.ACC_ANNOTATION),
        ENUM(Opcodes.ACC_ENUM);

        private final int value;

        Modifier(int value) {
            this.value = value;
        }
    }

    private static final ImmutableMap<ModifierTarget, EnumSet<Modifier>> MODIFIERS_BY_TYPE =
            ImmutableMap.of(
                    ModifierTarget.FIELD,
                    EnumSet.of(
                            Modifier.STATIC,
                            Modifier.FINAL,
                            Modifier.TRANSIENT,
                            Modifier.VOLATILE,
                            Modifier.ENUM,
                            Modifier.SYNTHETIC),
                    ModifierTarget.METHOD,
                    EnumSet.of(
                            Modifier.STATIC,
                            Modifier.NATIVE,
                            Modifier.ABSTRACT,
                            Modifier.FINAL,
                            Modifier.SYNCHRONIZED,
                            Modifier.BRIDGE,
                            Modifier.SYNTHETIC,
                            Modifier.STRICTFP,
                            Modifier.VARARGS),
                    ModifierTarget.CLASS,
                    EnumSet.of(
                            Modifier.STATIC,
                            Modifier.FINAL,
                            Modifier.ENUM,
                            Modifier.SYNTHETIC,
                            Modifier.ABSTRACT,
                            Modifier.INTERFACE,
                            Modifier.ANNOTATION,
                            Modifier.SUPER,
                            Modifier.STRICTFP));

    @NonNull private final EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);

    @NonNull private final EnumSet<Modifier> modifiersWithNegator = EnumSet.noneOf(Modifier.class);

    @NonNull private final EnumSet<AccessFlag> accessFlags = EnumSet.noneOf(AccessFlag.class);

    @NonNull
    private final EnumSet<AccessFlag> accessFlagsWithNegator = EnumSet.noneOf(AccessFlag.class);

    public void addModifier(Modifier modifier, boolean hasNegator) {
        if (hasNegator) {
            this.modifiersWithNegator.add(modifier);
        } else {
            this.modifiers.add(modifier);
        }
    }

    public void addAccessFlag(AccessFlag accessFlag, boolean hasNegator) {
        if (hasNegator) {
            this.accessFlagsWithNegator.add(accessFlag);
        } else {
            this.accessFlags.add(accessFlag);
        }
    }

    @Nullable
    private static AccessFlag getAccessFlag(int toConvert) {
        for (AccessFlag accFlags : AccessFlag.values()) {
            if ((accFlags.value & toConvert) != 0) {
                return accFlags;
            }
        }
        return null;
    }

    private static EnumSet<Modifier> getModifiers(int bitmask, EnumSet<Modifier> modifiers) {
        EnumSet<Modifier> result = EnumSet.noneOf(Modifier.class);

        for (Modifier modifier : modifiers) {
            if ((modifier.value & bitmask) != 0) {
                result.add(modifier);
            }
        }

        return result;
    }

    @Override
    public boolean matches(@NonNull MemberModifier candidate) {
        // Combining multiple flags is allowed (e.g. public static).
        // It means that both access flags have to be set (e.g. public and static),
        // except when they are conflicting, in which case at least one of them has
        // to be set (e.g. at least public or protected).

        AccessFlag candidateAccFlag = getAccessFlag(candidate.modifier);

        // If the visibility is "package" but the specification isn't,
        // the modifier doesn't match
        if (!accessFlags.isEmpty()) {
            if (!accessFlags.contains(candidateAccFlag)) {
                return false;
            }
        }

        if (accessFlagsWithNegator.contains(candidateAccFlag)) {
            return false;
        }

        EnumSet<Modifier> candidateModifiers =
                getModifiers(candidate.modifier, MODIFIERS_BY_TYPE.get(candidate.modifierTarget));

        return candidateModifiers.containsAll(modifiers)
                && Collections.disjoint(candidateModifiers, modifiersWithNegator);
    }
}
