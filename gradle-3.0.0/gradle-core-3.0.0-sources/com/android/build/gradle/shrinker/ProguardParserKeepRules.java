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
import com.android.build.gradle.shrinker.parser.AnnotationSpecification;
import com.android.build.gradle.shrinker.parser.ClassSpecification;
import com.android.build.gradle.shrinker.parser.FieldSpecification;
import com.android.build.gradle.shrinker.parser.InheritanceSpecification;
import com.android.build.gradle.shrinker.parser.Matcher;
import com.android.build.gradle.shrinker.parser.MethodSpecification;
import com.android.build.gradle.shrinker.parser.ModifierSpecification;
import com.android.build.gradle.shrinker.parser.ModifierSpecification.ModifierTarget;
import com.android.build.gradle.shrinker.parser.NameSpecification;
import com.android.build.gradle.shrinker.parser.ProguardFlags;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link KeepRules} that uses {@link ProguardFlags} obtained from parsing a
 * ProGuard config file.
 */
public class ProguardParserKeepRules implements KeepRules {

    @NonNull private final List<ClassSpecification> keepClassSpecs;
    @NonNull private final List<ClassSpecification> keepClassMembersSpecs;
    @NonNull private final List<ClassSpecification> keepClassesWithMembersSpecs;
    @NonNull private final ShrinkerLogger shrinkerLogger;

    private ProguardParserKeepRules(
            @NonNull List<ClassSpecification> keepClassSpecs,
            @NonNull List<ClassSpecification> keepClassMembersSpecs,
            @NonNull List<ClassSpecification> keepClassesWithMembersSpecs,
            @NonNull ShrinkerLogger shrinkerLogger) {
        this.keepClassSpecs = keepClassSpecs;
        this.keepClassMembersSpecs = keepClassMembersSpecs;
        this.keepClassesWithMembersSpecs = keepClassesWithMembersSpecs;
        this.shrinkerLogger = shrinkerLogger;
    }

    @NonNull
    public static ProguardParserKeepRules keepRules(
            @NonNull ProguardFlags flags, @NonNull ShrinkerLogger shrinkerLogger) {
        return new ProguardParserKeepRules(
                flags.getKeepClassSpecs(),
                flags.getKeepClassMembersSpecs(),
                flags.getKeepClassesWithMembersSpecs(),
                shrinkerLogger);
    }

    @Nullable
    public static ProguardParserKeepRules whyAreYouKeepingRules(
            @NonNull ProguardFlags flags, @NonNull ShrinkerLogger shrinkerLogger) {
        if (flags.getWhyAreYouKeepingSpecs().isEmpty()) {
            return null;
        } else {
            return new ProguardParserKeepRules(
                    flags.getWhyAreYouKeepingSpecs(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    shrinkerLogger);
        }
    }

    @Override
    public <T> Map<T, DependencyType> getSymbolsToKeep(T klass, ShrinkerGraph<T> graph) {
        Map<T, DependencyType> result = Maps.newHashMap();

        for (ClassSpecification spec : keepClassSpecs) {
            if (matchesClass(klass, spec, graph)) {
                result.put(klass, DependencyType.REQUIRED_KEEP_RULES);
                result.put(
                        graph.getMemberReference(graph.getClassName(klass), "<init>", "()V"),
                        DependencyType.REQUIRED_CLASS_STRUCTURE);
                for (T member : findMatchingMembers(klass, spec, graph)) {
                    result.put(member, DependencyType.REQUIRED_KEEP_RULES);
                }
            }
        }

        for (ClassSpecification spec : keepClassMembersSpecs) {
            if (matchesClass(klass, spec, graph)) {
                for (T member : findMatchingMembers(klass, spec, graph)) {
                    result.put(member, DependencyType.IF_CLASS_KEPT);
                    graph.addDependency(klass, member, DependencyType.CLASS_IS_KEPT);
                }
            }
        }

        for (ClassSpecification spec : keepClassesWithMembersSpecs) {
            if (matchesClass(klass, spec, graph)) {
                for (T t : handleKeepClassesWithMembers(spec, klass, graph)) {
                    result.put(t, DependencyType.REQUIRED_KEEP_RULES);
                }
            }
        }

        return result;
    }

    private static <T> List<T> handleKeepClassesWithMembers(
            ClassSpecification classSpec, T klass, ShrinkerGraph<T> graph) {
        List<T> result = Lists.newArrayList();

        for (MethodSpecification methodSpec : classSpec.getMethodSpecifications()) {
            boolean found = false;
            for (T method : graph.getMethods(klass)) {
                if (matchesMethod(method, methodSpec, graph)) {
                    found = true;
                    result.add(method);
                }
            }

            if (!found) {
                return Collections.emptyList();
            }
        }

        for (FieldSpecification fieldSpec : classSpec.getFieldSpecifications()) {
            boolean found = false;
            for (T field : graph.getFields(klass)) {
                if (matchesField(field, fieldSpec, graph)) {
                    found = true;
                    result.add(field);
                }
            }

            if (!found) {
                return Collections.emptyList();
            }
        }

        // If we're here, then all member specs have matched something.
        result.add(klass);
        return result;
    }

    private static <T> List<T> findMatchingMembers(
            T klass, ClassSpecification spec, ShrinkerGraph<T> graph) {
        List<T> result = Lists.newArrayList();
        for (T method : graph.getMethods(klass)) {
            for (MethodSpecification methodSpec : spec.getMethodSpecifications()) {
                if (matchesMethod(method, methodSpec, graph)) {
                    result.add(method);
                }
            }
        }

        for (T field : graph.getFields(klass)) {
            for (FieldSpecification fieldSpecification : spec.getFieldSpecifications()) {
                if (matchesField(field, fieldSpecification, graph)) {
                    result.add(field);
                }
            }
        }

        return result;
    }

    private static <T> boolean matchesField(
            T field, FieldSpecification spec, ShrinkerGraph<T> graph) {
        return matches(spec.getName(), graph.getMemberName(field))
                && matches(spec.getModifier(), graph.getModifiers(field), ModifierTarget.FIELD)
                && matches(spec.getTypeSignature(), graph.getMemberDescriptor(field))
                && matchesAnnotations(field, spec.getAnnotations(), graph);
    }

    private static <T> boolean matchesMethod(
            T method, MethodSpecification spec, ShrinkerGraph<T> graph) {
        String nameAndDescriptor =
                graph.getMemberName(method) + ":" + graph.getMemberDescriptor(method);
        return matches(spec.getName(), nameAndDescriptor)
                && matches(spec.getModifiers(), graph.getModifiers(method), ModifierTarget.METHOD)
                && matchesAnnotations(method, spec.getAnnotations(), graph);
    }

    private <T> boolean matchesClass(T klass, ClassSpecification spec, ShrinkerGraph<T> graph) {
        int classModifiers = graph.getModifiers(klass);
        return matchesClassName(spec.getNames(), graph.getClassName(klass))
                && matches(spec.getClassType(), classModifiers)
                && matches(spec.getModifier(), classModifiers, ModifierTarget.CLASS)
                && matchesAnnotations(klass, spec.getAnnotation(), graph)
                && matchesInheritance(klass, spec.getInheritance(), graph);
    }

    private static boolean matchesClassName(List<NameSpecification> specs, String className) {
        return specs.stream().anyMatch(s -> s.matches(className));
    }

    private static boolean matches(
            @Nullable ModifierSpecification spec, int modifiers, @NonNull ModifierTarget target) {
        return spec == null
                || spec.matches(new ModifierSpecification.MemberModifier(target, modifiers));
    }

    private static <U> boolean matches(@Nullable Matcher<U> matcher, @NonNull U value) {
        return matcher == null || matcher.matches(value);
    }

    private static <T> boolean matchesAnnotations(
            @NonNull T classOrMember,
            @Nullable AnnotationSpecification annotation,
            @NonNull ShrinkerGraph<T> graph) {
        if (annotation == null) {
            return true;
        }

        for (String annotationName : graph.getAnnotations(classOrMember)) {
            if (annotation.getName().matches(annotationName)) {
                return true;
            }
        }

        return false;
    }

    private <T> boolean matchesInheritance(
            @NonNull T klass,
            @Nullable InheritanceSpecification spec,
            @NonNull ShrinkerGraph<T> graph) {
        if (spec == null) {
            return true;
        }

        FluentIterable<T> superTypes =
                TypeHierarchyTraverser.superclassesAndInterfaces(graph, shrinkerLogger)
                        .preOrderTraversal(klass)
                        .skip(1); // Skip the class itself.

        for (T superType : superTypes) {
            String name = graph.getClassName(superType);
            if (spec.getNameSpec().matches(name)) {
                return true;
            }
        }
        return false;
    }
}
