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
import org.objectweb.asm.Opcodes;

/**
 * Class representing a ProGuard config file.
 *
 * <p>Mostly copied from Jack.
 */
public class ProguardFlags {

    @NonNull private final List<ClassSpecification> keepClassSpecs = Lists.newArrayList();

    @NonNull
    private final List<ClassSpecification> keepClassesWithMembersSpecs = Lists.newArrayList();

    @NonNull private final List<ClassSpecification> keepClassMembersSpecs = Lists.newArrayList();

    @NonNull private final List<FilterSpecification> dontWarnSpecs = Lists.newArrayList();

    @NonNull private final List<ClassSpecification> whyAreYouKeepingSpecs = Lists.newArrayList();

    @Nullable private BytecodeVersion bytecodeVersion = null;

    private boolean ignoreWarnings;
    private boolean dontShrink;
    private boolean dontObfuscate;
    private boolean dontOptimize;

    @NonNull
    public List<ClassSpecification> getKeepClassSpecs() {
        return keepClassSpecs;
    }

    @NonNull
    public List<ClassSpecification> getKeepClassesWithMembersSpecs() {
        return keepClassesWithMembersSpecs;
    }

    @NonNull
    public List<ClassSpecification> getKeepClassMembersSpecs() {
        return keepClassMembersSpecs;
    }

    public void addKeepClassSpecification(@NonNull ClassSpecification classSpecification) {
        keepClassSpecs.add(classSpecification);
    }

    public void addKeepClassesWithMembers(@NonNull ClassSpecification classSpecification) {
        keepClassesWithMembersSpecs.add(classSpecification);
    }

    public void addKeepClassMembers(@NonNull ClassSpecification classSpecification) {
        keepClassMembersSpecs.add(classSpecification);
    }

    public void whyAreYouKeeping(@NonNull ClassSpecification classSpecification) {
        whyAreYouKeepingSpecs.add(classSpecification);
    }

    public void dontWarn(@NonNull List<FilterSpecification> classSpec) {
        dontWarnSpecs.addAll(classSpec);
    }

    public void target(@NonNull String target) {
        int version;
        switch (target) {
            case "8":
            case "1.8":
                version = Opcodes.V1_8;
                break;
            case "7":
            case "1.7":
                version = Opcodes.V1_7;
                break;
            case "6":
            case "1.6":
                version = Opcodes.V1_6;
                break;
            case "5":
            case "1.5":
                version = Opcodes.V1_5;
                break;
            case "1.4":
                version = Opcodes.V1_4;
                break;
            case "1.3":
                version = Opcodes.V1_3;
                break;
            case "1.2":
                version = Opcodes.V1_2;
                break;
            case "1.1":
                version = Opcodes.V1_1;
                break;
            default:
                throw new AssertionError("Unknown target " + target);
        }

        this.bytecodeVersion = new BytecodeVersion(version);
    }

    @NonNull
    public List<FilterSpecification> getDontWarnSpecs() {
        return dontWarnSpecs;
    }

    @NonNull
    public List<ClassSpecification> getWhyAreYouKeepingSpecs() {
        return whyAreYouKeepingSpecs;
    }

    public void setIgnoreWarnings(boolean ignoreWarnings) {
        this.ignoreWarnings = ignoreWarnings;
    }

    public boolean isIgnoreWarnings() {
        return ignoreWarnings;
    }

    @Nullable
    public BytecodeVersion getBytecodeVersion() {
        return bytecodeVersion;
    }

    public boolean isDontShrink() {
        return dontShrink;
    }

    public void setDontShrink(boolean dontShrink) {
        this.dontShrink = dontShrink;
    }

    public boolean isDontObfuscate() {
        return dontObfuscate;
    }

    public void setDontObfuscate(boolean dontObfuscate) {
        this.dontObfuscate = dontObfuscate;
    }

    public boolean isDontOptimize() {
        return dontOptimize;
    }

    public void setDontOptimize(boolean dontOptimize) {
        this.dontOptimize = dontOptimize;
    }
}
