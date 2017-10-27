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
import com.google.common.collect.Lists;
import java.util.List;

/** Represents a ProGuard class specification. */
public class ClassSpecification {

    @NonNull private final List<NameSpecification> nameSpecs;
    @NonNull private final ClassTypeSpecification classType;
    @Nullable private final AnnotationSpecification annotation;
    @Nullable private KeepModifier keepModifier;
    @Nullable private ModifierSpecification modifier;
    @NonNull private List<FieldSpecification> fieldSpecifications = Lists.newArrayList();
    @NonNull private List<MethodSpecification> methodSpecifications = Lists.newArrayList();
    @Nullable private InheritanceSpecification inheritanceSpecification;

    public ClassSpecification(
            @NonNull List<NameSpecification> nameSpecs,
            @NonNull ClassTypeSpecification classType,
            @Nullable AnnotationSpecification annotation) {
        this.nameSpecs = nameSpecs;
        this.classType = classType;
        this.annotation = annotation;
        this.keepModifier = new KeepModifier();
    }

    public void setKeepModifier(@Nullable KeepModifier keepModifier) {
        this.keepModifier = keepModifier;
    }

    @NonNull
    public KeepModifier getKeepModifier() {
        return keepModifier;
    }

    public void setModifier(@Nullable ModifierSpecification modifier) {
        this.modifier = modifier;
    }

    @Nullable
    public ModifierSpecification getModifier() {
        return modifier;
    }

    public void add(FieldSpecification fieldSpecification) {
        fieldSpecifications.add(fieldSpecification);
    }

    public void add(MethodSpecification methodSpecification) {
        methodSpecifications.add(methodSpecification);
    }

    @NonNull
    public List<MethodSpecification> getMethodSpecifications() {
        return methodSpecifications;
    }

    public List<NameSpecification> getNames() {
        return nameSpecs;
    }

    @NonNull
    public ClassTypeSpecification getClassType() {
        return classType;
    }

    @Nullable
    public AnnotationSpecification getAnnotation() {
        return annotation;
    }

    @NonNull
    public List<FieldSpecification> getFieldSpecifications() {
        return fieldSpecifications;
    }

    public void setInheritance(@Nullable InheritanceSpecification inheritanceSpecification) {
        this.inheritanceSpecification = inheritanceSpecification;
    }

    @Nullable
    public InheritanceSpecification getInheritance() {
        return inheritanceSpecification;
    }
}
