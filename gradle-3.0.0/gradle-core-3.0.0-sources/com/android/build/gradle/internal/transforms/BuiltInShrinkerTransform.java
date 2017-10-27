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

package com.android.build.gradle.internal.transforms;

import static com.android.build.gradle.shrinker.AbstractShrinker.logTime;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.shrinker.AbstractShrinker.CounterSet;
import com.android.build.gradle.shrinker.DependencyType;
import com.android.build.gradle.shrinker.FullRunShrinker;
import com.android.build.gradle.shrinker.IncrementalShrinker;
import com.android.build.gradle.shrinker.JavaSerializationShrinkerGraph;
import com.android.build.gradle.shrinker.ProguardConfig;
import com.android.build.gradle.shrinker.ProguardParserKeepRules;
import com.android.build.gradle.shrinker.ShrinkerLogger;
import com.android.build.gradle.shrinker.parser.ProguardFlags;
import com.android.build.gradle.shrinker.parser.UnsupportedFlagsHandler;
import com.android.build.gradle.shrinker.tracing.Trace;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.logging.Logging;
import org.gradle.tooling.BuildException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform that performs shrinking - only reachable methods in reachable class files are copied
 * into the output folders (one per stream).
 */
public class BuiltInShrinkerTransform extends ProguardConfigurable {
    private static final Logger LOG = Logging.getLogger(BuiltInShrinkerTransform.class);

    private static final String NAME = "androidGradleClassShrinker";
    private static final UnsupportedFlagsHandler FLAGS_HANDLER = new ShrinkerFlagsHandler();
    private static final Logger logger = LoggerFactory.getLogger(BuiltInShrinkerTransform.class);

    private final Set<File> platformJars;
    private final File incrementalDir;
    private final List<String> addtionalLines;

    public BuiltInShrinkerTransform(@NonNull VariantScope scope) {
        super(scope);
        this.platformJars = ImmutableSet.copyOf(
                scope.getGlobalScope().getAndroidBuilder().getBootClasspath(true));
        this.incrementalDir = scope.getIncrementalDir(scope.getTaskName(NAME));
        this.addtionalLines = Lists.newArrayList();
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return ImmutableList.of(SecondaryFile.nonIncremental(getAllConfigurationFiles()));
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(incrementalDir);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        TransformOutputProvider output = invocation.getOutputProvider();
        Collection<TransformInput> referencedInputs = invocation.getReferencedInputs();

        checkNotNull(output, "Missing output object for transform " + getName());

        if (isIncrementalRun(invocation.isIncremental(), referencedInputs)) {
            incrementalRun(invocation.getInputs(), referencedInputs, output);
        } else {
            fullRun(invocation.getInputs(), referencedInputs, output);
        }
    }

    private void fullRun(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @NonNull TransformOutputProvider output) throws IOException {
        ProguardFlags flags = getProguardFlags();

        ShrinkerLogger shrinkerLogger = new ShrinkerLogger(flags.getDontWarnSpecs(), logger);

        FullRunShrinker<String> shrinker =
                new FullRunShrinker<>(
                        WaitableExecutor.useGlobalSharedThreadPool(),
                        JavaSerializationShrinkerGraph.empty(incrementalDir),
                        platformJars,
                        shrinkerLogger,
                        flags.getBytecodeVersion());

        // Only save state if incremental mode is enabled.
        boolean saveState = this.isIncremental();

        ProguardParserKeepRules whyAreYouKeepingRules = null;
        if (!flags.getWhyAreYouKeepingSpecs().isEmpty()) {
            whyAreYouKeepingRules =
                    ProguardParserKeepRules.whyAreYouKeepingRules(flags, shrinkerLogger);
        }

        FullRunShrinker<String>.Result result =
                shrinker.run(
                        inputs,
                        referencedInputs,
                        output,
                        ImmutableMap.of(
                                CounterSet.SHRINK,
                                ProguardParserKeepRules.keepRules(flags, shrinkerLogger)),
                        whyAreYouKeepingRules,
                        saveState);

        if (!result.traces.isEmpty()) {
            // Print header identical to ProGuard.
            System.out.println("Explaining why classes and class members are being kept...");
            System.out.println();

            printWhyAreYouKeepingExplanation(result.traces, System.out);
        }

        checkForWarnings(flags, shrinkerLogger);
    }

    @VisibleForTesting
    static void printWhyAreYouKeepingExplanation(
            Map<String, Trace<String>> traces, PrintStream out) {
        traces.forEach(
                (node, trace) -> {
                    for (Pair<String, DependencyType> pair : trace.toList()) {
                        out.println(pair.getFirst());
                        out.print("  ");
                        out.print(pair.getSecond());
                        out.print(" from ");
                    }
                });

        out.println("keep rules");
    }

    private static void checkForWarnings(
            @NonNull ProguardFlags flags, @NonNull ShrinkerLogger shrinkerLogger) {
        if (shrinkerLogger.getWarningsCount() > 0 && !flags.isIgnoreWarnings()) {
            throw new BuildException(
                    "Warnings found during shrinking, please use -dontwarn or -ignorewarnings to suppress them.",
                    null);
        }
    }

    @NonNull
    private ProguardFlags getProguardFlags() throws IOException {
        ProguardConfig config = new ProguardConfig();

        for (File configFile : getAllConfigurationFiles()) {
            LOG.info("Applying ProGuard configuration file {}", configFile);

            // the file could not exist if it's published by a library sub-module as the publication
            // happens no matter what the module is doing (in case it's dynamically generated).
            if (configFile.isFile()) {
                config.parse(configFile, FLAGS_HANDLER);
            }
        }

        config.parse(getAdditionalConfigString());

        return config.getFlags();
    }

    @NonNull
    private String getAdditionalConfigString() {
        StringBuilder sb = new StringBuilder();

        for (String line : addtionalLines) {
            sb.append(line);
            sb.append("\n");
        }

        return sb.toString();
    }

    private void incrementalRun(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @NonNull TransformOutputProvider output) throws IOException {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            JavaSerializationShrinkerGraph graph =
                    JavaSerializationShrinkerGraph.readFromDir(
                            incrementalDir,
                            this.getClass().getClassLoader());
            logTime("loading state", stopwatch);

            ProguardFlags proguardFlags = getProguardFlags();

            if (!proguardFlags.getWhyAreYouKeepingSpecs().isEmpty()) {
                //noinspection SpellCheckingInspection: flag name from ProGuard
                logger.warn(
                        "-whyareyoukeeping is ignored during incremental runs. Clean the project to use it.");
            }

            ShrinkerLogger shrinkerLogger =
                    new ShrinkerLogger(proguardFlags.getDontWarnSpecs(), logger);

            IncrementalShrinker<String> shrinker =
                    new IncrementalShrinker<>(
                            WaitableExecutor.useGlobalSharedThreadPool(),
                            graph,
                            shrinkerLogger,
                            proguardFlags.getBytecodeVersion());

            shrinker.incrementalRun(inputs, output);
            checkForWarnings(proguardFlags, shrinkerLogger);
        } catch (IncrementalShrinker.IncrementalRunImpossibleException e) {
            logger.warn("Incremental shrinker run impossible: " + e.getMessage());
            // Log the full stack trace at INFO level for debugging.
            logger.info("Incremental shrinker run impossible: " + e.getMessage(), e);
            fullRun(inputs, referencedInputs, output);
        }
    }

    private static boolean isIncrementalRun(
            boolean isIncremental,
            @NonNull Collection<TransformInput> referencedInputs) {
        if (!isIncremental) {
            return false;
        }

        for (TransformInput referencedInput : referencedInputs) {
            for (JarInput jarInput : referencedInput.getJarInputs()) {
                if (jarInput.getStatus() != Status.NOTCHANGED) {
                    return false;
                }
            }

            for (DirectoryInput directoryInput : referencedInput.getDirectoryInputs()) {
                if (!directoryInput.getChangedFiles().isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void keep(@NonNull String keep) {
        this.addtionalLines.add("-keep " + keep);
    }

    @Override
    public void dontwarn(@NonNull String dontwarn) {
        this.addtionalLines.add("-dontwarn " + dontwarn);
    }

    @Override
    public void setActions(@NonNull PostprocessingFeatures actions) {
        // The built-in shrinker supports only one "action" (shrinking), and the transform should
        // not be created if shrinking is not desired.
    }

    private static class ShrinkerFlagsHandler implements UnsupportedFlagsHandler {

        private static final ImmutableSet<String> UNSUPPORTED_FLAGS =
                ImmutableSet.of(
                        "-dump",
                        "-forceprocessing",
                        "-injars",
                        "-keepdirectories",
                        "-libraryjars",
                        "-microedition",
                        "-outjars",
                        "-printconfiguration",
                        "-printmapping",
                        "-printseeds",
                        "-printusage");

        private static final ImmutableSet<String> IGNORED_FLAGS =
                ImmutableSet.of(
                        "-optimizations",
                        "-adaptclassstrings",
                        "-adaptresourcefilecontents",
                        "-adaptresourcefilenames",
                        "-allowaccessmodification",
                        "-applymapping",
                        "-assumenosideeffects",
                        "-classobfuscationdictionary",
                        "-flattenpackagehierarchy",
                        "-mergeinterfacesaggressively",
                        "-obfuscationdictionary",
                        "-optimizationpasses",
                        "-overloadaggressively",
                        "-packageobfuscationdictionary",
                        "-renamesourcefileattribute",
                        "-repackageclasses",
                        "-useuniqueclassmembernames");

        @Override
        public void unsupportedFlag(@NonNull String flagName) {
            if (UNSUPPORTED_FLAGS.contains(flagName)) {
                throw new InvalidUserDataException(
                        flagName + " is not supported by the built-in class shrinker.");
            } else if (IGNORED_FLAGS.contains(flagName)) {
                logger.warn(flagName + " is ignored by the built-in class shrinker.");
            }
        }
    }
}
