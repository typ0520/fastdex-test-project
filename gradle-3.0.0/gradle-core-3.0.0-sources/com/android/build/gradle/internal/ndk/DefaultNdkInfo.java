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

package com.android.build.gradle.internal.ndk;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.Toolchain;
import com.android.repository.Revision;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.android.utils.ImmutableCollectors;
import com.android.utils.Pair;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.logging.Logging;

/**
 * Default NdkInfo.  Used for r10 and r11.
 */
public class DefaultNdkInfo implements NdkInfo {

    private static final String ABI_LIST_FILE = "meta/abis.json";

    private final File root;

    private final List<AbiInfo> abiInfoList;

    private final Map<Pair<Toolchain, Abi>, String> defaultToolchainVersions = Maps.newHashMap();

    public DefaultNdkInfo(@NonNull File root) {
        this.root = root;
        File abiFile = new File(root, ABI_LIST_FILE);
        if (abiFile.isFile()) {
            Map<String, AbiInfo> infoMap;
            try {
                infoMap =
                        new Gson()
                                .fromJson(
                                        new FileReader(abiFile),
                                        new TypeToken<Map<String, AbiInfo>>() {}.getType());
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Unreachable. Unable to find abi list file: " + abiFile);
            } catch (JsonParseException e) {
                Logging.getLogger(this.getClass())
                        .warn(
                                "WARNING: Error parsing ABI metadata file '"
                                        + abiFile
                                        + "'.  Using "
                                        + "default ABI list.");
                abiInfoList = getDefaultAbiInfoList();
                return;
            }
            ImmutableList.Builder<AbiInfo> builder = ImmutableList.builder();
            for (Map.Entry<String, AbiInfo> entry : infoMap.entrySet()) {
                Abi abi = Abi.getByName(entry.getKey());
                if (abi == null) {
                    Logging.getLogger(this.getClass())
                            .warn(
                                    "WARNING: Ignoring invalid ABI '"
                                            + entry.getKey()
                                            + "' found in ABI metadata file '"
                                            + abiFile
                                            + "'.");
                    continue;
                }
                builder.add(
                        new AbiInfo(
                                abi,
                                entry.getValue().isDeprecated(),
                                entry.getValue().isDefault()));
            }
            abiInfoList = builder.build();
        } else {
            abiInfoList = getDefaultAbiInfoList();
        }
    }

    private static List<AbiInfo> getDefaultAbiInfoList() {
        return Arrays.stream(Abi.values())
                .map(abi -> new AbiInfo(abi, false, true))
                .collect(ImmutableCollectors.toImmutableList());
    }

    @Override
    @NonNull
    public File getRootDirectory() {
        return root;
    }

    /**
     * Returns the sysroot path for compilation.
     *
     * <p>If unified headers is enabled, this will be different from getLinkerSysrootPath. They will
     * be the same otherwise.
     */
    @Override
    @NonNull
    public String getCompilerSysrootPath(
            @NonNull Abi abi, @NonNull String platformVersion, boolean useUnifiedHeaders) {
        if (useUnifiedHeaders) {
            return FileUtils.join(root.getPath(), "sysroot");
        } else {
            return getLinkerSysrootPath(abi, platformVersion);
        }
    }

    @Override
    @NonNull
    public String getLinkerSysrootPath(@NonNull Abi abi, @NonNull String platformVersion) {
        return FileUtils.join(
                root.getPath(), "platforms", platformVersion, "arch-" + abi.getArchitecture());
    }

    /**
     * Retrieve the newest supported version if it is not the specified version is not supported.
     *
     * An older NDK may not support the specified compiledSdkVersion.  In that case, determine what
     * is the newest supported version and modify compileSdkVersion.
     */
    @Override
    @Nullable
    public String findLatestPlatformVersion(@NonNull String targetPlatformString) {

        AndroidVersion androidVersion = AndroidTargetHash.getVersionFromHash(targetPlatformString);
        int targetVersion;
        if (androidVersion == null) {
            Logging.getLogger(this.getClass()).warn(
                    "Unable to parse NDK platform version.  Try to find the latest instead.");
            targetVersion = Integer.MAX_VALUE;
        } else {
            targetVersion = androidVersion.getFeatureLevel();
        }
        targetVersion = findTargetPlatformVersionOrLower(targetVersion);
        if (targetVersion == 0) {
            return null;
        }
        return "android-" + targetVersion;
    }

    /**
     *  Find suitable platform for the given ABI.
     *
     *  (1) If platforms/android-[min sdk]/arch-[ABI] exists, then use the min sdk as platform for
     *      that ABI
     *
     *  (2) If there exists platforms/android-[platform]/arch-[ABI] such that platform greater-than
     *      min sdk, use max(platform where platform less than min sdk)
     *
     *  (3) Use min(platform where platforms/android-[platform]/arch-[ABI] exists)
     */
    @Override
    public int findSuitablePlatformVersion(@NonNull String abiName, int minSdkVersion) {
        Abi abi = Abi.getByName(abiName);
        if (abi == null) {
            // This ABI is not recognized
            return 0;
        }

        // If platforms/android-[min sdk]/arch-[ABI] exists, then use the min sdk as platform for
        // that ABI
        File platformDir = FileUtils.join(root, "platforms");
        checkState(platformDir.isDirectory());
        if (new File(getLinkerSysrootPath(abi, "android-" + minSdkVersion)).isDirectory()) {
            return minSdkVersion;
        }

        // Walk over the platform folders that contain this ABI
        File[] platformSubDirs = platformDir.listFiles(File::isDirectory);

        int highestVersionBelowMinSdk = 0;
        int lowestVersionOverall = 0;
        for(File platform : platformSubDirs) {
            if (platform.getName().startsWith("android-")) {
                if (FileUtils.join(platform, "arch-" + abi.getArchitecture()).isDirectory()) {
                    try {
                        int version = Integer.parseInt(
                                platform.getName().substring("android-".length()));
                        if (version > highestVersionBelowMinSdk && version < minSdkVersion) {
                            highestVersionBelowMinSdk = version;
                        }
                        if (lowestVersionOverall == 0 || version < lowestVersionOverall) {
                            lowestVersionOverall = version;
                        }
                    } catch (NumberFormatException ignore) {
                        // Ignore unrecognized directories.
                    }
                }
            }
        }

        // If there exists platforms/android-[platform]/arch-[ABI] such that platform < min sdk,
        // use max(platform where platform < min sdk)
        if (highestVersionBelowMinSdk > 0) {
            return highestVersionBelowMinSdk;
        }

        // Use min(platform where platforms/android-[platform]/arch-[ABI] exists)
        checkState(lowestVersionOverall > 0,
                String.format("Expected caller to ensure valid ABI: %s", abi));
        return lowestVersionOverall;
    }

    // Will return 0 if no platform found
    private int findTargetPlatformVersionOrLower(int targetVersion) {
        File platformDir = new File(root, "/platforms");
        if (new File(platformDir, "android-" + targetVersion).exists()) {
            return targetVersion;
        } else {
            File[] platformSubDirs = platformDir.listFiles(File::isDirectory);
            int highestVersion = 0;
            for(File platform : platformSubDirs) {
                if (platform.getName().startsWith("android-")) {
                    try {
                        int version = Integer.parseInt(
                                platform.getName().substring("android-".length()));
                        if (version > highestVersion && version < targetVersion) {
                            highestVersion = version;
                        }
                    } catch(NumberFormatException ignore) {
                        // Ignore unrecognized directories.
                    }
                }
            }
            return highestVersion;
        }
    }

    private static String getToolchainPrefix(Toolchain toolchain, Abi abi) {
        if (toolchain == Toolchain.GCC) {
            return abi.getGccToolchainPrefix();
        } else {
            return "llvm";
        }
    }

    /**
     * Return the directory containing the toolchain.
     *
     * @param toolchain toolchain to use.
     * @param toolchainVersion toolchain version to use.
     * @param abi target ABI of the toolchaina
     * @return a directory that contains the executables.
     */
    @Override
    @NonNull
    public File getToolchainPath(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi) {
        abi = getToolchainAbi(abi);
        String version = toolchainVersion.isEmpty()
                ? getDefaultToolchainVersion(toolchain, abi)
                : toolchainVersion;
        version = version.isEmpty() ? "" : "-" + version;  // prepend '-' if non-empty.

        File prebuiltFolder = new File(
                getRootDirectory(),
                "toolchains/" + getToolchainPrefix(toolchain, abi) + version + "/prebuilt");

        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String hostOs;
        if (osName.contains("windows")) {
            hostOs = "windows";
        } else if (osName.contains("mac")) {
            hostOs = "darwin";
        } else {
            hostOs = "linux";
        }

        // There should only be one directory in the prebuilt folder.  If there are more than one
        // attempt to determine the right one based on the operating system.
        File[] toolchainPaths = prebuiltFolder.listFiles(File::isDirectory);

        if (toolchainPaths == null) {
            throw new InvalidUserDataException("Unable to find toolchain: " + prebuiltFolder);
        }
        if (toolchainPaths.length == 1) {
            return toolchainPaths[0];
        }

        // Use 64-bit toolchain if available.
        File toolchainPath = new File(prebuiltFolder, hostOs + "-x86_64");
        if (toolchainPath.isDirectory()) {
            return toolchainPath;
        }

        // Fallback to 32-bit if we can't find the 64-bit toolchain.
        String osString = (osName.equals("windows")) ? hostOs : hostOs + "-x86";
        toolchainPath = new File(prebuiltFolder, osString);
        if (toolchainPath.isDirectory()) {
            return toolchainPath;
        } else {
            throw new InvalidUserDataException("Unable to find toolchain prebuilt folder in: "
                    + prebuiltFolder);
        }
    }

    @NonNull
    protected Abi getToolchainAbi(@NonNull Abi abi) {
        return abi;
    }

    /**
     * Return the executable for compiling C code.
     */
    @Override
    @NonNull
    public File getCCompiler(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi) {
        abi = getToolchainAbi(abi);
        String compiler = toolchain == Toolchain.CLANG ? "clang" : abi.getGccExecutablePrefix() + "-gcc";
        return new File(getToolchainPath(toolchain, toolchainVersion, abi), "bin/" + compiler);
    }

    /**
     * Return the executable for compiling C++ code.
     */
    @Override
    @NonNull
    public File getCppCompiler(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi) {
        abi = getToolchainAbi(abi);
        String compiler = toolchain == Toolchain.CLANG ? "clang++" : abi.getGccExecutablePrefix() + "-g++";
        return new File(getToolchainPath(toolchain, toolchainVersion, abi), "bin/" + compiler);
    }

    /**
     * Return the linker.
     */
    @Override
    @NonNull
    public File getLinker(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi) {
        return getCppCompiler(toolchain, toolchainVersion, abi);
    }

    /**
     * Return the assembler.
     */
    @Override
    @NonNull
    public File getAssembler(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi) {
        return getCCompiler(toolchain, toolchainVersion, abi);
    }

    @Override
    @NonNull
    public File getAr(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi) {
        abi = getToolchainAbi(abi);
        // For clang, we use the ar from the GCC toolchain.
        String ar = abi.getGccExecutablePrefix()
                + (toolchain == Toolchain.CLANG ? "-ar" : "-gcc-ar");
        return new File(
                getToolchainPath(Toolchain.GCC, getDefaultToolchainVersion(Toolchain.GCC, abi), abi),
                "bin/" + ar);
    }

    /**
     * Return the executable for removing debug symbols from a shared object.
     */
    @Override
    @NonNull
    public File getStripExecutable(Toolchain toolchain, String toolchainVersion, Abi abi) {
        abi = getToolchainAbi(abi);
        return FileUtils.join(
                getToolchainPath(
                        Toolchain.GCC,
                        toolchain == Toolchain.GCC
                                ? toolchainVersion
                                : getDefaultToolchainVersion(Toolchain.GCC, abi),
                        abi),
                "bin",
                abi.getGccExecutablePrefix() + "-strip");
    }


    /**
     * Return the default version of the specified toolchain for a target abi.
     *
     * The default version is the highest version found in the NDK for the specified toolchain and
     * ABI.  The result is cached for performance.
     */
    @Override
    @NonNull
    public String getDefaultToolchainVersion(@NonNull Toolchain toolchain, @NonNull Abi abi) {
        abi = getToolchainAbi(abi);
        String defaultVersion = defaultToolchainVersions.get(Pair.of(toolchain, abi));
        if (defaultVersion != null) {
            return defaultVersion;
        }

        final String toolchainPrefix = getToolchainPrefix(toolchain, abi);
        File toolchains = new File(getRootDirectory(), "toolchains");
        File[] toolchainsForAbi = toolchains.listFiles(
                (dir, filename) -> filename.startsWith(toolchainPrefix));
        if (toolchainsForAbi == null || toolchainsForAbi.length == 0) {
            throw new RuntimeException(
                    "No toolchains found in the NDK toolchains folder for ABI with prefix: "
                            + toolchainPrefix);
        }

        // Once we have a list of toolchains, we look the highest version
        Revision bestRevision = null;
        String bestVersionString = "";
        for (File toolchainFolder : toolchainsForAbi) {
            String folderName = toolchainFolder.getName();

            Revision revision = new Revision(0);
            String versionString = "";
            if (folderName.length() > toolchainPrefix.length() + 1) {
                // Find version if folderName is in the form {prefix}-{version}
                try {
                    versionString = folderName.substring(toolchainPrefix.length() + 1);
                    revision = Revision.parseRevision(versionString);
                } catch (NumberFormatException ignore) {
                }
            }
            if (bestRevision == null || revision.compareTo(bestRevision) > 0) {
                bestRevision = revision;
                bestVersionString = versionString;
            }
        }
        defaultToolchainVersions.put(Pair.of(toolchain, abi), bestVersionString);
        if (bestRevision == null) {
            throw new RuntimeException("Unable to find a valid toolchain in " + toolchains);
        }
        return bestVersionString;
    }

    @Override
    @NonNull
    public StlNativeToolSpecification getStlNativeToolSpecification(
            @NonNull Stl stl,
            @NonNull String stlVersion,
            @NonNull Abi abi) {
        StlSpecification spec =
                new DefaultStlSpecificationFactory()
                        .create(
                                stl,
                                MoreObjects.firstNonNull(
                                        stlVersion, getDefaultToolchainVersion(Toolchain.GCC, abi)),
                                abi);
        return new DefaultStlNativeToolSpecification(this, spec, stl);
    }

    @NonNull
    @Override
    public Collection<Abi> getDefault32BitsAbis() {
        return abiInfoList
                .stream()
                .filter(abiInfo -> abiInfo.isDefault() && !abiInfo.isDeprecated())
                .map(AbiInfo::getAbi)
                .filter(abi -> !abi.supports64Bits())
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<Abi> getDefaultAbis() {
        return abiInfoList
                .stream()
                .filter(abiInfo -> abiInfo.isDefault() && !abiInfo.isDeprecated())
                .map(AbiInfo::getAbi)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<Abi> getSupported32BitsAbis() {
        return abiInfoList
                .stream()
                .map(AbiInfo::getAbi)
                .filter(abi -> !abi.supports64Bits())
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<Abi> getSupportedAbis() {
        return abiInfoList.stream().map(AbiInfo::getAbi).collect(Collectors.toList());
    }
}
