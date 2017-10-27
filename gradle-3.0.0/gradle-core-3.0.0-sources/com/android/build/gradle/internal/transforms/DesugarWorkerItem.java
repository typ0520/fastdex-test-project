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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.builder.core.DesugarProcessBuilder;
import com.google.common.collect.ImmutableList;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;

/** ToDo: replace DesugarProcessBuilder */
public class DesugarWorkerItem {

    private static final String DESUGAR_MAIN = "com.google.devtools.build.android.desugar.Desugar";
    private static final Logger LOGGER = Logging.getLogger(DesugarWorkerItem.class);

    @NonNull private final Path java8LangSupportJar;
    @NonNull private final Path tmpFolder;
    private final boolean verbose;
    @NonNull private final Path input;
    @NonNull private final Path output;
    @NonNull private final List<Path> classpath;
    @NonNull private final List<Path> bootClasspath;
    private final int minSdkVersion;

    public DesugarWorkerItem(
            @NonNull Path java8LangSupportJar,
            @NonNull Path tmpFolder,
            boolean verbose,
            @NonNull Path input,
            @NonNull Path output,
            @NonNull List<Path> classpath,
            @NonNull List<Path> bootClasspath,
            int minSdkVersion) {
        this.java8LangSupportJar = java8LangSupportJar;
        this.tmpFolder = tmpFolder;
        this.verbose = verbose;
        this.input = input;
        this.output = output;
        this.classpath = classpath;
        this.bootClasspath = bootClasspath;
        this.minSdkVersion = minSdkVersion;
    }

    public void configure(WorkerConfiguration workerConfiguration) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "desugar configuring in {}", ManagementFactory.getRuntimeMXBean().getName());
        }
        workerConfiguration.setIsolationMode(IsolationMode.PROCESS);
        workerConfiguration.classpath(ImmutableList.of(java8LangSupportJar.toFile()));
        workerConfiguration.forkOptions(
                javaForkOptions ->
                        javaForkOptions.setJvmArgs(
                                ImmutableList.of(
                                        "-Xmx64m",
                                        "-Djdk.internal.lambda.dumpProxyClasses="
                                                + tmpFolder.toString())));

        workerConfiguration.setParams(
                input.toString(),
                output.toString(),
                classpath.stream().map(Path::toString).collect(Collectors.toList()),
                bootClasspath.stream().map(Path::toString).collect(Collectors.toList()),
                minSdkVersion,
                verbose);
    }

    /**
     * Action running in a separate process to desugar java8 byte codes into java7 compliant byte
     * codes.
     */
    public static class DesugarAction implements Runnable {
        private final boolean verbose;
        @NonNull private final String input;
        @NonNull private final String output;
        @NonNull private final List<String> classpath;
        @NonNull private final List<String> bootClasspath;
        private final int minSdkVersion;

        @Inject
        public DesugarAction(
                @NonNull String input,
                @NonNull String output,
                @NonNull List<String> classpath,
                @NonNull List<String> bootClassPath,
                Integer minSdkVersion,
                Boolean verbose) {
            this.input = input;
            this.output = output;
            this.classpath = classpath;
            this.bootClasspath = bootClassPath;
            this.minSdkVersion = minSdkVersion;
            this.verbose = verbose;
        }

        @Override
        public void run() {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "New desugar in {}", ManagementFactory.getRuntimeMXBean().getName());
                }
                Class<?> clazz = Class.forName(DESUGAR_MAIN);
                Method mainMethod = clazz.getMethod("main", String[].class);
                mainMethod.setAccessible(true);
                ImmutableList.Builder<String> builder = ImmutableList.builder();
                if (verbose) {
                    builder.add("--verbose");
                }

                builder.add("--input", input);
                builder.add("--output", output);
                for (String s : classpath) {
                    builder.add("--classpath_entry", s);
                }
                for (String s : bootClasspath) {
                    builder.add("--bootclasspath_entry", s);
                }

                builder.add("--min_sdk_version", Integer.toString(minSdkVersion));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "desugar parameters for {} are {}",
                            ManagementFactory.getRuntimeMXBean().getName(),
                            builder.build());
                }
                if (minSdkVersion < DesugarProcessBuilder.MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
                    builder.add("--desugar_try_with_resources_if_needed");
                } else {
                    builder.add("--nodesugar_try_with_resources_if_needed");
                }
                builder.add("--desugar_try_with_resources_omit_runtime_classes");
                ImmutableList<String> parameters = builder.build();
                mainMethod.invoke(null, (Object) parameters.toArray(new String[parameters.size()]));

            } catch (ClassNotFoundException
                    | NoSuchMethodException
                    | IllegalAccessException
                    | InvocationTargetException e) {
                LOGGER.error("Error while running desugar ", e);
            }
        }
    }
}
