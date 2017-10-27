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

import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.tasks.SimpleWorkQueue;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import proguard.ClassPath;

/**
 * ProGuard support as a transform
 */
public class ProGuardTransform extends BaseProguardAction {
    private static final Logger LOG = Logging.getLogger(ProGuardTransform.class);

    private final VariantScope variantScope;

    private final File proguardOut;

    private final File printMapping;
    private final File dump;
    private final File printSeeds;
    private final File printUsage;
    private final ImmutableList<File> secondaryFileOutputs;

    private File testedMappingFile = null;
    private FileCollection testMappingConfiguration = null;

    public ProGuardTransform(@NonNull VariantScope variantScope) {
        super(variantScope);
        this.variantScope = variantScope;

        GlobalScope globalScope = variantScope.getGlobalScope();
        proguardOut = new File(Joiner.on(File.separatorChar).join(
                String.valueOf(globalScope.getBuildDir()),
                FD_OUTPUTS,
                "mapping",
                variantScope.getVariantConfiguration().getDirName()));

        printMapping = new File(proguardOut, "mapping.txt");
        dump = new File(proguardOut, "dump.txt");
        printSeeds = new File(proguardOut, "seeds.txt");
        printUsage = new File(proguardOut, "usage.txt");
        secondaryFileOutputs = ImmutableList.of(printMapping, dump, printSeeds, printUsage);
    }

    @Nullable
    public File getMappingFile() {
        return printMapping;
    }

    public void applyTestedMapping(@Nullable File testedMappingFile) {
        this.testedMappingFile = testedMappingFile;
    }

    public void applyTestedMapping(@Nullable FileCollection testMappingConfiguration) {
        this.testMappingConfiguration = testMappingConfiguration;
    }

    @NonNull
    @Override
    public String getName() {
        return "proguard";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_JARS;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        final List<SecondaryFile> files = Lists.newArrayList();

        if (testedMappingFile != null && testedMappingFile.isFile()) {
            files.add(SecondaryFile.nonIncremental(testedMappingFile));
        } else if (testMappingConfiguration != null) {
            files.add(SecondaryFile.nonIncremental(testMappingConfiguration));
        }

        // the config files
        files.add(SecondaryFile.nonIncremental(getAllConfigurationFiles()));

        return files;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return secondaryFileOutputs;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of(
                "shrink", configuration.shrink,
                "obfuscate", configuration.obfuscate,
                "optimize", configuration.optimize);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public void transform(@NonNull final TransformInvocation invocation) throws TransformException {
        // only run one minification at a time (across projects)
        SettableFuture<TransformOutputProvider> resultFuture = SettableFuture.create();
        final Job<Void> job = new Job<>(getName(),
                new com.android.builder.tasks.Task<Void>() {
                    @Override
                    public void run(@NonNull Job<Void> job,
                            @NonNull JobContext<Void> context) throws IOException {
                        doMinification(
                                invocation.getInputs(),
                                invocation.getReferencedInputs(),
                                invocation.getOutputProvider());
                    }

                    @Override
                    public void finished() {
                        resultFuture.set(invocation.getOutputProvider());
                    }

                    @Override
                    public void error(Throwable e) {
                        resultFuture.setException(e);
                    }
                }, resultFuture);
        try {
            SimpleWorkQueue.push(job);

            // wait for the task completion.
            try {
                job.awaitRethrowExceptions();
            } catch (ExecutionException e) {
                throw new RuntimeException("Job failed, see logs for details", e.getCause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void doMinification(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider output)
            throws IOException {
        try {
            checkNotNull(output, "Missing output object for transform " + getName());
            Set<ContentType> outputTypes = getOutputTypes();
            Set<? super Scope> scopes = getScopes();
            File outFile =
                    output.getContentLocation(
                            "combined_res_and_classes", outputTypes, scopes, Format.JAR);
            mkdirs(outFile.getParentFile());

            GlobalScope globalScope = variantScope.getGlobalScope();

            // set the mapping file if there is one.
            File testedMappingFile = computeMappingFile();
            if (testedMappingFile != null) {
                applyMapping(testedMappingFile);
            }

            // --- InJars / LibraryJars ---
            addInputsToConfiguration(inputs, false);
            addInputsToConfiguration(referencedInputs, true);

            // libraryJars: the runtime jars, with all optional libraries.
            for (File runtimeJar : globalScope.getAndroidBuilder().getBootClasspath(true)) {
                libraryJar(runtimeJar);
            }

            // --- Out files ---
            outJar(outFile);

            // proguard doesn't verify that the seed/mapping/usage folders exist and will fail
            // if they don't so create them.
            mkdirs(proguardOut);

            for (File configFile : getAllConfigurationFiles()) {
                LOG.info("Applying ProGuard configuration file {}", configFile);
                applyConfigurationFile(configFile);
            }

            configuration.printMapping = printMapping;
            configuration.dump = dump;
            configuration.printSeeds = printSeeds;
            configuration.printUsage = printUsage;

            forceprocessing();
            runProguard();
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }

            throw new IOException(e);
        }
    }

    private void addInputsToConfiguration(
            @NonNull Collection<TransformInput> inputs,
            boolean referencedOnly) {
        ClassPath classPath;
        List<String> baseFilter;

        if (referencedOnly) {
            classPath = configuration.libraryJars;
            baseFilter = JAR_FILTER;
        } else {
            classPath = configuration.programJars;
            baseFilter = null;
        }

        for (TransformInput transformInput : inputs) {
            for (JarInput jarInput : transformInput.getJarInputs()) {
                handleQualifiedContent(classPath, jarInput, baseFilter);
            }

            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                handleQualifiedContent(classPath, directoryInput, baseFilter);
            }
        }
    }

    private void handleQualifiedContent(
            @NonNull ClassPath classPath,
            @NonNull QualifiedContent content,
            @Nullable List<String> baseFilter) {
        List<String> filter = baseFilter;

        if (!content.getContentTypes().contains(DefaultContentType.CLASSES)) {
            // if the content is not meant to contain classes, we ignore them
            // in case they are present.
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            if (filter != null) {
                builder.addAll(filter);
            }
            builder.add("!**.class");
            filter = builder.build();
        } else if (!content.getContentTypes().contains(DefaultContentType.RESOURCES)) {
            // if the content is not meant to contain resources, we ignore them
            // in case they are present (by accepting only classes.)
            filter = ImmutableList.of("**.class");
        }

        inputJar(classPath, content.getFile(), filter);
    }

    @Nullable
    private File computeMappingFile() {
        if (testedMappingFile != null && testedMappingFile.isFile()) {
            return testedMappingFile;
        } else if (testMappingConfiguration != null && testMappingConfiguration.getSingleFile().isFile()) {
            return testMappingConfiguration.getSingleFile();
        }

        return null;
    }
}
