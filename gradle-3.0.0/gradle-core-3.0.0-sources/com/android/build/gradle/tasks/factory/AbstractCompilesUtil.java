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

package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.ILogger;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.AbstractCompile;

/**
 * Common code for configuring {@link AbstractCompile} instances.
 */
public class AbstractCompilesUtil {

    public static final String ANDROID_APT_PLUGIN_NAME = "com.neenbedankt.android-apt";

    /**
     * Determines the java language level to use and sets it on the given task and {@link
     * CompileOptions}. The latter is to propagate the information to Studio.
     */
    public static void configureLanguageLevel(
            AbstractCompile compileTask,
            final CompileOptions compileOptions,
            String compileSdkVersion,
            VariantScope.Java8LangSupport java8LangSupport) {
        setDefaultJavaVersion(compileOptions, compileSdkVersion, java8LangSupport);
        compileTask.setSourceCompatibility(compileOptions.getSourceCompatibility().toString());
        compileTask.setTargetCompatibility(compileOptions.getTargetCompatibility().toString());
    }

    public static void setDefaultJavaVersion(
            final CompileOptions compileOptions,
            String compileSdkVersion,
            VariantScope.Java8LangSupport java8LangSupport) {
        compileOptions.setDefaultJavaVersion(
                chooseDefaultJavaVersion(
                        compileSdkVersion,
                        System.getProperty("java.specification.version"),
                        java8LangSupport));
    }

    @NonNull
    @VisibleForTesting
    static JavaVersion chooseDefaultJavaVersion(
            @NonNull String compileSdkVersion,
            @NonNull String currentJdkVersion,
            VariantScope.Java8LangSupport java8LangSupport) {
        final AndroidVersion hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion);
        Integer compileSdkLevel = (hash == null ? null : hash.getFeatureLevel());

        JavaVersion javaVersionToUse;
        if (compileSdkLevel == null) {
            javaVersionToUse = JavaVersion.VERSION_1_6;
        } else {
            if (0 < compileSdkLevel && compileSdkLevel <= 20) {
                javaVersionToUse = JavaVersion.VERSION_1_6;
            } else if (21 <= compileSdkLevel && compileSdkLevel < 24) {
                javaVersionToUse = JavaVersion.VERSION_1_7;
            } else {
                javaVersionToUse = JavaVersion.VERSION_1_7;
            }
        }

        JavaVersion jdkVersion = JavaVersion.toVersion(currentJdkVersion);

        if (jdkVersion.compareTo(javaVersionToUse) < 0) {
            Logging.getLogger(AbstractCompilesUtil.class).warn(
                    "Default language level for compileSdkVersion '{}' is " +
                            "{}, but the JDK used is {}, so the JDK language level will be used.",
                    compileSdkVersion,
                    javaVersionToUse,
                    jdkVersion);
            javaVersionToUse = jdkVersion;
        }
        return javaVersionToUse;
    }

    /**
     * Determine if java compilation can be incremental.
     */
    public static boolean isIncremental(
            @NonNull Project project,
            @NonNull VariantScope variantScope,
            @NonNull CompileOptions compileOptions,
            @Nullable Configuration processorConfiguration,
            @NonNull ILogger log) {
        boolean incremental = true;
        if (compileOptions.getIncremental() != null) {
            incremental = compileOptions.getIncremental();
            log.verbose("Incremental flag set to %1$b in DSL", incremental);
        } else {
            boolean hasAnnotationProcessor =
                    processorConfiguration != null
                            && !processorConfiguration.getAllDependencies().isEmpty();
            if (variantScope.getGlobalScope().getExtension().getDataBinding().isEnabled()
                    || hasAnnotationProcessor
                    || project.getPlugins().hasPlugin("me.tatarka.retrolambda")) {
                incremental = false;
                log.verbose("Incremental Java compilation disabled in variant %1$s "
                                + "as you are using an incompatible plugin",
                        variantScope.getVariantConfiguration().getFullName());
            }
        }
        return incremental;
    }
}
