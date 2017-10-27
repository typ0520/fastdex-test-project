/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_APP_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.ApplicationId;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.FeatureVariantData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.utils.FileCache;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.google.common.base.CharMatcher;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Set;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates all metadata (like AndroidManifest.xml) necessary for a ABI dimension split APK.
 */
public class GenerateSplitAbiRes extends BaseTask {

    private String applicationId;
    private String outputBaseName;

    // these are the default values set in the variant's configuration, although they
    // are not directly use in this task, they will be used when versionName and versionCode
    // is not changed by the user's scripts. Therefore, if those values change, this task
    // should be considered out of date.
    private String versionName;
    private int versionCode;
    private AaptGeneration aaptGeneration;

    private Set<String> splits;
    private File outputDirectory;
    private boolean debuggable;
    private AaptOptions aaptOptions;
    private OutputScope outputScope;
    private OutputFactory outputFactory;
    private VariantType variantType;
    private VariantScope variantScope;
    private FileCache fileCache;
    @Nullable private String featureName;
    @Nullable private FileCollection applicationIdOverride;

    @Input
    public String getApplicationId() {
        return applicationId;
    }

    @Input
    public int getVersionCode() {
        return versionCode;
    }

    @Input
    @Optional
    public String getVersionName() {
        return versionName;
    }

    @Input
    public String getAaptGeneration() {
        return aaptGeneration.name();
    }

    @Input
    public String getOutputBaseName() {
        return outputBaseName;
    }

    @Input
    public Set<String> getSplits() {
        return splits;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    @Nested
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    @Input
    @Optional
    @Nullable
    public String getFeatureName() {
        return featureName;
    }

    @InputFiles
    @Optional
    @Nullable
    public FileCollection getApplicationIdOverride() {
        return applicationIdOverride;
    }

    @TaskAction
    protected void doFullTaskAction() throws IOException, InterruptedException, ProcessException {

        outputScope.deleteAllEntries(VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES);
        for (String split : getSplits()) {
            File resPackageFile = getOutputFileForSplit(split);

            ApkData abiApkData =
                    outputFactory.addConfigurationSplit(
                            OutputFile.FilterType.ABI, split, resPackageFile.getName());
            abiApkData.setVersionCode(variantScope.getVariantConfiguration().getVersionCode());
            abiApkData.setVersionName(variantScope.getVariantConfiguration().getVersionName());

            // call user's script for the newly discovered ABI pure split.
            if (variantScope.getVariantData().variantOutputFactory != null) {
                variantScope.getVariantData().variantOutputFactory.create(abiApkData);
            }

            File manifestFile = generateSplitManifest(split, abiApkData);

            AndroidBuilder builder = getBuilder();
            try (Aapt aapt =
                    AaptGradleFactory.make(
                            aaptGeneration,
                            builder,
                            new LoggedProcessOutputHandler(
                                    new AaptGradleFactory.FilteringLogger(builder.getLogger())),
                            fileCache,
                            true,
                            FileUtils.mkdirs(
                                    new File(
                                            variantScope.getIncrementalDir(getName()),
                                            "aapt-temp")),
                            variantScope
                                    .getGlobalScope()
                                    .getExtension()
                                    .getAaptOptions()
                                    .getCruncherProcesses())) {

                AaptPackageConfig.Builder aaptConfig = new AaptPackageConfig.Builder();
                aaptConfig
                        .setManifestFile(manifestFile)
                        .setOptions(DslAdaptersKt.convert(aaptOptions))
                        .setDebuggable(debuggable)
                        .setResourceOutputApk(resPackageFile)
                        .setVariantType(variantType);

                getBuilder().processResources(aapt, aaptConfig);
            }

            outputScope.addOutputForSplit(
                    VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES,
                    abiApkData,
                    resPackageFile);
        }

        outputScope.save(VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES, outputDirectory);
    }

    @VisibleForTesting
    File generateSplitManifest(String split, ApkData abiApkData) throws IOException {
        // Split name can only contains 0-9, a-z, A-Z, '.' and '_'.  Replace all other
        // characters with underscore.
        CharMatcher charMatcher =
                CharMatcher.inRange('0', '9')
                        .or(CharMatcher.inRange('A', 'Z'))
                        .or(CharMatcher.inRange('a', 'z'))
                        .or(CharMatcher.is('_'))
                        .or(CharMatcher.is('.'))
                        .negate();

        String encodedSplitName =
                (featureName != null ? featureName + "." : "")
                        + "config."
                        + charMatcher.replaceFrom(split, '_');

        File tmpDirectory = new File(outputDirectory, split);
        FileUtils.mkdirs(tmpDirectory);

        File tmpFile = new File(tmpDirectory, "AndroidManifest.xml");

        String versionNameToUse = abiApkData.getVersionName();
        if (versionNameToUse == null) {
            versionNameToUse = String.valueOf(abiApkData.getVersionCode());
        }

        // Override the applicationId for features.
        String manifestAppId;
        if (applicationIdOverride != null && !applicationIdOverride.isEmpty()) {
            manifestAppId =
                    ApplicationId.load(applicationIdOverride.getSingleFile()).getApplicationId();
        } else {
            manifestAppId = applicationId;
        }

        try (OutputStreamWriter fileWriter =
                new OutputStreamWriter(
                        new BufferedOutputStream(new FileOutputStream(tmpFile)), "UTF-8")) {

            fileWriter.append(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "      package=\""
                            + manifestAppId
                            + "\"\n"
                            + "      android:versionCode=\""
                            + abiApkData.getVersionCode()
                            + "\"\n"
                            + "      android:versionName=\""
                            + versionNameToUse
                            + "\"\n");

            if (featureName != null) {
                fileWriter.append("      configForSplit=\"" + featureName + "\"\n");
            }

            fileWriter.append(
                    "      split=\""
                            + encodedSplitName
                            + "\"\n"
                            + "      targetABI=\""
                            + split
                            + "\">\n"
                            + "       <uses-sdk android:minSdkVersion=\"21\"/>\n"
                            + "</manifest> ");
            fileWriter.flush();
        }
        return tmpFile;
    }

    // FIX ME : this calculation should move to SplitScope.Split interface
    private File getOutputFileForSplit(final String split) {
        return new File(outputDirectory, "resources-" + getOutputBaseName() + "-" + split + ".ap_");
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<GenerateSplitAbiRes> {

        @NonNull private final VariantScope scope;
        @NonNull private final File outputDirectory;

        public ConfigAction(@NonNull VariantScope scope, @NonNull File outputDirectory) {
            this.scope = scope;
            this.outputDirectory = outputDirectory;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("generate", "SplitAbiRes");
        }

        @Override
        @NonNull
        public Class<GenerateSplitAbiRes> getType() {
            return GenerateSplitAbiRes.class;
        }

        @Override
        public void execute(@NonNull GenerateSplitAbiRes generateSplitAbiRes) {
            final VariantConfiguration config = scope.getVariantConfiguration();

            generateSplitAbiRes.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            generateSplitAbiRes.setVariantName(config.getFullName());
            generateSplitAbiRes.featureName =
                    config.getType() == VariantType.FEATURE && !scope.isBaseFeature()
                            ? ((FeatureVariantData) scope.getVariantData()).getFeatureName()
                            : null;

            // not used directly, but considered as input for the task.
            generateSplitAbiRes.versionCode = config.getVersionCode();
            generateSplitAbiRes.versionName = config.getVersionName();
            generateSplitAbiRes.aaptGeneration =
                    AaptGeneration.fromProjectOptions(scope.getGlobalScope().getProjectOptions());
            generateSplitAbiRes.fileCache = scope.getGlobalScope().getBuildCache();

            generateSplitAbiRes.variantScope = scope;
            generateSplitAbiRes.variantType = config.getType();
            generateSplitAbiRes.outputDirectory = outputDirectory;
            generateSplitAbiRes.splits =
                    AbiSplitOptions.getAbiFilters(
                            scope.getGlobalScope().getExtension().getSplits().getAbiFilters());
            generateSplitAbiRes.outputBaseName = config.getBaseName();
            generateSplitAbiRes.applicationId = config.getApplicationId();
            generateSplitAbiRes.debuggable = config.getBuildType().isDebuggable();
            generateSplitAbiRes.aaptOptions =
                    scope.getGlobalScope().getExtension().getAaptOptions();
            generateSplitAbiRes.outputScope = scope.getOutputScope();
            generateSplitAbiRes.outputFactory = scope.getVariantData().getOutputFactory();

            if (scope.getVariantData().getType() == VariantType.FEATURE) {
                if (scope.isBaseFeature()) {
                    generateSplitAbiRes.applicationIdOverride =
                            scope.getArtifactFileCollection(
                                    METADATA_VALUES, MODULE, METADATA_APP_ID_DECLARATION);
                } else {
                    generateSplitAbiRes.applicationIdOverride =
                            scope.getArtifactFileCollection(
                                    COMPILE_CLASSPATH, MODULE, FEATURE_APPLICATION_ID_DECLARATION);
                }
            }
        }
    }
}
