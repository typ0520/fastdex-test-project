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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.builder.model.SourceProvider;
import com.android.builder.testing.TestData;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.file.FileCollection;
import org.xml.sax.SAXException;

/** Implementation of {@link TestData} for separate test modules. */
public class TestApplicationTestData extends AbstractTestDataImpl {

    private final String testApplicationId;
    private final Map<String, String> testedProperties;
    private final GradleVariantConfiguration variantConfiguration;

    public TestApplicationTestData(
            GradleVariantConfiguration variantConfiguration,
            String testApplicationId,
            @NonNull FileCollection testApkDir,
            @NonNull FileCollection testedApksDir) {
        super(variantConfiguration, testApkDir, testedApksDir);
        this.variantConfiguration = variantConfiguration;
        this.testedProperties = new HashMap<>();
        this.testApplicationId = testApplicationId;
    }

    @Override
    public void loadFromMetadataFile(File metadataFile)
            throws ParserConfigurationException, SAXException, IOException {
        Collection<BuildOutput> testedManifests =
                BuildOutputs.load(
                        TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS,
                        metadataFile.getParentFile());
        // all published manifests have the same package so first one will do.
        Optional<BuildOutput> splitOutput = testedManifests.stream().findFirst();

        if (splitOutput.isPresent()) {
            testedProperties.putAll(splitOutput.get().getProperties());
        } else {
            throw new RuntimeException(
                    "No merged manifest metadata at " + metadataFile.getAbsolutePath());
        }
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return testApplicationId;
    }

    @Nullable
    @Override
    public String getTestedApplicationId() {
        return testedProperties.get("packageId");
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    @NonNull
    @Override
    public ImmutableList<File> getTestedApks(
            @NonNull ProcessExecutor processExecutor,
            @Nullable File splitSelectExe,
            @NonNull DeviceConfigProvider deviceConfigProvider,
            @NonNull ILogger logger) throws ProcessException {

        // use a Set to remove duplicate entries.
        ImmutableList.Builder<File> selectedApks = ImmutableList.builder();
        // retrieve all the published files.
        Collection<BuildOutput> testedApkFiles =
                BuildOutputs.load(VariantScope.TaskOutputType.APK, testedApksDir);

        // if we have more than one, that means pure splits are in the equation.
        if (testedApkFiles.size() > 1 && splitSelectExe != null) {
            OutputScope testedOutputScope = new OutputScope(MultiOutputPolicy.MULTI_APK);
            List<String> testedSplitApksPath = getSplitApks(testedOutputScope);
            selectedApks.addAll(
                    SplitOutputMatcher.computeBestOutput(
                            processExecutor,
                            splitSelectExe,
                            deviceConfigProvider,
                            getMainApk(testedOutputScope),
                            testedSplitApksPath));
        } else {
            // if we have only one or no split-select tool available, just install them all
            // it's not efficient but it's correct.
            if (testedApkFiles.size() > 1) {
                logger.warning("split-select tool unavailable, all split APKs will be installed");
            }
            selectedApks.addAll(
                    testedApkFiles
                            .stream()
                            .map(BuildOutput::getOutputFile)
                            .collect(Collectors.toList()));
        }
        return selectedApks.build();
    }

    @NonNull
    @Override
    public List<File> getTestDirectories() {
        // For now we check if there are any test sources. We could inspect the test classes and
        // apply JUnit logic to see if there's something to run, but that would not catch the case
        // where user makes a typo in a test name or forgets to inherit from a JUnit class
        ImmutableList.Builder<File> javaDirectories = ImmutableList.builder();
        for (SourceProvider sourceProvider : variantConfiguration.getSortedSourceProviders()) {
            javaDirectories.addAll(sourceProvider.getJavaDirectories());
        }
        return javaDirectories.build();
    }

    @NonNull
    private static List<String> getSplitApks(OutputScope outputScope) {
        return outputScope
                .getOutputs(TaskOutputHolder.TaskOutputType.APK)
                .stream()
                .filter(
                        splitOutput ->
                                splitOutput.getApkInfo().getType()
                                        == VariantOutput.OutputType.SPLIT)
                .map(splitOutput -> splitOutput.getOutputFile().getAbsolutePath())
                .collect(Collectors.toList());
    }

    /**
     * Retrieve the main APK from the list of APKs published by the tested configuration. There can
     * be multiple split APKs along the main APK returned by the configuration.
     *
     * @return the tested main APK
     */
    @NonNull
    private static File getMainApk(OutputScope outputScope) {

        Optional<File> mainApk =
                outputScope
                        .getOutputs(TaskOutputHolder.TaskOutputType.APK)
                        .stream()
                        .filter(
                                splitOutput ->
                                        splitOutput.getApkInfo().getType()
                                                != VariantOutput.OutputType.SPLIT)
                        .map(BuildOutput::getOutputFile)
                        .findFirst();

        if (mainApk.isPresent()) {
            return mainApk.get();
        }
        throw new RuntimeException("Cannot retrieve main APK");
    }
}
