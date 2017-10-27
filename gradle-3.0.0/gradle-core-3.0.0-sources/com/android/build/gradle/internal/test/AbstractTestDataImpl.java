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

package com.android.build.gradle.internal.test;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.testing.TestData;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import org.gradle.api.file.FileCollection;

/**
 * Common implementation of {@link TestData} for embedded test projects (in androidTest folder)
 * and separate module test projects.
 */
public abstract class AbstractTestDataImpl implements TestData {

    @NonNull
    private final VariantConfiguration<?, ?, ?> testVariantConfig;

    @NonNull
    private Map<String, String> extraInstrumentationTestRunnerArgs;

    @NonNull
    private boolean animationsDisabled;

    @NonNull protected final FileCollection testApkDir;

    @Nullable protected final FileCollection testedApksDir;

    public AbstractTestDataImpl(
            @NonNull VariantConfiguration<?, ?, ?> testVariantConfig,
            @NonNull FileCollection testApkDir,
            @Nullable FileCollection testedApksDir) {
        this.testVariantConfig = checkNotNull(testVariantConfig);
        this.extraInstrumentationTestRunnerArgs = Maps.newHashMap();
        this.testApkDir = testApkDir;
        this.testedApksDir = testedApksDir;
    }

    @NonNull
    @Override
    public String getInstrumentationRunner() {
        return testVariantConfig.getInstrumentationRunner();
    }

    @NonNull
    @Override
    public Map<String, String> getInstrumentationRunnerArguments() {
        return ImmutableMap.<String, String>builder()
                .putAll(testVariantConfig.getInstrumentationRunnerArguments())
                .putAll(extraInstrumentationTestRunnerArgs)
                .build();
    }

    public void setExtraInstrumentationTestRunnerArgs(
            @NonNull Map<String, String> extraInstrumentationTestRunnerArgs) {
        this.extraInstrumentationTestRunnerArgs =
                ImmutableMap.copyOf(extraInstrumentationTestRunnerArgs);
    }

    @NonNull
    @Override
    public boolean getAnimationsDisabled() {
        return animationsDisabled;
    }

    public void setAnimationsDisabled(boolean animationsDisabled) {
        this.animationsDisabled = animationsDisabled;
    }

    @Override
    public boolean isTestCoverageEnabled() {
        return testVariantConfig.isTestCoverageEnabled();
    }

    @NonNull
    @Override
    public AndroidVersion getMinSdkVersion() {
        return testVariantConfig.getMinSdkVersion();
    }

    @NonNull
    @Override
    public String getFlavorName() {
        return testVariantConfig.getFlavorName().toUpperCase(Locale.getDefault());
    }

    /**
     * Returns the directory containing the test APK as a {@link FileCollection}.
     *
     * @return the directory containing the test APK
     */
    @NonNull
    public FileCollection getTestApkDir() {
        return testApkDir;
    }

    /**
     * Returns the directory containing the tested APKs as a {@link FileCollection}, or null if the
     * test data is for testing a library.
     *
     * @return the directory containing the tested APKs, or null if the test data is for testing a
     *     library
     */
    @Nullable
    public FileCollection getTestedApksDir() {
        return testedApksDir;
    }

    @NonNull
    @Override
    public File getTestApk() {
        Collection<BuildOutput> testApkOutputs =
                BuildOutputs.load(VariantScope.TaskOutputType.APK, testApkDir);
        if (testApkOutputs.size() != 1) {
            throw new RuntimeException(
                    "Unexpected number of main APKs, expected 1, got  "
                            + testApkOutputs.size()
                            + ":"
                            + Joiner.on(",").join(testApkOutputs));
        }
        return testApkOutputs.iterator().next().getOutputFile();
    }
}
