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

package com.android.build.gradle.tasks;

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_WINDOWS;
import static com.android.build.gradle.options.LongOption.DEPRECATED_NDK_COMPILE_LEASE;
import static com.android.build.gradle.options.NdkLease.DEPRECATED_NDK_COMPILE_LEASE_DAYS;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NdkTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.NdkLease;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.util.ReferenceHolder;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.api.tasks.util.PatternSet;

public class NdkCompile extends NdkTask {

    private static String getAlternatives(File generatedMakefile, String urlSuffix) {
        String generatedAndridMk = "";
        if (generatedMakefile != null) {
            generatedAndridMk =
                    String.format(
                            " To get started, you can use the sample ndk-build script the Android\n"
                                    + " plugin generated for you at:\n"
                                    + " %s\n",
                            generatedMakefile);
        }

        return String.format(
                "Consider using CMake or ndk-build integration. For more information, go to:\n"
                        + " https://d.android.com/r/studio-ui/add-native-code.html%s\n"
                        + "%s"
                        + "Alternatively, you can use the experimental plugin:\n"
                        + " https://developer.android.com/r/tools/experimental-plugin.html\n",
                urlSuffix, generatedAndridMk);
    }

    private FileCollection sourceFolders;
    private File generatedMakefile;

    private boolean debuggable;

    private File soFolder;

    private File objFolder;

    private File ndkDirectory;

    private boolean ndkRenderScriptMode;

    private boolean ndkCygwinMode;

    private boolean isForTesting;

    private boolean isUseDeprecatedNdkFlag;

    private boolean isDeprecatedNdkCompileLeaseExpired;

    @OutputFile
    public File getGeneratedMakefile() {
        return generatedMakefile;
    }

    public void setGeneratedMakefile(File generatedMakefile) {
        this.generatedMakefile = generatedMakefile;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @OutputDirectory
    public File getSoFolder() {
        return soFolder;
    }

    public void setSoFolder(File soFolder) {
        this.soFolder = soFolder;
    }

    @OutputDirectory
    public File getObjFolder() {
        return objFolder;
    }

    public void setObjFolder(File objFolder) {
        this.objFolder = objFolder;
    }

    @Optional
    @Input
    public File getNdkDirectory() {
        return ndkDirectory;
    }

    public void setNdkDirectory(File ndkDirectory) {
        this.ndkDirectory = ndkDirectory;
    }

    @Input
    public boolean isNdkRenderScriptMode() {
        return ndkRenderScriptMode;
    }

    public void setNdkRenderScriptMode(boolean ndkRenderScriptMode) {
        this.ndkRenderScriptMode = ndkRenderScriptMode;
    }

    @Input
    public boolean isNdkCygwinMode() {
        return ndkCygwinMode;
    }

    public void setNdkCygwinMode(boolean ndkCygwinMode) {
        this.ndkCygwinMode = ndkCygwinMode;
    }

    @Input
    public boolean isForTesting() {
        return isForTesting;
    }

    public void setForTesting(boolean forTesting) {
        isForTesting = forTesting;
    }

    @Input
    public boolean isDeprecatedNdkCompileLeaseExpired() {
        return isDeprecatedNdkCompileLeaseExpired;
    }

    @Input
    public boolean isUseDeprecatedNdkFlag() {
        return isUseDeprecatedNdkFlag;
    }

    @SkipWhenEmpty
    @InputFiles
    public FileTree getSource() {
        return sourceFolders.getAsFileTree();
    }

    private static String getAlternativesAndLeaseNotice(File generatedMakefile, String urlSuffix) {
        return String.format(
                getAlternatives(generatedMakefile, urlSuffix)
                        + "To continue using the deprecated NDK compile for another %s days, "
                        + "set \n"
                        + "%s=%s in gradle.properties",
                DEPRECATED_NDK_COMPILE_LEASE_DAYS,
                DEPRECATED_NDK_COMPILE_LEASE.getPropertyName(),
                NdkLease.getFreshDeprecatedNdkCompileLease());
    }

    @TaskAction
    void taskAction(IncrementalTaskInputs inputs) throws IOException, ProcessException {
        FileTree sourceFileTree = getSource();
        Set<File> sourceFiles =
                sourceFileTree.matching(new PatternSet().exclude("**/*.h")).getFiles();
        File makefile = getGeneratedMakefile();

        if (isUseDeprecatedNdkFlag) {
            writeMakefile(sourceFiles, makefile);
            throw new RuntimeException(
                    String.format(
                            "Error: Flag %s is no longer supported and will be removed in the next "
                                    + "version of Android Studio.  Please switch to a supported "
                                    + "build system.\n%s",
                            BooleanOption.ENABLE_DEPRECATED_NDK.getPropertyName(),
                            getAlternativesAndLeaseNotice(makefile, "#ndkCompile")));
        }
        if (isDeprecatedNdkCompileLeaseExpired) {
            writeMakefile(sourceFiles, makefile);
            // Normally, we would catch the user when they try to configure the NDK, but NDK do
            // not need to be configured by default.  Throw this exception during task execution in
            // case we miss it.
            throw new RuntimeException(
                    "Error: Your project contains C++ files but it is not using a supported "
                            + "native build system.\n"
                            + getAlternatives(null, ""));
        }

        if (sourceFiles.isEmpty()) {
            makefile.delete();
            FileUtils.cleanOutputDir(getSoFolder());
            FileUtils.cleanOutputDir(getObjFolder());
            return;
        }

        if (ndkDirectory == null || !ndkDirectory.isDirectory()) {
            throw new GradleException(
                    "NDK not configured.\n" +
                    "Download the NDK from http://developer.android.com/tools/sdk/ndk/." +
                    "Then add ndk.dir=path/to/ndk in local.properties.\n" +
                    "(On Windows, make sure you escape backslashes, e.g. C:\\\\ndk rather than C:\\ndk)");
        }

        final ReferenceHolder<Boolean> generateMakeFile = ReferenceHolder.of(false);

        if (!inputs.isIncremental()) {
            getLogger().info("Unable do incremental execution: full task run");
            generateMakeFile.setValue(true);
            FileUtils.cleanOutputDir(getSoFolder());
            FileUtils.cleanOutputDir(getObjFolder());
        } else {
            // look for added or removed files *only*

            inputs.outOfDate(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails change) {
                    if (change.isAdded()) {
                        generateMakeFile.setValue(true);
                    }
                }
            });

            inputs.removed(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails change) {
                    generateMakeFile.setValue(true);
                }
            });

        }

        if (generateMakeFile.getValue()) {
            writeMakefile(sourceFiles, makefile);
        }

        getLogger()
                .warn(
                        "Warning: Deprecated NDK integration enabled by "
                                + DEPRECATED_NDK_COMPILE_LEASE.getPropertyName()
                                + " flag in gradle.properties will be removed from Android Gradle "
                                + "plugin in the next version.\n"
                                + getAlternatives(makefile, "#ndkCompile"));

        // now build
        runNdkBuild(ndkDirectory, makefile);
    }

    private void writeMakefile(@NonNull Set<File> sourceFiles, @NonNull File makefile)
            throws IOException {
        CoreNdkOptions ndk = getNdkConfig();
        Preconditions.checkNotNull(ndk, "Ndk config should be set");

        StringBuilder sb = new StringBuilder();

        sb.append("LOCAL_PATH := $(call my-dir)\n" +
                "include $(CLEAR_VARS)\n\n");

        String moduleName = ndk.getModuleName() != null ? ndk.getModuleName() : getProject().getName();
        if (isForTesting) {
            moduleName = moduleName + "_test";
        }

        sb.append("LOCAL_MODULE := ").append(moduleName).append('\n');

        if (ndk.getcFlags() != null) {
            sb.append("LOCAL_CFLAGS := ").append(ndk.getcFlags()).append('\n');
        }

        // To support debugging from Android Studio.
        sb.append("LOCAL_LDFLAGS := -Wl,--build-id\n");

        List<String> fullLdlibs = Lists.newArrayList();
        if (ndk.getLdLibs() != null) {
            fullLdlibs.addAll(ndk.getLdLibs());
        }
        if (isNdkRenderScriptMode()) {
            fullLdlibs.add("dl");
            fullLdlibs.add("log");
            fullLdlibs.add("jnigraphics");
            fullLdlibs.add("RScpp_static");
        }

        if (!fullLdlibs.isEmpty()) {
            sb.append("LOCAL_LDLIBS := \\\n");
            for (String lib : fullLdlibs) {
                sb.append("\t-l").append(lib).append(" \\\n");
            }
            sb.append('\n');
        }

        sb.append("LOCAL_SRC_FILES := \\\n");
        for (File sourceFile : sourceFiles) {
            sb.append('\t').append(sourceFile.getAbsolutePath()).append(" \\\n");
        }
        sb.append('\n');

        for (File sourceFolder : sourceFolders.getFiles()) {
            sb.append("LOCAL_C_INCLUDES += ").append(sourceFolder.getAbsolutePath()).append('\n');
        }

        if (isNdkRenderScriptMode()) {
            sb.append("LOCAL_LDFLAGS += -L$(call host-path,$(TARGET_C_INCLUDES)/../lib/rs)\n");

            sb.append("LOCAL_C_INCLUDES += $(TARGET_C_INCLUDES)/rs/cpp\n");
            sb.append("LOCAL_C_INCLUDES += $(TARGET_C_INCLUDES)/rs\n");
            sb.append("LOCAL_C_INCLUDES += $(TARGET_OBJS)/$(LOCAL_MODULE)\n");
        }

        sb.append("\ninclude $(BUILD_SHARED_LIBRARY)\n");

        Files.write(sb.toString(), makefile, Charsets.UTF_8);
    }

    private void runNdkBuild(@NonNull File ndkLocation, @NonNull File makefile)
            throws ProcessException {
        CoreNdkOptions ndk = getNdkConfig();

        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        String exe = ndkLocation.getAbsolutePath() + File.separator + "ndk-build";
        if (CURRENT_PLATFORM == PLATFORM_WINDOWS && !ndkCygwinMode) {
            exe += ".cmd";
        }
        builder.setExecutable(exe);

        builder.addArgs(
                "NDK_PROJECT_PATH=null",
                "APP_BUILD_SCRIPT=" + makefile.getAbsolutePath());

        // target
        IAndroidTarget target = getBuilder().getTarget();
        if (!target.isPlatform()) {
            target = target.getParent();
        }
        builder.addArgs("APP_PLATFORM=" + target.hashString());

        // temp out
        builder.addArgs("NDK_OUT=" + getObjFolder().getAbsolutePath());

        // libs out
        builder.addArgs("NDK_LIBS_OUT=" + getSoFolder().getAbsolutePath());

        // debug builds
        if (isDebuggable()) {
            builder.addArgs("NDK_DEBUG=1");
        }

        if (ndk.getStl() != null) {
            builder.addArgs("APP_STL=" + ndk.getStl());
        }

        Set<String> abiFilters = ndk.getAbiFilters();
        if (abiFilters != null && !abiFilters.isEmpty()) {
            if (abiFilters.size() == 1) {
                builder.addArgs("APP_ABI=" + abiFilters.iterator().next());
            } else {
                Joiner joiner = Joiner.on(',').skipNulls();
                builder.addArgs("APP_ABI=" + joiner.join(abiFilters.iterator()));
            }
        } else {
            builder.addArgs("APP_ABI=all");
        }

        if (ndk.getJobs() != null) {
            builder.addArgs("-j" + ndk.getJobs());
        }

        ProcessOutputHandler handler = new LoggedProcessOutputHandler(getBuilder().getLogger());
        getBuilder().executeProcess(builder.createProcess(), handler)
                .rethrowFailure().assertNormalExitValue();
    }

    private boolean isNdkOptionUnset() {
        // If none of the NDK options are set, then it is likely that NDK is not configured.
        return (getModuleName() == null &&
                getcFlags() == null &&
                getLdLibs() == null &&
                getAbiFilters() == null &&
                getStl() == null);
    }

    public static class ConfigAction implements TaskConfigAction<NdkCompile> {

        @NonNull private final VariantScope variantScope;

        public ConfigAction(@NonNull VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("compile", "Ndk");
        }

        @NonNull
        @Override
        public Class<NdkCompile> getType() {
            return NdkCompile.class;
        }

        @Override
        public void execute(@NonNull NdkCompile ndkCompile) {
            final BaseVariantData variantData = variantScope.getVariantData();

            ndkCompile.setAndroidBuilder(variantScope.getGlobalScope().getAndroidBuilder());
            ndkCompile.setVariantName(variantData.getName());
            ndkCompile.setNdkDirectory(
                    variantScope.getGlobalScope().getSdkHandler().getNdkFolder());
            ndkCompile.setForTesting(variantData.getType().isForTesting());
            ndkCompile.isUseDeprecatedNdkFlag =
                    variantScope
                            .getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.ENABLE_DEPRECATED_NDK);
            ndkCompile.isDeprecatedNdkCompileLeaseExpired =
                    NdkLease.isDeprecatedNdkCompileLeaseExpired(
                            variantScope.getGlobalScope().getProjectOptions());
            variantData.ndkCompileTask = ndkCompile;

            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

            if (Boolean.TRUE
                    .equals(variantConfig.getMergedFlavor().getRenderscriptNdkModeEnabled())) {
                ndkCompile.setNdkRenderScriptMode(true);
            } else {
                ndkCompile.setNdkRenderScriptMode(false);
            }

            final Callable<Collection<File>> callable =
                    TaskInputHelper.bypassFileCallable(
                            () -> {
                                Collection<File> sourceList = variantConfig.getJniSourceList();
                                if (Boolean.TRUE.equals(
                                        variantConfig
                                                .getMergedFlavor()
                                                .getRenderscriptNdkModeEnabled())) {
                                    sourceList.add(
                                            variantData.renderscriptCompileTask
                                                    .getSourceOutputDir());
                                }

                                return sourceList;
                            });
            ndkCompile.sourceFolders = variantScope.getGlobalScope().getProject().files(callable);

            ndkCompile.setGeneratedMakefile(
                    new File(
                            variantScope.getGlobalScope().getIntermediatesDir(),
                            "ndk/"
                                    + variantData.getVariantConfiguration().getDirName()
                                    + "/Android.mk"));

            ndkCompile.setNdkConfig(variantConfig.getNdkConfig());
            ndkCompile.setDebuggable(variantConfig.getBuildType().isJniDebuggable());

            ndkCompile.setObjFolder(
                    new File(
                            variantScope.getGlobalScope().getIntermediatesDir(),
                            "ndk/" + variantData.getVariantConfiguration().getDirName() + "/obj"));

            Collection<File> ndkSoFolder = variantScope.getNdkSoFolder();
            if (ndkSoFolder != null && !ndkSoFolder.isEmpty()) {
                ndkCompile.setSoFolder(ndkSoFolder.iterator().next());
            }
        }
    }
}
