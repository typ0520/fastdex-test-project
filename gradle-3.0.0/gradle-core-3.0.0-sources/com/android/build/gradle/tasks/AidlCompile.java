/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.AIDL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.CombinedInput;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.builder.compiling.DependencyFileProcessor;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.internal.incremental.DependencyData;
import com.android.builder.internal.incremental.DependencyDataStore;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.util.PatternSet;

/** Task to compile aidl files. Supports incremental update. */
@CacheableTask
public class AidlCompile extends IncrementalTask {

    private static final String DEPENDENCY_STORE = "dependency.store";
    private static final PatternSet PATTERN_SET = new PatternSet().include("**/*.aidl");

    private File sourceOutputDir;

    @Nullable
    private File packagedDir;

    @Nullable
    private Collection<String> packageWhitelist;

    private Supplier<Collection<File>> sourceDirs;
    private FileCollection importDirs;

    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSourceFiles() {
        // this is because aidl may be in the same folder as Java and we want to restrict to
        // .aidl files and not java files.
        return getProject().files(sourceDirs.get()).getAsFileTree().matching(PATTERN_SET);
    }

    private static class DepFileProcessor implements DependencyFileProcessor {
        List<DependencyData> dependencyDataList =
                Collections.synchronizedList(Lists.newArrayList());

        List<DependencyData> getDependencyDataList() {
            return dependencyDataList;
        }

        @Override
        public DependencyData processFile(@NonNull File dependencyFile) throws IOException {
            DependencyData data = DependencyData.parseDependencyFile(dependencyFile);
            if (data != null) {
                dependencyDataList.add(data);
            }

            return data;
        }
    }

    @Override
    @Internal
    protected boolean isIncremental() {
        // TODO fix once dep file parsing is resolved.
        return false;
    }

    /**
     * Action methods to compile all the files.
     *
     * <p>The method receives a {@link DependencyFileProcessor} to be used by the {@link
     * com.android.builder.internal.compiler.SourceSearcher.SourceFileProcessor} during the
     * compilation.
     *
     * @param dependencyFileProcessor a DependencyFileProcessor
     */
    private void compileAllFiles(DependencyFileProcessor dependencyFileProcessor)
            throws InterruptedException, ProcessException, IOException {
        getBuilder().compileAllAidlFiles(
                sourceDirs.get(),
                getSourceOutputDir(),
                getPackagedDir(),
                getPackageWhitelist(),
                getImportDirs().getFiles(),
                dependencyFileProcessor,
                new LoggedProcessOutputHandler(getILogger()));
    }

    /** Returns the import folders. */
    @NonNull
    @Internal
    private List<File> getImportFolders() {
        List<File> fullImportDir = Lists.newArrayList();
        fullImportDir.addAll(getImportDirs().getFiles());
        fullImportDir.addAll(sourceDirs.get());

        return fullImportDir;
    }

    /**
     * Compiles a single file.
     *
     * @param sourceFolder the file to compile.
     * @param file the file to compile.
     * @param importFolders the import folders.
     * @param dependencyFileProcessor a DependencyFileProcessor
     */
    private void compileSingleFile(
            @NonNull File sourceFolder,
            @NonNull File file,
            @Nullable List<File> importFolders,
            @NonNull DependencyFileProcessor dependencyFileProcessor,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws InterruptedException, ProcessException, IOException {
        getBuilder().compileAidlFile(
                sourceFolder,
                file,
                getSourceOutputDir(),
                getPackagedDir(),
                getPackageWhitelist(),
                Preconditions.checkNotNull(importFolders),
                dependencyFileProcessor,
                processOutputHandler);
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getSourceOutputDir();
        File parcelableDir = getPackagedDir();
        FileUtils.cleanOutputDir(destinationDir);
        if (parcelableDir != null) {
            FileUtils.cleanOutputDir(parcelableDir);
        }

        DepFileProcessor processor = new DepFileProcessor();

        try {
            compileAllFiles(processor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<DependencyData> dataList = processor.getDependencyDataList();

        DependencyDataStore store = new DependencyDataStore();
        store.addData(dataList);

        try {
            store.saveTo(new File(getIncrementalFolder(), DEPENDENCY_STORE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        File incrementalData = new File(getIncrementalFolder(), DEPENDENCY_STORE);
        DependencyDataStore store = new DependencyDataStore();
        Multimap<String, DependencyData> inputMap;
        try {
            inputMap = store.loadFrom(incrementalData);
        } catch (Exception ignored) {
            FileUtils.delete(incrementalData);
            getProject().getLogger().info(
                    "Failed to read dependency store: full task run!");
            doFullTaskAction();
            return;
        }

        final List<File> importFolders = getImportFolders();
        final DepFileProcessor processor = new DepFileProcessor();
        final ProcessOutputHandler processOutputHandler =
                new LoggedProcessOutputHandler(getILogger());

        // use an executor to parallelize the compilation of multiple files.
        WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

        Map<String, DependencyData> mainFileMap = store.getMainFileMap();

        for (final Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
            FileStatus status = entry.getValue();

            switch (status) {
                case NEW:
                    executor.execute(() -> {
                        File file = entry.getKey();
                        compileSingleFile(getSourceFolder(file), file, importFolders,
                                processor, processOutputHandler);
                        return null;
                    });
                    break;
                case CHANGED:
                    Collection<DependencyData> impactedData =
                            inputMap.get(entry.getKey().getAbsolutePath());
                    if (impactedData != null) {
                        for (final DependencyData data : impactedData) {
                            executor.execute(() -> {
                                File file = new File(data.getMainFile());
                                compileSingleFile(getSourceFolder(file), file,
                                        importFolders, processor, processOutputHandler);
                                return null;
                            });
                        }
                    }
                    break;
                case REMOVED:
                    final DependencyData data2 = mainFileMap.get(entry.getKey().getAbsolutePath());
                    if (data2 != null) {
                        executor.execute(() -> {
                            cleanUpOutputFrom(data2);
                            return null;
                        });
                        store.remove(data2);
                    }
                    break;
            }
        }

        try {
            executor.waitForTasksWithQuickFail(true /*cancelRemaining*/);
        } catch (Throwable t) {
            FileUtils.delete(incrementalData);
            throw new RuntimeException(t);
        }

        // get all the update data for the recompiled objects
        store.updateAll(processor.getDependencyDataList());

        try {
            store.saveTo(incrementalData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File getSourceFolder(@NonNull File file) {
        File parentDir = file;
        while ((parentDir = parentDir.getParentFile()) != null) {
            if (sourceDirs.get().contains(parentDir)) {
                return parentDir;
            }
        }

        throw new IllegalArgumentException(String.format("File '%s' is not in a source dir", file));
    }

    private static void cleanUpOutputFrom(@NonNull DependencyData dependencyData)
            throws IOException {
        for (String output : dependencyData.getOutputFiles()) {
            FileUtils.delete(new File(output));
        }
        for (String output : dependencyData.getSecondaryOutputFiles()) {
            FileUtils.delete(new File(output));
        }
    }

    @OutputDirectory
    public File getSourceOutputDir() {
        return sourceOutputDir;
    }

    public void setSourceOutputDir(File sourceOutputDir) {
        this.sourceOutputDir = sourceOutputDir;
    }

    @OutputDirectory
    @Optional
    @Nullable
    public File getPackagedDir() {
        return packagedDir;
    }

    public void setPackagedDir(@Nullable File packagedDir) {
        this.packagedDir = packagedDir;
    }

    @Input
    @Optional
    @Nullable
    public Collection<String> getPackageWhitelist() {
        return packageWhitelist;
    }

    public void setPackageWhitelist(@Nullable Collection<String> packageWhitelist) {
        this.packageWhitelist = packageWhitelist;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getImportDirs() {
        return importDirs;
    }

    public static class ConfigAction implements TaskConfigAction<AidlCompile> {

        @NonNull
        VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("compile", "Aidl");
        }

        @Override
        @NonNull
        public Class<AidlCompile> getType() {
            return AidlCompile.class;
        }

        @Override
        public void execute(@NonNull AidlCompile compileTask) {
            final VariantConfiguration<?, ?, ?> variantConfiguration = scope
                    .getVariantConfiguration();

            scope.getVariantData().aidlCompileTask = compileTask;

            compileTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            compileTask.setVariantName(scope.getVariantConfiguration().getFullName());
            compileTask.setIncrementalFolder(scope.getIncrementalDir(getName()));

            compileTask.sourceDirs = TaskInputHelper
                    .bypassFileSupplier(variantConfiguration::getAidlSourceList);
            compileTask.importDirs = scope.getArtifactFileCollection(
                    COMPILE_CLASSPATH, ALL, AIDL);

            compileTask.setSourceOutputDir(scope.getAidlSourceOutputDir());

            if (variantConfiguration.getType() == VariantType.LIBRARY) {
                compileTask.setPackagedDir(scope.getPackagedAidlDir());
                compileTask.setPackageWhitelist(
                        scope.getGlobalScope().getExtension().getAidlPackageWhiteList());
            }
        }
    }

    // Workaround for https://issuetracker.google.com/67418335
    @Override
    @Input
    @NonNull
    public String getCombinedInput() {
        return new CombinedInput(super.getCombinedInput())
                .add("packagedDir", getPackagedDir())
                .toString();
    }
}
