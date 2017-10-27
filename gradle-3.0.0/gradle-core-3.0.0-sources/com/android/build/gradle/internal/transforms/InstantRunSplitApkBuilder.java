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

package com.android.build.gradle.internal.transforms;

import static java.nio.file.Files.deleteIfExists;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.packaging.PackagerException;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;

/** Common behavior for creating instant run related split APKs. */
public abstract class InstantRunSplitApkBuilder extends Transform {

    @NonNull
    protected final Logger logger;
    @NonNull
    protected final Project project;
    @NonNull protected final AndroidBuilder androidBuilder;
    @Nullable private final FileCache fileCache;
    @NonNull private final AaptGeneration aaptGeneration;
    @NonNull protected final InstantRunBuildContext buildContext;
    @NonNull
    protected final File outputDirectory;
    @Nullable protected final CoreSigningConfig signingConf;
    @NonNull
    private final PackagingScope packagingScope;
    @NonNull
    private final AaptOptions aaptOptions;
    @NonNull protected final File supportDirectory;
    @NonNull protected final File aaptIntermediateDirectory;

    public InstantRunSplitApkBuilder(
            @NonNull Logger logger,
            @NonNull Project project,
            @NonNull InstantRunBuildContext buildContext,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCache fileCache,
            @NonNull PackagingScope packagingScope,
            @Nullable CoreSigningConfig signingConf,
            @NonNull AaptGeneration aaptGeneration,
            @NonNull AaptOptions aaptOptions,
            @NonNull File outputDirectory,
            @NonNull File supportDirectory,
            @NonNull File aaptIntermediateDirectory) {
        this.logger = logger;
        this.project = project;
        this.buildContext = buildContext;
        this.androidBuilder = androidBuilder;
        this.fileCache = fileCache;
        this.packagingScope = packagingScope;
        this.signingConf = signingConf;
        this.aaptGeneration = aaptGeneration;
        this.aaptOptions = aaptOptions;
        this.outputDirectory = outputDirectory;
        this.supportDirectory = supportDirectory;
        this.aaptIntermediateDirectory = aaptIntermediateDirectory;
    }

    @NonNull
    @Override
    public final Map<String, Object> getParameterInputs() {
        ImmutableMap.Builder<String, Object> builder =
                ImmutableMap.<String, Object>builder()
                        .put("applicationId", packagingScope.getApplicationId())
                        .put("versionCode", packagingScope.getVersionCode())
                        .put("aaptGeneration", aaptGeneration.name());
        if (packagingScope.getVersionName() != null) {
            builder.put("versionName", packagingScope.getVersionName());
        }
        return builder.build();
    }

    protected static class DexFiles {
        private final ImmutableSet<File> dexFiles;
        private final String dexFolderName;

        protected DexFiles(@NonNull File[] dexFiles, @NonNull String dexFolderName) {
            this(ImmutableSet.copyOf(dexFiles), dexFolderName);
        }

        protected DexFiles(@NonNull ImmutableSet<File> dexFiles, @NonNull String dexFolderName) {
            this.dexFiles = dexFiles;
            this.dexFolderName = dexFolderName;
        }

        protected String encodeName() {
            return dexFolderName.replace('-', '_');
        }

        protected ImmutableSet<File> getDexFiles() {
            return dexFiles;
        }
    }

    @NonNull
    protected File generateSplitApk(@NonNull DexFiles dexFiles)
            throws IOException, KeytoolException, PackagerException, InterruptedException,
                    ProcessException, TransformException, ExecutionException {

        String uniqueName = dexFiles.encodeName();
        final File alignedOutput = new File(outputDirectory, uniqueName + ".apk");
        Files.createParentDirs(alignedOutput);

        try (Aapt aapt = getAapt()) {
            File resPackageFile =
                    generateSplitApkResourcesAp(
                            logger,
                            aapt,
                            packagingScope,
                            supportDirectory,
                            aaptOptions,
                            androidBuilder,
                            uniqueName);

            // packageCodeSplitApk uses a temporary directory for incremental runs. Since we don't
            // do incremental builds here, make sure it gets an empty directory.
            File tempDir = new File(supportDirectory, "package_" + uniqueName);
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw new TransformException(
                        "Cannot create temporary folder " + tempDir.getAbsolutePath());
            }

            FileUtils.cleanOutputDir(tempDir);

            androidBuilder.packageCodeSplitApk(
                    resPackageFile,
                    dexFiles.dexFiles,
                    signingConf,
                    alignedOutput,
                    tempDir,
                    ApkCreatorFactories.fromProjectProperties(project, true));

            buildContext.addChangedFile(FileType.SPLIT, alignedOutput);
            deleteIfExists(resPackageFile.toPath());
        }

        return alignedOutput;
    }

    @NonNull
    public static File generateSplitApkManifest(
            @NonNull File apkSupportDir,
            @NonNull String splitName,
            @NonNull String packageId,
            @Nullable String versionName,
            int versionCode,
            @Nullable String minSdkVersion)
            throws IOException {

        String versionNameToUse = versionName;
        if (versionNameToUse == null) {
            versionNameToUse = String.valueOf(versionCode);
        }

        File androidManifest = new File(apkSupportDir, "AndroidManifest.xml");
        try (OutputStreamWriter fileWriter =
                     new OutputStreamWriter(new FileOutputStream(androidManifest), "UTF-8")) {
            fileWriter
                    .append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                    .append(
                            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                    .append("      package=\"")
                    .append(packageId)
                    .append("\"\n");
            if (versionCode != VersionQualifier.DEFAULT_VERSION) {
                fileWriter
                        .append("      android:versionCode=\"").append(String.valueOf(versionCode))
                        .append("\"\n")
                        .append("      android:versionName=\"").append(versionNameToUse)
                        .append("\"\n");
            }
            fileWriter.append("      split=\"lib_").append(splitName).append("_apk\">\n");
            if (minSdkVersion != null) {
                fileWriter
                        .append("\t<uses-sdk android:minSdkVersion=\"")
                        .append(minSdkVersion)
                        .append("\"/>\n");
            }
            fileWriter.append("</manifest>\n").flush();
        }
        return androidManifest;
    }

    /**
     * Generate a split APK resources, only containing a minimum AndroidManifest.xml to be a legal
     * split APK but has not resources attached. The returned resources_ap file returned can be used
     * to build a legal split APK.
     */
    @NonNull
    public static File generateSplitApkResourcesAp(
            @NonNull Logger logger,
            @NonNull Aapt aapt,
            @NonNull PackagingScope packagingScope,
            @NonNull File supportDirectory,
            @NonNull AaptOptions aaptOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull String uniqueName)
            throws IOException, ProcessException, InterruptedException {

        File apkSupportDir = new File(supportDirectory, uniqueName);
        if (!apkSupportDir.exists() && !apkSupportDir.mkdirs()) {
            logger.error("Cannot create apk support dir {}", apkSupportDir.getAbsoluteFile());
        }
        File androidManifest =
                generateSplitApkManifest(
                        apkSupportDir,
                        uniqueName,
                        packagingScope.getApplicationId(),
                        packagingScope.getVersionName(),
                        packagingScope.getVersionCode(),
                        null);

        return generateSplitApkResourcesAp(
                logger,
                aapt,
                androidManifest,
                supportDirectory,
                aaptOptions,
                androidBuilder,
                null, /* imports */
                uniqueName);
    }

    /**
     * Generate the compile resouces_ap file that contains the resources for this split plus the
     * split definition.
     */
    @NonNull
    public static File generateSplitApkResourcesAp(
            @NonNull Logger logger,
            @NonNull Aapt aapt,
            @NonNull File androidManifest,
            @NonNull File supportDirectory,
            @NonNull AaptOptions aaptOptions,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCollection imports,
            @NonNull String uniqueName)
            throws IOException, ProcessException, InterruptedException {

        File apkSupportDir = new File(supportDirectory, uniqueName);
        if (!apkSupportDir.exists() && !apkSupportDir.mkdirs()) {
            logger.error("Cannot create apk support dir {}", apkSupportDir.getAbsoluteFile());
        }

        File resFilePackageFile = new File(apkSupportDir, "resources_ap");

        List<File> importedAPKs =
                imports != null
                        ? imports.getAsFileTree()
                                .getFiles()
                                .stream()
                                .filter(file -> file.getName().endsWith(SdkConstants.EXT_RES))
                                .collect(Collectors.toList())
                        : ImmutableList.of();

        AaptPackageConfig.Builder aaptConfig =
                new AaptPackageConfig.Builder()
                        .setManifestFile(androidManifest)
                        .setOptions(aaptOptions)
                        .setDebuggable(true)
                        .setVariantType(VariantType.DEFAULT)
                        .setImports(ImmutableList.copyOf(importedAPKs))
                        .setResourceOutputApk(resFilePackageFile);

        androidBuilder.processResources(aapt, aaptConfig);

        return resFilePackageFile;
    }

    protected Aapt getAapt() throws IOException {
        return makeAapt(aaptGeneration, androidBuilder, fileCache, aaptIntermediateDirectory);
    }

    @NonNull
    public static Aapt makeAapt(
            @NonNull AaptGeneration aaptGeneration,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCache fileCache,
            @NonNull File intermediateFolder)
            throws IOException {
        return AaptGradleFactory.make(
                aaptGeneration,
                androidBuilder,
                null,
                fileCache,
                true,
                FileUtils.mkdirs(intermediateFolder),
                0);
    }

}
