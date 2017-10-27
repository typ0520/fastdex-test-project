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

package com.android.build.gradle.internal.profile;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.wireless.android.sdk.stats.ApiVersion;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.google.wireless.android.sdk.stats.GradleBuildSplits;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.util.Locale;

/**
 * Utilities to map internal representations of types to analytics.
 */
public class AnalyticsUtil {

    public static GradleTransformExecutionType getTransformType(
            @NonNull Class<? extends Transform> taskClass) {
        try {
            return GradleTransformExecutionType.valueOf(getPotentialTransformTypeName(taskClass));
        } catch (IllegalArgumentException ignored) {
            return GradleTransformExecutionType.UNKNOWN_TRANSFORM_TYPE;
        }
    }

    @VisibleForTesting
    @NonNull
    static String getPotentialTransformTypeName(Class<?> taskClass) {
        String taskImpl = taskClass.getSimpleName();
        if (taskImpl.endsWith("Transform")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "Transform".length());
        }
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, taskImpl);
    }


    @NonNull
    public static GradleTaskExecutionType getTaskExecutionType(@NonNull Class<?> taskClass) {
        try {
            return GradleTaskExecutionType.valueOf(getPotentialTaskExecutionTypeName(taskClass));
        } catch (IllegalArgumentException ignored) {
            return GradleTaskExecutionType.UNKNOWN_TASK_TYPE;
        }
    }

    @VisibleForTesting
    @NonNull
    static String getPotentialTaskExecutionTypeName(Class<?> taskClass) {
        String taskImpl = taskClass.getSimpleName();
        if (taskImpl.endsWith("_Decorated")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "_Decorated".length());
        }
        if (taskImpl.endsWith("Task")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "Task".length());
        }
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, taskImpl);
    }

    @NonNull
    public static ApiVersion toProto(@NonNull AndroidVersion apiVersion) {
        ApiVersion.Builder builder = ApiVersion.newBuilder().setApiLevel(apiVersion.getApiLevel());
        if (apiVersion.getCodename() != null) {
            builder.setCodename(apiVersion.getCodename());
        }
        return builder.build();
    }

    @NonNull
    public static ApiVersion toProto(@NonNull com.android.builder.model.ApiVersion apiVersion) {
        ApiVersion.Builder builder = ApiVersion.newBuilder().setApiLevel(apiVersion.getApiLevel());
        if (apiVersion.getCodename() != null) {
            builder.setCodename(apiVersion.getCodename());
        }
        return builder.build();
    }

    @NonNull
    public static GradleBuildSplits toProto(@NonNull Splits splits) {
        GradleBuildSplits.Builder builder = GradleBuildSplits.newBuilder();
        if (splits.getDensity().isEnable()) {
            builder.setDensityEnabled(true);
            builder.setDensityAuto(splits.getDensity().isAuto());

            for (String compatibleScreen : splits.getDensity().getCompatibleScreens()) {
                builder.addDensityCompatibleScreens(getCompatibleScreen(compatibleScreen));
            }

            for (String filter : splits.getDensity().getApplicableFilters()) {
                Density density = Density.getEnum(filter);
                builder.addDensityValues(density == null ? -1 : density.getDpiValue());
            }
        }

        if (splits.getLanguage().isEnable()) {
            builder.setLanguageEnabled(true);
            builder.setLanguageAuto(splits.getLanguage().isAuto());
            builder.addAllLanguageIncludes(splits.getLanguage().getInclude());
        }

        if (splits.getAbi().isEnable()) {
            builder.setAbiEnabled(true);
            builder.setAbiEnableUniversalApk(splits.getAbi().isUniversalApk());
            for (String filter : splits.getAbi().getApplicableFilters()) {
                builder.addAbiFilters(getAbi(filter));
            }
        }
        return builder.build();
    }

    @NonNull
    public static GradleBuildVariant.Java8LangSupport toProto(
            @NonNull VariantScope.Java8LangSupport type) {
        Preconditions.checkArgument(
                type != VariantScope.Java8LangSupport.UNUSED
                        && type != VariantScope.Java8LangSupport.INVALID,
                "Unsupported type");
        switch (type) {
            case RETROLAMBDA:
                return GradleBuildVariant.Java8LangSupport.RETROLAMBDA;
            case DEXGUARD:
                return GradleBuildVariant.Java8LangSupport.DEXGUARD;
            case DESUGAR:
                return GradleBuildVariant.Java8LangSupport.INTERNAL;
            case INVALID:
                // fall through
            case UNUSED:
                throw new IllegalArgumentException("Unexpected type " + type);
        }
        throw new AssertionError("Unrecognized type " + type);
    }

    @NonNull
    public static GradleBuildVariant.DexBuilderTool toProto(@NonNull DexerTool dexerTool) {
        switch (dexerTool) {
            case DX:
                return GradleBuildVariant.DexBuilderTool.DX_DEXER;
            case D8:
                return GradleBuildVariant.DexBuilderTool.D8_DEXER;
        }
        throw new AssertionError("Unrecognized type " + dexerTool);
    }

    @NonNull
    public static GradleBuildVariant.DexMergerTool toProto(@NonNull DexMergerTool dexMerger) {
        switch (dexMerger) {
            case DX:
                return GradleBuildVariant.DexMergerTool.DX_MERGER;
            case D8:
                return GradleBuildVariant.DexMergerTool.D8_MERGER;
        }
        throw new AssertionError("Unrecognized type " + dexMerger);
    }

    @NonNull
    private static DeviceInfo.ApplicationBinaryInterface getAbi(@NonNull String name) {
        Abi abi = Abi.getByName(name);
        if (abi == null) {
            return DeviceInfo.ApplicationBinaryInterface.UNKNOWN_ABI;
        }
        switch (abi) {
            case ARMEABI:
                return DeviceInfo.ApplicationBinaryInterface.ARME_ABI;
            case ARMEABI_V7A:
                return DeviceInfo.ApplicationBinaryInterface.ARME_ABI_V7A;
            case ARM64_V8A:
                return DeviceInfo.ApplicationBinaryInterface.ARM64_V8A_ABI;
            case X86:
                return DeviceInfo.ApplicationBinaryInterface.X86_ABI;
            case X86_64:
                return DeviceInfo.ApplicationBinaryInterface.X86_64_ABI;
            case MIPS:
                return DeviceInfo.ApplicationBinaryInterface.MIPS_ABI;
            case MIPS64:
                return DeviceInfo.ApplicationBinaryInterface.MIPS_R2_ABI;
        }
        // Shouldn't happen
        return DeviceInfo.ApplicationBinaryInterface.UNKNOWN_ABI;
    }

    @NonNull
    private static GradleBuildSplits.CompatibleScreenSize getCompatibleScreen(
            @NonNull String compatibleScreen) {
        switch (compatibleScreen.toLowerCase(Locale.US)) {
            case "small":
                return GradleBuildSplits.CompatibleScreenSize.SMALL;
            case "normal":
                return GradleBuildSplits.CompatibleScreenSize.NORMAL;
            case "large":
                return GradleBuildSplits.CompatibleScreenSize.LARGE;
            case "xlarge":
                return GradleBuildSplits.CompatibleScreenSize.XLARGE;
            default:
                return GradleBuildSplits.CompatibleScreenSize.UNKNOWN_SCREEN_SIZE;
        }
    }
}
