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

package com.android.build.gradle.tasks;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.external.gson.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.BuildCommandException;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.FileBackedOutputStream;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared utility methods for dealing with external native build tasks.
 */
public class ExternalNativeBuildTaskUtils {
    // Forked CMake version is the one we get when we execute "cmake --version" commond.
    public static final String CUSTOM_FORK_CMAKE_VERSION = "3.6.0-rc2";

    /**
     * Utility function that takes an ABI string and returns the corresponding output folder. Output
     * folder is where build artifacts are placed.
     */
    @NonNull
    static File getOutputFolder(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(jsonFolder, abi);
    }

    /**
     * Utility function that gets the name of the output JSON for a particular ABI.
     */
    @NonNull
    public static File getOutputJson(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(getOutputFolder(jsonFolder, abi), "android_gradle_build.json");
    }

    /** Utility function that gets the name of the output JSON for a particular ABI. */
    @NonNull
    public static File getCompileCommandsJson(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(getOutputFolder(jsonFolder, abi), "compile_commands.json");
    }

    @NonNull
    public static List<File> getOutputJsons(@NonNull File jsonFolder,
            @NonNull Collection<String> abis) {
        List<File> outputs = Lists.newArrayList();
        for (String abi : abis) {
            outputs.add(getOutputJson(jsonFolder, abi));
        }
        return outputs;
    }

    /**
     * Deserialize a JSON file into NativeBuildConfigValue. Emit task-specific exception if there is
     * an issue.
     */
    @NonNull
    static NativeBuildConfigValue getNativeBuildConfigValue(
            @NonNull File json,
            @NonNull String groupName) throws IOException {
        checkArgument(!Strings.isNullOrEmpty(groupName),
                "group name missing in", json);

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                .create();
        List<String> lines = Files.readLines(json, Charsets.UTF_8);
        NativeBuildConfigValue config = gson.fromJson(Joiner.on("\n").join(lines),
                NativeBuildConfigValue.class);
        if (config.libraries == null) {
            return config;
        }
        for (NativeLibraryValue library : config.libraries.values()) {
            library.groupName = groupName;
        }
        return config;
    }

    /**
     * Deserialize a JSON files into NativeBuildConfigValue.
     */
    @NonNull
    public static Collection<NativeBuildConfigValue> getNativeBuildConfigValues(
            @NonNull Collection<File> jsons,
            @NonNull String groupName) throws IOException {
        List<NativeBuildConfigValue> configValues = Lists.newArrayList();
        for (File json : jsons) {
            configValues.add(getNativeBuildConfigValue(json, groupName));
        }
        return configValues;
    }

    /** Return true if we should regenerate out-of-date JSON files. */
    public static boolean shouldRegenerateOutOfDateJsons(@NonNull ProjectOptions options) {
        return options.get(BooleanOption.IDE_BUILD_MODEL_ONLY)
                || options.get(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED)
                || options.get(BooleanOption.IDE_INVOKED_FROM_IDE)
                || options.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL);
    }

    public static boolean isExternalNativeBuildEnabled(@NonNull CoreExternalNativeBuild config) {
        return (config.getNdkBuild().getPath() != null)
                || (config.getCmake().getPath() != null);
    }

    public static class ExternalNativeBuildProjectPathResolution {
        @Nullable
        public final String errorText;
        @Nullable
        public final NativeBuildSystem buildSystem;
        @Nullable
        public final File makeFile;
        @Nullable public final File externalNativeBuildDir;

        private ExternalNativeBuildProjectPathResolution(
                @Nullable NativeBuildSystem buildSystem,
                @Nullable File makeFile,
                @Nullable File externalNativeBuildDir,
                @Nullable String errorText) {
            checkArgument(makeFile == null || buildSystem != null,
                    "Expected path and buildSystem together, no taskClass");
            checkArgument(makeFile != null || buildSystem == null,
                    "Expected path and buildSystem together, no path");
            checkArgument(makeFile == null || errorText == null,
                    "Expected path or error but both existed");
            this.buildSystem = buildSystem;
            this.makeFile = makeFile;
            this.externalNativeBuildDir = externalNativeBuildDir;
            this.errorText = errorText;
        }
    }

    /**
     * Resolve the path of any native build project.
     *
     * @param config -- the AndroidConfig
     * @return Path resolution.
     */
    @NonNull
    public static ExternalNativeBuildProjectPathResolution getProjectPath(
            @NonNull CoreExternalNativeBuild config) {
        // Path discovery logic:
        // If there is exactly 1 path in the DSL, then use it.
        // If there are more than 1, then that is an error. The user has specified both cmake and
        //    ndkBuild in the same project.

        Map<NativeBuildSystem, File> externalProjectPaths = getExternalBuildExplicitPaths(config);
        if (externalProjectPaths.size() > 1) {
            return new ExternalNativeBuildProjectPathResolution(
                    null, null, null, "More than one externalNativeBuild path specified");
        }

        if (externalProjectPaths.isEmpty()) {
            // No external projects present.
            return new ExternalNativeBuildProjectPathResolution(null, null, null, null);
        }

        NativeBuildSystem buildSystem = externalProjectPaths.keySet().iterator().next();
        return new ExternalNativeBuildProjectPathResolution(
                buildSystem,
                externalProjectPaths.get(buildSystem),
                getExternalNativeBuildPath(config).get(buildSystem),
                null);
    }

    /**
     * Writes the given object as JSON to the given json file.
     *
     * @throws IOException I/O failure
     */
    public static void writeNativeBuildConfigValueToJsonFile(
            @NonNull File outputJson, @NonNull NativeBuildConfigValue nativeBuildConfigValue)
            throws IOException {
        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                        .disableHtmlEscaping()
                        .setPrettyPrinting()
                        .create();

        FileWriter jsonWriter = new FileWriter(outputJson);
        gson.toJson(nativeBuildConfigValue, jsonWriter);
        jsonWriter.close();
    }

    /**
     * @return a map of generate task to path from DSL. Zero entries means there are no paths in the
     *     DSL. Greater than one entries means that multiple paths are specified, this is an error.
     */
    @NonNull
    private static Map<NativeBuildSystem, File> getExternalBuildExplicitPaths(
            @NonNull CoreExternalNativeBuild config) {
        Map<NativeBuildSystem, File> map = new EnumMap<>(NativeBuildSystem.class);
        File cmake = config.getCmake().getPath();
        File ndkBuild = config.getNdkBuild().getPath();

        if (cmake != null) {
            map.put(NativeBuildSystem.CMAKE, cmake);
        }
        if (ndkBuild != null) {
            map.put(NativeBuildSystem.NDK_BUILD, ndkBuild);
        }
        return map;
    }

    @NonNull
    private static Map<NativeBuildSystem, File> getExternalNativeBuildPath(
            @NonNull CoreExternalNativeBuild config) {
        Map<NativeBuildSystem, File> map = new EnumMap<>(NativeBuildSystem.class);
        File cmake = config.getCmake().getBuildStagingDirectory();
        File ndkBuild = config.getNdkBuild().getBuildStagingDirectory();
        if (cmake != null) {
            map.put(NativeBuildSystem.CMAKE, cmake);
        }
        if (ndkBuild != null) {
            map.put(NativeBuildSystem.NDK_BUILD, ndkBuild);
        }

        return map;
    }

    /**
     * Execute an external process and log the result in the case of a process exceptions. Returns
     * the info part of the log so that it can be parsed by ndk-build parser;
     *
     * @throws BuildCommandException when the build failed.
     */
    @NonNull
    public static String executeBuildProcessAndLogError(
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ProcessInfoBuilder process,
            boolean logStdioToInfo)
            throws BuildCommandException, IOException {
        ProgressiveLoggingProcessOutputHandler handler =
                new ProgressiveLoggingProcessOutputHandler(androidBuilder.getLogger(),
                        logStdioToInfo);
        try {
            // Log the command to execute but only in verbose (ie --info)
            androidBuilder.getLogger().verbose(process.toString());
            androidBuilder.executeProcess(process.createProcess(), handler)
                    .rethrowFailure().assertNormalExitValue();

            return handler.getStandardOutputString();
        } catch (ProcessException e) {
            // Also, add process output to the process exception so that it can be analyzed by
            // caller. Use combined stderr stdout instead of just stdout because compiler errors
            // go to stdout.
            String combinedMessage = String.format("%s\n%s", e.getMessage(),
                    handler.getCombinedOutputString());
            throw new BuildCommandException(combinedMessage);
        }
    }

    /**
     * Returns the folder with the CMake binary. For more info, check the comments on
     * doFindCmakeExecutableFolder below.
     *
     * @param sdkHandler sdk handler
     * @return Folder with the required CMake binary
     */
    @NonNull
    public static File findCmakeExecutableFolder(
            @NonNull String cmakeVersion, @NonNull SdkHandler sdkHandler) {
        return doFindCmakeExecutableFolder(cmakeVersion, sdkHandler, getEnvironmentPathList());
    }

    /**
     * @return array of folders (as Files) retrieved from PATH environment variable and from Sdk
     *     cmake folder.
     */
    @NonNull
    private static List<File> getEnvironmentPathList() {
        List<File> fileList = new ArrayList<>();
        String envPath = System.getenv("PATH");

        List<String> pathList = new ArrayList<>();
        if (envPath != null) {
            pathList.addAll(Arrays.asList(envPath.split(System.getProperty("path.separator"))));
        }

        for (String path : pathList) {
            fileList.add(new File(path));
        }

        return fileList;
    }

    /**
     * Returns the folder with the CMake binary. There are 3 possible places to find/look the CMake
     * binary path:
     *
     * <p>- First search the path specified in the local properties, return if one is available.
     *
     * <p>- Check the version in externalNativeBuild in app's build.gradle, if one is specified,
     * search for a CMake binary that matches the version specified. Note: the version should be an
     * exact match. Return the path if one is found. Note: If the version matches CMake installed in
     * SDK (forked-cmake or vanilla-cmake), then do not search the path, just look for it within the
     * SDK.
     *
     * <p>- Find CMake in the Sdk folder (or install CMake if it's unavailable) and return the CMake
     * folder.
     *
     * @param sdkHandler sdk handler
     * @param foldersToSearch folders to search if not found specified in local.properties
     * @return Folder with the required CMake binary
     */
    @VisibleForTesting
    @NonNull
    static File doFindCmakeExecutableFolder(
            @Nullable String cmakeVersion,
            @NonNull SdkHandler sdkHandler,
            @NonNull List<File> foldersToSearch) {
        if (sdkHandler.getCmakePathInLocalProp() != null) {
            return sdkHandler.getCmakePathInLocalProp();
        }

        if (cmakeVersion != null && !isDefaultSdkCmakeVersion(cmakeVersion)) {
            // getRequiredCmakeFromFolders will throw a RuntimeException with errors if it is unable
            // to find the required CMake.
            File cmakeFolder =
                    getRequiredCmakeFromFolders(
                            Revision.parseRevision(cmakeVersion), foldersToSearch);
            return new File(cmakeFolder.getParent());
        }

        return getCmakeFolderFromSdkPackage(sdkHandler);
    }

    /**
     * By default, in SDK we support CMake versions (be it forked-cmake or vanilla-cmake). This
     * function returns true if its one of those versions.
     */
    private static boolean isDefaultSdkCmakeVersion(@NonNull String cmakeVersion) {
        // TODO(kravindran) Add vanilla CMake version information once its in the SDK.
        return (cmakeVersion.equals(CUSTOM_FORK_CMAKE_VERSION));
    }

    /**
     * Returns a CMake folder which has CMake binary that exactly matches the cmake version
     * specified.
     *
     * @param cmakeVersion - cmake binary with the version to search for.
     * @param foldersToSearch folders to search if not found specified in local.properties
     * @return CMake binary folder
     */
    @NonNull
    private static File getRequiredCmakeFromFolders(
            @NonNull Revision cmakeVersion, @NonNull List<File> foldersToSearch) {
        List<File> foldersWithErrors = new ArrayList<>();
        for (File cmakeFolder : foldersToSearch) {
            // Check if cmake executable is present, if not continue searching.
            File cmakeBin;
            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
                cmakeBin = new File(cmakeFolder, "cmake.exe");
            } else {
                cmakeBin = new File(cmakeFolder, "cmake");
            }

            if (!cmakeBin.exists()) {
                continue;
            }
            try {
                Revision version = CmakeUtils.getVersion(cmakeFolder);
                if (cmakeVersion.equals(version)) {
                    return cmakeFolder;
                }
            } catch (IOException e) {
                // Ignore if we get an exception when trying to find the version. It could be due to
                // corrupt/inaccessible cmake, we'll search other locations instead.
                foldersWithErrors.add(cmakeFolder);
            }
        }

        StringBuilder errorMsg =
                new StringBuilder(
                        String.format(
                                "Unable to find CMake with version: %s within folder: %s\n.",
                                cmakeVersion, foldersToSearch.toString()));

        if (!foldersWithErrors.isEmpty()) {
            errorMsg.append(
                    String.format(
                            "Folders have inaccessible/corrupt CMake: %s",
                            foldersWithErrors.toString()));
        }

        errorMsg.append(
                "Please make sure the folder to CMake binary is added to the PATH environment"
                        + "variable.");

        throw new RuntimeException(errorMsg.toString());
    }

    /**
     * Returns the CMake folder installed within Sdk folder, if one is not present, install CMake
     * and return the CMake folder.
     */
    @NonNull
    private static File getCmakeFolderFromSdkPackage(@NonNull SdkHandler sdkHandler) {
        ProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler sdk = AndroidSdkHandler.getInstance(sdkHandler.getSdkFolder());
        LocalPackage cmakePackage =
                sdk.getLatestLocalPackageForPrefix(SdkConstants.FD_CMAKE, null, true, progress);
        if (cmakePackage != null) {
            return cmakePackage.getLocation();
        }
        // If CMake package is not found, we install it and try to find it.
        sdkHandler.installCMake();
        cmakePackage =
                sdk.getLatestLocalPackageForPrefix(SdkConstants.FD_CMAKE, null, true, progress);
        if (cmakePackage != null) {
            return cmakePackage.getLocation();
        }

        return new File(sdkHandler.getSdkFolder(), SdkConstants.FD_CMAKE);
    }

    /**
     * A process output handler that receives STDOUT and STDERR progressively (as it is happening)
     * and logs the output line-by-line to Gradle. This class also collected precise byte-for-byte
     * output.
     */
    private static class ProgressiveLoggingProcessOutputHandler implements ProcessOutputHandler {
        @NonNull
        private final ILogger logger;
        @NonNull private final FileBackedOutputStream standardOutput;
        @NonNull private final FileBackedOutputStream combinedOutput;
        @NonNull
        private final ProgressiveLoggingProcessOutput loggingProcessOutput;
        private final boolean logStdioToInfo;

        public ProgressiveLoggingProcessOutputHandler(
                @NonNull ILogger logger, boolean logStdioToInfo) {
            this.logger = logger;
            this.logStdioToInfo = logStdioToInfo;
            standardOutput = new FileBackedOutputStream(2048);
            combinedOutput = new FileBackedOutputStream(2048);
            loggingProcessOutput = new ProgressiveLoggingProcessOutput();
        }

        @NonNull
        String getStandardOutputString() throws IOException {
            return standardOutput.asByteSource().asCharSource(Charsets.UTF_8).read();
        }

        @NonNull
        String getCombinedOutputString() throws IOException {
            return combinedOutput.asByteSource().asCharSource(Charsets.UTF_8).read();
        }

        @NonNull
        @Override
        public ProcessOutput createOutput() {
            return loggingProcessOutput;
        }

        @Override
        public void handleOutput(@NonNull ProcessOutput processOutput) throws ProcessException {
            // Nothing to do here because the process output is handled as it comes in.
        }

        private class ProgressiveLoggingProcessOutput implements ProcessOutput {
            @NonNull
            private final ProgressiveLoggingOutputStream outputStream;
            @NonNull
            private final ProgressiveLoggingOutputStream errorStream;

            ProgressiveLoggingProcessOutput() {
                outputStream = new ProgressiveLoggingOutputStream(logStdioToInfo, standardOutput);
                errorStream = new ProgressiveLoggingOutputStream(true /* logStdioToInfo */, null);
            }

            @NonNull
            @Override
            public ProgressiveLoggingOutputStream getStandardOutput() {
                return outputStream;
            }

            @NonNull
            @Override
            public ProgressiveLoggingOutputStream getErrorOutput() {
                return errorStream;
            }

            @Override
            public void close() throws IOException {}

            private class ProgressiveLoggingOutputStream extends OutputStream {
                private static final int INITIAL_BUFFER_SIZE = 256;
                @NonNull
                byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
                int nextByteIndex = 0;
                private final boolean logToInfo;
                private final FileBackedOutputStream individualOutput;

                ProgressiveLoggingOutputStream(
                        boolean logToInfo, FileBackedOutputStream individualOutput) {
                    this.logToInfo = logToInfo;
                    this.individualOutput = individualOutput;
                }

                @Override
                public void write(int b) throws IOException {
                    combinedOutput.write(b);
                    if (individualOutput != null) {
                        individualOutput.write(b);
                    }
                    // Check for /r and /n respectively
                    if (b == 0x0A || b == 0x0D) {
                        printBuffer();
                    } else {
                        writeBuffer(b);
                    }
                }

                private void writeBuffer(int b) {
                    if (nextByteIndex == buffer.length) {
                        buffer = Arrays.copyOf(buffer, buffer.length * 2);
                    }
                    buffer[nextByteIndex] = (byte) b;
                    nextByteIndex++;
                }

                private void printBuffer() throws UnsupportedEncodingException {
                    if (nextByteIndex == 0) {
                        return;
                    }
                    String line = new String(buffer, 0, nextByteIndex, "UTF-8");
                    if (logToInfo) {
                        logger.info(line);
                    }
                    nextByteIndex = 0;
                }

                @Override
                public void flush() throws IOException {
                    printBuffer();
                }

                @Override
                public void close() throws IOException {
                    printBuffer();
                }
            }
        }
    }
}
