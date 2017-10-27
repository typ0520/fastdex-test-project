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
package com.android.build.gradle.internal.variant;

import android.databinding.tool.LayoutXmlProcessor;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.VariantOutputFactory;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScopeImpl;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.BinaryFileProviderTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.PackageSplitAbi;
import com.android.build.gradle.tasks.PackageSplitRes;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ShaderCompile;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.VariantType;
import com.android.builder.model.SourceProvider;
import com.android.builder.profile.Recorder;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.SourceFile;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.JavaCompile;

/** Base data about a variant. */
public abstract class BaseVariantData implements TaskContainer {

    @NonNull
    protected final TaskManager taskManager;
    @NonNull
    private final GradleVariantConfiguration variantConfiguration;

    private VariantDependencies variantDependency;

    // Needed for ModelBuilder.  Should be removed once VariantScope can replace BaseVariantData.
    @NonNull
    private final VariantScope scope;

    public Task preBuildTask;

    public Task sourceGenTask;
    public Task resourceGenTask;
    public Task assetGenTask;
    public CheckManifest checkManifestTask;
    public AndroidTask<PackageSplitRes> packageSplitResourcesTask;
    public AndroidTask<PackageSplitAbi> packageSplitAbiTask;

    // FIX ME : move all AndroidTask<> above to Scope and use these here.
    private Map<TaskKind, Task> registeredTasks = new ConcurrentHashMap<>();

    public RenderscriptCompile renderscriptCompileTask;
    public AidlCompile aidlCompileTask;
    public MergeResources mergeResourcesTask;
    public ManifestProcessorTask processManifest;
    public MergeSourceSetFolders mergeAssetsTask;
    public GenerateBuildConfig generateBuildConfigTask;
    public GenerateResValues generateResValuesTask;
    public Copy copyApkTask;
    public GenerateApkDataTask generateApkDataTask;
    public ShaderCompile shaderCompileTask;

    public Sync processJavaResourcesTask;
    public NdkCompile ndkCompileTask;

    public JavaCompile javacTask;

    @NonNull
    public Collection<ExternalNativeBuildTask> externalNativeBuildTasks = Lists.newArrayList();
    // empty anchor compile task to set all compilations tasks as dependents.
    public Task compileTask;
    /** JavaCompile, keeping it for backwards compatibility */
    public Task javaCompilerTask;

    public BinaryFileProviderTask binaryFileProviderTask;

    public Task obfuscationTask;

    private ImmutableList<ConfigurableFileTree> defaultJavaSources;

    private List<File> extraGeneratedSourceFolders = Lists.newArrayList();
    private List<ConfigurableFileTree> extraGeneratedSourceFileTrees;
    private final ConfigurableFileCollection extraGeneratedResFolders;
    private Map<Object, FileCollection> preJavacGeneratedBytecodeMap;
    private FileCollection preJavacGeneratedBytecodeLatest;
    private final ConfigurableFileCollection allPreJavacGeneratedBytecode;
    private final ConfigurableFileCollection allPostJavacGeneratedBytecode;

    private Set<String> densityFilters;
    private Set<String> languageFilters;
    private Set<String> abiFilters;

    @Nullable
    private LayoutXmlProcessor layoutXmlProcessor;

    /**
     * If true, variant outputs will be considered signed. Only set if you manually set the outputs
     * to point to signed files built by other tasks.
     */
    public boolean outputsAreSigned = false;

    @NonNull private final OutputScope outputScope;

    @NonNull private final OutputFactory outputFactory;
    public VariantOutputFactory variantOutputFactory;

    public BaseVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig androidConfig,
            @NonNull TaskManager taskManager,
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull ErrorReporter errorReporter,
            @NonNull Recorder recorder) {
        this.variantConfiguration = variantConfiguration;
        this.taskManager = taskManager;

        // eventually, this will require a more open ended comparison.
        MultiOutputPolicy multiOutputPolicy =
                androidConfig.getGeneratePureSplits()
                                && variantConfiguration.getMinSdkVersionValue() >= 21
                        ? MultiOutputPolicy.SPLITS
                        : MultiOutputPolicy.MULTI_APK;

        // warn the user in case we are forced to ignore the generatePureSplits flag.
        if (androidConfig.getGeneratePureSplits()
                && multiOutputPolicy != MultiOutputPolicy.SPLITS) {
            Logging.getLogger(BaseVariantData.class).warn(
                    String.format("Variant %s, MinSdkVersion %s is too low (<21) "
                                    + "to support pure splits, reverting to full APKs",
                            variantConfiguration.getFullName(),
                            variantConfiguration.getMinSdkVersion().getApiLevel()));
        }

        final Project project = globalScope.getProject();
        scope =
                new VariantScopeImpl(
                        globalScope,
                        errorReporter,
                        new TransformManager(
                                globalScope.getProject(),
                                taskManager.getAndroidTasks(),
                                errorReporter,
                                recorder),
                        this);
        outputScope = new OutputScope(multiOutputPolicy);
        outputFactory =
                new OutputFactory(
                        globalScope.getProjectBaseName(), variantConfiguration, outputScope);

        taskManager.configureScopeForNdk(scope);

        // this must be created immediately since the variant API happens after the task that
        // depends on this are created.
        extraGeneratedResFolders = globalScope.getProject().files();
        preJavacGeneratedBytecodeLatest = globalScope.getProject().files();
        allPreJavacGeneratedBytecode = project.files();
        allPostJavacGeneratedBytecode = project.files();
    }

    @NonNull
    public LayoutXmlProcessor getLayoutXmlProcessor() {
        if (layoutXmlProcessor == null) {
            File resourceBlameLogDir = scope.getResourceBlameLogDir();
            final MergingLog mergingLog = new MergingLog(resourceBlameLogDir);
            layoutXmlProcessor = new LayoutXmlProcessor(
                    getVariantConfiguration().getOriginalApplicationId(),
                    taskManager.getDataBindingBuilder()
                            .createJavaFileWriter(scope.getClassOutputForDataBinding()),
                    file -> {
                        SourceFile input = new SourceFile(file);
                        SourceFile original = mergingLog.find(input);
                        // merged log api returns the file back if original cannot be found.
                        // it is not what we want so we alter the response.
                        return original == input ? null : original.getSourceFile();
                    }
            );
        }
        return layoutXmlProcessor;
    }

    @NonNull
    public OutputScope getOutputScope() {
        return outputScope;
    }

    @NonNull
    public OutputFactory getOutputFactory() {
        return outputFactory;
    }

    @Override
    public void addTask(TaskKind taskKind, Task task) {
        registeredTasks.put(taskKind, task);
    }

    @Nullable
    @Override
    public Task getTaskByKind(TaskKind name) {
        return registeredTasks.get(name);
    }

    @Nullable
    @Override
    public <U extends Task> U getTaskByType(Class<U> taskType) {
        // Using Class::isInstance instead of Class::equal because the tasks are decorated by
        // Gradle.
        Optional<Task> requestedTask =
                registeredTasks.values().stream().filter(taskType::isInstance).findFirst();
        return requestedTask.isPresent() ? taskType.cast(requestedTask.get()) : null;
    }

    @NonNull
    public GradleVariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantDependency(@NonNull VariantDependencies variantDependency) {
        this.variantDependency = variantDependency;
    }

    @NonNull
    public VariantDependencies getVariantDependency() {
        return variantDependency;
    }

    @NonNull
    public abstract String getDescription();

    @NonNull
    public String getApplicationId() {
        return variantConfiguration.getApplicationId();
    }

    @NonNull
    protected String getCapitalizedBuildTypeName() {
        return StringHelper.capitalize(variantConfiguration.getBuildType().getName());
    }

    @NonNull
    protected String getCapitalizedFlavorName() {
        return StringHelper.capitalize(variantConfiguration.getFlavorName());
    }

    @NonNull
    public VariantType getType() {
        return variantConfiguration.getType();
    }

    @NonNull
    public String getName() {
        return variantConfiguration.getFullName();
    }

    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return prefix + StringHelper.capitalize(variantConfiguration.getFullName()) + suffix;
    }

    @NonNull
    public List<File> getExtraGeneratedSourceFolders() {
        return extraGeneratedSourceFolders;
    }

    @Nullable
    public FileCollection getExtraGeneratedResFolders() {
        return extraGeneratedResFolders;
    }

    @NonNull
    public FileCollection getAllPreJavacGeneratedBytecode() {
        return allPreJavacGeneratedBytecode;
    }

    @NonNull
    public FileCollection getAllPostJavacGeneratedBytecode() {
        return allPostJavacGeneratedBytecode;
    }

    @NonNull
    public FileCollection getGeneratedBytecode(@Nullable Object generatorKey) {
        if (generatorKey == null) {
            return allPreJavacGeneratedBytecode;
        }

        FileCollection result = preJavacGeneratedBytecodeMap.get(generatorKey);
        if (result == null) {
            throw new RuntimeException("Bytecode generator key not found");
        }

        return result;
    }

    public void addJavaSourceFoldersToModel(@NonNull File generatedSourceFolder) {
        extraGeneratedSourceFolders.add(generatedSourceFolder);
    }

    public void addJavaSourceFoldersToModel(@NonNull File... generatedSourceFolders) {
        Collections.addAll(extraGeneratedSourceFolders, generatedSourceFolders);
    }

    public void addJavaSourceFoldersToModel(@NonNull Collection<File> generatedSourceFolders) {
        extraGeneratedSourceFolders.addAll(generatedSourceFolders);
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... generatedSourceFolders) {
        registerJavaGeneratingTask(task, Arrays.asList(generatedSourceFolders));
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedSourceFolders) {
        Preconditions.checkNotNull(javacTask);
        sourceGenTask.dependsOn(task);

        final Project project = scope.getGlobalScope().getProject();
        if (extraGeneratedSourceFileTrees == null) {
            extraGeneratedSourceFileTrees = new ArrayList<>();
        }

        for (File f : generatedSourceFolders) {
            ConfigurableFileTree fileTree = project.fileTree(f).builtBy(task);
            extraGeneratedSourceFileTrees.add(fileTree);
            javacTask.source(fileTree);
        }

        addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    public void registerExternalAptJavaOutput(@NonNull ConfigurableFileTree folder) {
        Preconditions.checkNotNull(javacTask);

        javacTask.source(folder);
        // Disable incremental compilation when annotation processor is present. (b/65519025)
        // TODO: remove once https://github.com/gradle/gradle/issues/2996 is fixed.
        javacTask.getOptions().setIncremental(false);
        addJavaSourceFoldersToModel(folder.getDir());
    }

    public void registerGeneratedResFolders(@NonNull FileCollection folders) {
        extraGeneratedResFolders.from(folders);
    }

    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull File... generatedResFolders) {
        registerResGeneratingTask(task, Arrays.asList(generatedResFolders));
    }

    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedResFolders) {
        System.out.println("registerResGeneratingTask is deprecated, use registerGeneratedFolders(FileCollection)");

        final Project project = scope.getGlobalScope().getProject();
        registerGeneratedResFolders(project.files(generatedResFolders).builtBy(task));
    }

    public Object registerPreJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        if (preJavacGeneratedBytecodeMap == null) {
            preJavacGeneratedBytecodeMap = Maps.newHashMap();
        }
        // latest contains the generated bytecode up to now, so create a new key and put it in the
        // map.
        Object key = new Object();
        preJavacGeneratedBytecodeMap.put(key, preJavacGeneratedBytecodeLatest);

        // now create a new file collection that will contains the previous latest plus the new
        // one

        // and make this the latest
        preJavacGeneratedBytecodeLatest = preJavacGeneratedBytecodeLatest.plus(fileCollection);

        // also add the stable all-bytecode file collection. We need a stable collection for
        // queries that request all the generated bytecode before the variant api is called.
        allPreJavacGeneratedBytecode.from(fileCollection);

        return key;
    }

    public void registerPostJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        allPostJavacGeneratedBytecode.from(fileCollection);
    }

    /**
     * Calculates the filters for this variant. The filters can either be manually specified by
     * the user within the build.gradle or can be automatically discovered using the variant
     * specific folders.
     *
     * This method must be called before {@link #getFilters(OutputFile.FilterType)}.
     *
     * @param splits the splits configuration from the build.gradle.
     */
    public void calculateFilters(Splits splits) {
        List<File> folders = Lists.newArrayList(getGeneratedResFolders());
        folders.addAll(variantConfiguration.getSourceFiles(SourceProvider::getResDirectories));
        densityFilters = getFilters(folders, DiscoverableFilterType.DENSITY, splits);
        languageFilters = getFilters(folders, DiscoverableFilterType.LANGUAGE, splits);
        abiFilters = getFilters(folders, DiscoverableFilterType.ABI, splits);
    }

    /**
     * Returns the filters values (as manually specified or automatically discovered) for a
     * particular {@link com.android.build.OutputFile.FilterType}
     * @param filterType the type of filter in question
     * @return a possibly empty set of filter values.
     * @throws IllegalStateException if {@link #calculateFilters(Splits)} has not been called prior
     * to invoking this method.
     */
    @NonNull
    public Set<String> getFilters(OutputFile.FilterType filterType) {
        if (densityFilters == null || languageFilters == null || abiFilters == null) {
            throw new IllegalStateException("calculateFilters method not called");
        }
        switch(filterType) {
            case DENSITY:
                return densityFilters;
            case LANGUAGE:
                return languageFilters;
            case ABI:
                return abiFilters;
            default:
                throw new RuntimeException("Unhandled filter type");
        }
    }

    /**
     * Returns the list of generated res folders for this variant.
     */
    private List<File> getGeneratedResFolders() {
        List<File> generatedResFolders = Lists.newArrayList(
                scope.getRenderscriptResOutputDir(),
                scope.getGeneratedResOutputDir());
        if (extraGeneratedResFolders != null) {
            generatedResFolders.addAll(extraGeneratedResFolders.getFiles());
        }
        if (scope.getMicroApkTask() != null
                && getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
            generatedResFolders.add(scope.getMicroApkResDirectory());
        }
        return generatedResFolders;
    }

    @NonNull
    public List<String> discoverListOfResourceConfigs() {
        List<String> resFoldersOnDisk = new ArrayList<String>();
        Set<File> resourceFolders =
                variantConfiguration.getSourceFiles(SourceProvider::getResDirectories);
        resFoldersOnDisk.addAll(getAllFilters(
                resourceFolders,
                DiscoverableFilterType.LANGUAGE.folderPrefix,
                DiscoverableFilterType.DENSITY.folderPrefix));
        return resFoldersOnDisk;
    }

    /**
     * Defines the discoverability attributes of filters.
     */
    private enum DiscoverableFilterType {

        DENSITY("drawable-") {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getDensityFilters();
            }

            @Override
            boolean isAuto(@NonNull Splits splits) {
                return splits.getDensity().isAuto();
            }

        }, LANGUAGE("values-") {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getLanguageFilters();
            }

            @Override
            boolean isAuto(@NonNull Splits splits) {
                return splits.getLanguage().isAuto();
            }
        }, ABI("") {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getAbiFilters();
            }

            @Override
            boolean isAuto(@NonNull Splits splits) {
                // so far, we never auto-discover abi filters.
                return false;
            }
        };

        /**
         * Sets the folder prefix that filter specific resources must start with.
         */
        private String folderPrefix;

        DiscoverableFilterType(String folderPrefix) {
            this.folderPrefix = folderPrefix;
        }

        /**
         * Returns the applicable filters configured in the build.gradle for this filter type.
         * @param splits the build.gradle splits configuration
         * @return a list of filters.
         */
        @NonNull
        abstract Collection<String> getConfiguredFilters(@NonNull Splits splits);

        /**
         * Returns true if the user wants the build system to auto discover the splits for this
         * split type.
         * @param splits the build.gradle splits configuration.
         * @return true to use auto-discovery, false to use the build.gradle configuration.
         */
        abstract boolean isAuto(@NonNull Splits splits);
    }

    /**
     * Gets the list of filter values for a filter type either from the user specified build.gradle
     * settings or through a discovery mechanism using folders names.
     * @param resourceFolders the list of source folders to discover from.
     * @param filterType the filter type
     * @param splits the variant's configuration for splits.
     * @return a possibly empty list of filter value for this filter type.
     */
    @NonNull
    private static Set<String> getFilters(
            @NonNull List<File> resourceFolders,
            @NonNull DiscoverableFilterType filterType,
            @NonNull Splits splits) {

        Set<String> filtersList = new HashSet<String>();
        if (filterType.isAuto(splits)) {
            filtersList.addAll(getAllFilters(resourceFolders, filterType.folderPrefix));
        } else {
            filtersList.addAll(filterType.getConfiguredFilters(splits));
        }
        return filtersList;
    }

    /**
     * Discover all sub-folders of all the resource folders which names are
     * starting with one of the provided prefixes.
     * @param resourceFolders the list of resource folders
     * @param prefixes the list of prefixes to look for folders.
     * @return a possibly empty list of folders.
     */
    @NonNull
    private static List<String> getAllFilters(Iterable<File> resourceFolders, String... prefixes) {
        List<String> providedResFolders = new ArrayList<>();
        for (File resFolder : resourceFolders) {
            File[] subResFolders = resFolder.listFiles();
            if (subResFolders != null) {
                for (File subResFolder : subResFolders) {
                    for (String prefix : prefixes) {
                        if (subResFolder.getName().startsWith(prefix)) {
                            providedResFolders
                                    .add(subResFolder.getName().substring(prefix.length()));
                        }
                    }
                }
            }
        }
        return providedResFolders;
    }

    /**
     * Computes the Java sources to use for compilation.
     *
     * <p>Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    @NonNull
    public List<ConfigurableFileTree> getJavaSources() {
        if (extraGeneratedSourceFileTrees == null || extraGeneratedSourceFileTrees.isEmpty()) {
            return getDefaultJavaSources();
        }

        // Build the list of source folders.
        ImmutableList.Builder<ConfigurableFileTree> sourceSets = ImmutableList.builder();

        // First the default source folders.
        sourceSets.addAll(getDefaultJavaSources());

        // then the third party ones
        sourceSets.addAll(extraGeneratedSourceFileTrees);

        return sourceSets.build();
    }

    /**
     * Computes the default java sources: source sets and generated sources.
     *
     * <p>Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    @NonNull
    private List<ConfigurableFileTree> getDefaultJavaSources() {
        if (defaultJavaSources == null) {
            Project project = scope.getGlobalScope().getProject();
            // Build the list of source folders.
            ImmutableList.Builder<ConfigurableFileTree> sourceSets = ImmutableList.builder();

            // First the actual source folders.
            List<SourceProvider> providers = variantConfiguration.getSortedSourceProviders();
            for (SourceProvider provider : providers) {
                sourceSets.addAll(
                        ((AndroidSourceSet) provider).getJava().getSourceDirectoryTrees());
            }

            // then all the generated src folders.
            if (scope.getProcessResourcesTask() != null) {
                sourceSets.add(
                        project.fileTree(scope.getRClassSourceOutputDir())
                                .builtBy(scope.getProcessResourcesTask().getName()));
            }

            // for the other, there's no duplicate so no issue.
            if (scope.getGenerateBuildConfigTask() != null) {
                sourceSets.add(
                        project.fileTree(scope.getBuildConfigSourceOutputDir())
                                .builtBy(scope.getGenerateBuildConfigTask().getName()));
            }

            if (scope.getAidlCompileTask() != null) {
                sourceSets.add(
                        project.fileTree(scope.getAidlSourceOutputDir())
                                .builtBy(scope.getAidlCompileTask().getName()));
            }

            if (scope.getGlobalScope().getExtension().getDataBinding().isEnabled()
                    && scope.getDataBindingExportBuildInfoTask() != null) {
                sourceSets.add(
                        project.fileTree(scope.getClassOutputForDataBinding())
                                .builtBy(scope.getDataBindingExportBuildInfoTask().getName()));
            }

            if (!variantConfiguration.getRenderscriptNdkModeEnabled()
                    && scope.getRenderscriptCompileTask() != null) {
                sourceSets.add(
                        project.fileTree(scope.getRenderscriptSourceOutputDir())
                                .builtBy(scope.getRenderscriptCompileTask().getName()));
            }

            defaultJavaSources = sourceSets.build();
        }

        return defaultJavaSources;
    }

    /**
     * Returns the Java folders needed for code coverage report.
     *
     * <p>This includes all the source folders except for the ones containing R and buildConfig.
     */
    @NonNull
    public List<File> getJavaSourceFoldersForCoverage() {
        // Build the list of source folders.
        List<File> sourceFolders = Lists.newArrayList();

        // First the actual source folders.
        List<SourceProvider> providers = variantConfiguration.getSortedSourceProviders();
        for (SourceProvider provider : providers) {
            for (File sourceFolder : provider.getJavaDirectories()) {
                if (sourceFolder.isDirectory()) {
                    sourceFolders.add(sourceFolder);
                }
            }
        }

        File sourceFolder;
        // then all the generated src folders, except the ones for the R/Manifest and
        // BuildConfig classes.
        sourceFolder = aidlCompileTask.getSourceOutputDir();
        if (sourceFolder.isDirectory()) {
            sourceFolders.add(sourceFolder);
        }

        if (!variantConfiguration.getRenderscriptNdkModeEnabled()) {
            sourceFolder = renderscriptCompileTask.getSourceOutputDir();
            if (sourceFolder.isDirectory()) {
                sourceFolders.add(sourceFolder);
            }
        }

        return sourceFolders;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(variantConfiguration.getFullName())
                .toString();
    }

    @NonNull
    public VariantScope getScope() {
        return scope;
    }

    @NonNull
    public File getJavaResourcesForUnitTesting() {
        if (processJavaResourcesTask != null) {
            return processJavaResourcesTask.getOutputs().getFiles().getSingleFile();
        } else {
            return scope.getSourceFoldersJavaResDestinationDir();
        }
    }
}
