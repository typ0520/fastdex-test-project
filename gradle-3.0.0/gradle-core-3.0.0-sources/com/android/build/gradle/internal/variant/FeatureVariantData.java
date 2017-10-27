/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.regex.Pattern;

/** Data about a variant that produce a feature split. */
public class FeatureVariantData extends ApkVariantData implements TestedVariantData {

    /** Regular expression defining the character to be replaced in the split name. */
    private static final Pattern FEATURE_REPLACEMENT = Pattern.compile("-");

    /** Regular expression defining the characters to be excluded from the split name. */
    private static final Pattern FEATURE_EXCLUSION = Pattern.compile("[^a-zA-Z0-9_]");

    private final Map<VariantType, TestVariantData> testVariants;
    private final String featureName;

    public FeatureVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig androidConfig,
            @NonNull TaskManager taskManager,
            @NonNull GradleVariantConfiguration config,
            @NonNull ErrorReporter errorReporter,
            @NonNull Recorder recorder) {
        super(globalScope, androidConfig, taskManager, config, errorReporter, recorder);
        testVariants = Maps.newEnumMap(VariantType.class);

        // Compute the split value name for the manifest.
        String splitName =
                FEATURE_REPLACEMENT
                        .matcher(getScope().getGlobalScope().getProjectBaseName())
                        .replaceAll("_");
        featureName = FEATURE_EXCLUSION.matcher(splitName).replaceAll("");

        // create default output
        getOutputFactory().addMainApk();
    }

    @Override
    @NonNull
    public String getDescription() {
        if (getVariantConfiguration().hasFlavors()) {
            return String.format(
                    "%s feature split build for flavor %s",
                    getCapitalizedBuildTypeName(), getCapitalizedFlavorName());
        } else {
            return String.format("%s feature split build", getCapitalizedBuildTypeName());
        }
    }

    @Nullable
    @Override
    public TestVariantData getTestVariantData(@NonNull VariantType type) {
        return testVariants.get(type);
    }

    @Override
    public void setTestVariantData(
            @NonNull TestVariantData testVariantData, @NonNull VariantType type) {
        testVariants.put(type, testVariantData);
    }

    @NonNull
    public String getFeatureName() {
        return featureName;
    }
}
