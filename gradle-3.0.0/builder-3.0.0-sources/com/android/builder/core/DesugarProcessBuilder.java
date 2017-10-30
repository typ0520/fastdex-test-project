/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessEnvBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A builder to create an information necessary to run Desugar in a separate JVM process. */
public final class DesugarProcessBuilder extends ProcessEnvBuilder<DesugarProcessBuilder> {
    public static final int MIN_SUPPORTED_API_TRY_WITH_RESOURCES = 19;
    private static final String DESUGAR_MAIN = "com.google.devtools.build.android.desugar.Desugar";
    /**
     * Windows has limit of 2^15 chars for the command line length; 260 is maximum path length, so
     * allow at most 100 file path args.
     */
    @VisibleForTesting static final int MAX_PATH_ARGS_FOR_WINDOWS = 100;

    @NonNull private final Path java8LangSupportJar;
    private final boolean verbose;
    @NonNull private final Map<Path, Path> inputsToOutputs;
    @NonNull private final List<Path> classpath;
    @NonNull private final List<Path> bootClasspath;
    private final int minSdkVersion;
    @NonNull private final Path tmpDir;

    public DesugarProcessBuilder(
            @NonNull Path java8LangSupportJar,
            boolean verbose,
            @NonNull Map<Path, Path> inputsToOutputs,
            @NonNull List<Path> classpath,
            @NonNull List<Path> bootClasspath,
            int minSdkVersion,
            @NonNull Path tmpDir) {
        this.java8LangSupportJar = java8LangSupportJar;
        this.verbose = verbose;
        this.inputsToOutputs = ImmutableMap.copyOf(inputsToOutputs);
        this.classpath = classpath;
        this.bootClasspath = bootClasspath;
        this.minSdkVersion = minSdkVersion;
        this.tmpDir = tmpDir;
    }

    @NonNull
    public JavaProcessInfo build(boolean isWindows) throws ProcessException, IOException {

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.addEnvironments(mEnvironment);

        builder.setClasspath(java8LangSupportJar.toString());
        builder.setMain(DESUGAR_MAIN);
        builder.addJvmArg("-Xmx64M");

        int pathArgs = 2 * inputsToOutputs.size() + classpath.size() + bootClasspath.size();

        List<String> args = new ArrayList<>(8 * pathArgs + 5);

        if (verbose) {
            args.add("--verbose");
        }
        inputsToOutputs.forEach(
                (in, out) -> {
                    args.add("--input");
                    args.add(in.toString());
                    args.add("--output");
                    args.add(out.toString());
                });
        classpath.forEach(
                c -> {
                    args.add("--classpath_entry");
                    args.add(c.toString());
                });
        bootClasspath.forEach(
                b -> {
                    args.add("--bootclasspath_entry");
                    args.add(b.toString());
                });

        args.add("--min_sdk_version");
        args.add(Integer.toString(minSdkVersion));
        if (minSdkVersion < MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
            args.add("--desugar_try_with_resources_if_needed");
        } else {
            args.add("--nodesugar_try_with_resources_if_needed");
        }
        args.add("--desugar_try_with_resources_omit_runtime_classes");

        if (isWindows && pathArgs > MAX_PATH_ARGS_FOR_WINDOWS) {
            if (!Files.exists(tmpDir)) {
                Files.createDirectories(tmpDir);
            }
            Path argsFile = Files.createTempFile(tmpDir, "desugar_args", "");
            Files.write(argsFile, args, Charsets.UTF_8);

            builder.addArgs("@" + argsFile.toString());
        } else {
            builder.addArgs(args);
        }

        return builder.createJavaProcess();
    }
}
