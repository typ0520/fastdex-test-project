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
import com.android.dex.Dex;
import com.android.dex.DexFormat;
import com.android.dex.FieldId;
import com.android.dex.MethodId;
import com.android.dex.ProtoId;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dex merging strategy that tracks field and method references that can be merged. This will
 * account for duplicate references from different DEX files, and will count those as a single
 * reference.
 */
public class ReferenceCountMergingStrategy implements DexMergingStrategy {

    @NonNull private final Set<FieldEvaluated> fieldRefs = Sets.newHashSet();
    @NonNull private final Set<MethodEvaluated> methodRefs = Sets.newHashSet();
    @NonNull private final List<Dex> currentDexes = Lists.newArrayList();

    @Override
    public boolean tryToAddForMerging(@NonNull Dex dexFile) {
        if (tryAddFields(dexFile) && tryAddMethods(dexFile)) {
            currentDexes.add(dexFile);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void startNewDex() {
        fieldRefs.clear();
        methodRefs.clear();
        currentDexes.clear();
    }

    @NonNull
    @Override
    public ImmutableList<Dex> getAllDexToMerge() {
        return ImmutableList.copyOf(currentDexes);
    }

    private boolean tryAddFields(@NonNull Dex dexFile) {
        List<FieldId> fieldIds = dexFile.fieldIds();
        Set<FieldEvaluated> fieldsEvaluated = new HashSet<>(fieldIds.size());
        fieldIds.forEach(f -> fieldsEvaluated.add(FieldEvaluated.create(f, dexFile)));

        // find how many references are shared, and deduct from the total count
        int shared = Sets.intersection(fieldsEvaluated, fieldRefs).size();
        if (fieldRefs.size() + fieldsEvaluated.size() - shared > DexFormat.MAX_MEMBER_IDX + 1) {
            return false;
        } else {
            fieldRefs.addAll(fieldsEvaluated);
            return true;
        }
    }

    private boolean tryAddMethods(@NonNull Dex dexFile) {
        List<MethodId> methodIds = dexFile.methodIds();
        Set<MethodEvaluated> methodsEvaluated = new HashSet<>(methodIds.size());
        methodIds.forEach(f -> methodsEvaluated.add(MethodEvaluated.create(f, dexFile)));

        // find how many references are shared, and deduct from the total count
        int shared = Sets.intersection(methodsEvaluated, methodRefs).size();
        if (methodRefs.size() + methodsEvaluated.size() - shared > DexFormat.MAX_MEMBER_IDX + 1) {
            return false;
        } else {
            methodRefs.addAll(methodsEvaluated);
            return true;
        }
    }

    @AutoValue
    abstract static class FieldEvaluated {

        @NonNull
        public static FieldEvaluated create(@NonNull FieldId fieldId, @NonNull Dex dex) {
            return new AutoValue_ReferenceCountMergingStrategy_FieldEvaluated(
                    dex.typeNames().get(fieldId.getDeclaringClassIndex()),
                    dex.typeNames().get(fieldId.getTypeIndex()),
                    dex.strings().get(fieldId.getNameIndex()));
        }

        @NonNull
        abstract String declaringClass();

        @NonNull
        abstract String type();

        @NonNull
        abstract String name();
    }

    @AutoValue
    abstract static class MethodEvaluated {

        @NonNull
        public static MethodEvaluated create(@NonNull MethodId methodId, @NonNull Dex dex) {
            String declaringClass = dex.typeNames().get(methodId.getDeclaringClassIndex());
            String name = dex.strings().get(methodId.getNameIndex());

            ProtoId protoId = dex.protoIds().get(methodId.getProtoIndex());
            String protoShorty = dex.strings().get(protoId.getShortyIndex());
            String protoReturnType = dex.typeNames().get(protoId.getReturnTypeIndex());
            String protoParameterTypes = dex.readTypeList(protoId.getParametersOffset()).toString();
            return new AutoValue_ReferenceCountMergingStrategy_MethodEvaluated(
                    declaringClass, name, protoShorty, protoReturnType, protoParameterTypes);
        }

        @NonNull
        abstract String declaringClass();

        @NonNull
        abstract String name();

        @NonNull
        abstract String protoShorty();

        @NonNull
        abstract String protoReturnType();

        @NonNull
        abstract String protoParameterTypes();
    }
}
