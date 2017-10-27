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

package com.android.build.gradle.internal.scope;

import static com.android.SdkConstants.FD_COMPILED;
import static com.android.SdkConstants.FD_MERGED;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.build.gradle.internal.TaskManager.DIR_BUNDLES;
import static com.android.build.gradle.internal.dsl.BuildType.PostprocessingConfiguration.POSTPROCESSING_BLOCK;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.METADATA_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;
import static com.android.build.gradle.internal.scope.CodeShrinker.ANDROID_GRADLE;
import static com.android.build.gradle.internal.scope.CodeShrinker.PROGUARD;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact;
import com.android.build.gradle.internal.dependency.FilteredArtifactCollection;
import com.android.build.gradle.internal.dependency.SubtractingArtifactCollection;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.PostprocessingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.internal.publishing.VariantPublishingSpec;
import com.android.build.gradle.internal.publishing.VariantPublishingSpec.OutputPublishingSpec;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.DeploymentDevice;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BootClasspathBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.SyncIssue;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * A scope containing data for a specific variant.
 */
public class VariantScopeImpl extends GenericVariantScopeImpl implements VariantScope {

    private static final ILogger LOGGER = LoggerWrapper.getLogger(VariantScopeImpl.class);

    @NonNull private final VariantPublishingSpec variantPublishingSpec;

    @NonNull private final GlobalScope globalScope;
    @NonNull private final BaseVariantData variantData;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final TransformManager transformManager;
    @Nullable private Collection<Object> ndkBuildable;
    @Nullable private Collection<File> ndkSoFolder;
    @Nullable private File ndkObjFolder;
    @NonNull private final Map<Abi, File> ndkDebuggableLibraryFolders = Maps.newHashMap();

    @Nullable private File mergeResourceOutputDir;

    // Tasks
    private AndroidTask<DefaultTask> assembleTask;
    private AndroidTask<? extends DefaultTask> preBuildTask;

    private AndroidTask<Task> sourceGenTask;
    private AndroidTask<Task> resourceGenTask;
    private AndroidTask<Task> assetGenTask;
    private AndroidTask<CheckManifest> checkManifestTask;

    private AndroidTask<RenderscriptCompile> renderscriptCompileTask;
    private AndroidTask<AidlCompile> aidlCompileTask;
    @Nullable private AndroidTask<MergeResources> mergeResourcesTask;
    @Nullable private AndroidTask<MergeSourceSetFolders> mergeAssetsTask;
    private AndroidTask<GenerateBuildConfig> generateBuildConfigTask;

    private AndroidTask<Sync> processJavaResourcesTask;
    private AndroidTask<TransformTask> mergeJavaResourcesTask;

    @Nullable private AndroidTask<? extends JavaCompile> javacTask;

    // empty anchor compile task to set all compilations tasks as dependents.
    private AndroidTask<Task> compileTask;

    private AndroidTask<GenerateApkDataTask> microApkTask;

    @Nullable private AndroidTask<ExternalNativeBuildTask> externalNativeBuild;

    @Nullable private ExternalNativeJsonGenerator externalNativeJsonGenerator;

    @NonNull
    private final List<NativeBuildConfigValue> externalNativeBuildConfigValues =
            Lists.newArrayList();

    @Nullable private CodeShrinker defaultCodeShrinker;

    /**
     * This is an instance of {@link JacocoReportTask} in android test variants, an umbrella
     * {@link Task} in app and lib variants and null in unit test variants.
     */
    private AndroidTask<?> coverageReportTask;

    private File resourceOutputDir;

    private InstantRunTaskManager instantRunTaskManager;

    private ConfigurableFileCollection desugarTryWithResourcesRuntimeJar;
    private AndroidTask<DataBindingExportBuildInfoTask> dataBindingExportBuildInfoTask;

    public VariantScopeImpl(
            @NonNull GlobalScope globalScope,
            @NonNull ErrorReporter errorReporter,
            @NonNull TransformManager transformManager,
            @NonNull BaseVariantData variantData) {
        this.globalScope = globalScope;
        this.errorReporter = errorReporter;
        this.transformManager = transformManager;
        this.variantData = variantData;
        this.variantPublishingSpec = VariantPublishingSpec.getVariantSpec(variantData.getType());
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        this.instantRunBuildContext =
                new InstantRunBuildContext(
                        variantData.getVariantConfiguration().isInstantRunBuild(globalScope),
                        AaptGeneration.fromProjectOptions(projectOptions),
                        DeploymentDevice.getDeploymentDeviceAndroidVersion(projectOptions),
                        projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI),
                        projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY),
                        projectOptions.get(BooleanOption.ENABLE_SEPARATE_APK_RESOURCES));

        validatePostprocessingOptions();
    }

    private void validatePostprocessingOptions() {
        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();
        if (postprocessingOptions == null) {
            return;
        }

        if (postprocessingOptions.getCodeShrinkerEnum() == ANDROID_GRADLE) {
            if (postprocessingOptions.isObfuscate()) {
                errorReporter.handleSyncError(
                        null,
                        SyncIssue.TYPE_GENERIC,
                        "The 'android-gradle' code shrinker does not support obfuscating.");
            }

            if (postprocessingOptions.isOptimizeCode()) {
                errorReporter.handleSyncError(
                        null,
                        SyncIssue.TYPE_GENERIC,
                        "The 'android-gradle' code shrinker does not support optimizing code.");
            }
        }
    }

    @Override
    protected Project getProject() {
        return globalScope.getProject();
    }

    @Override
    @NonNull
    public VariantPublishingSpec getPublishingSpec() {
        return variantPublishingSpec;
    }

    @Override
    public ConfigurableFileCollection addTaskOutput(
            @NonNull TaskOutputType outputType, @NonNull Object file, @Nullable String taskName) {
        ConfigurableFileCollection fileCollection;
        try {
            fileCollection = super.addTaskOutput(outputType, file, taskName);
        } catch (TaskOutputAlreadyRegisteredException e) {
            throw new RuntimeException(
                    String.format(
                            "OutputType '%s' already registered for variant '%s'",
                            e.getOutputType(), this.getFullVariantName()),
                    e);
        }

        if (file instanceof File) {
            OutputPublishingSpec taskSpec = variantPublishingSpec.getSpec(outputType);
            if (taskSpec != null) {
                publishIntermediateArtifact(
                        (File) file,
                        taskName,
                        taskSpec.getArtifactType(),
                        taskSpec.getPublishedConfigTypes());
            }
        }
        return fileCollection;
    }

    @NonNull
    @Override
    public FileCollection getOutput(@NonNull OutputType outputType)
            throws MissingTaskOutputException {
        try {
            return super.getOutput(outputType);
        } catch (MissingTaskOutputException e) {
            throw new RuntimeException(
                    String.format(
                            "Variant '%s' has no output with type '%s'",
                            this.getFullVariantName(), e.getOutputType()),
                    e);
        }
    }

    private void publishIntermediateArtifact(
            @NonNull File file,
            @NonNull String builtBy,
            @NonNull ArtifactType artifactType,
            @NonNull Collection<PublishedConfigType> configTypes) {
        Preconditions.checkState(!configTypes.isEmpty());

        // FIXME this needs to be parameterized based on the variant's publishing type.
        final VariantDependencies variantDependency = getVariantData().getVariantDependency();

        if (configTypes.contains(API_ELEMENTS)) {
            Preconditions.checkNotNull(
                    variantDependency.getApiElements(),
                    "Publishing to API Element with no ApiElement configuration object");
            publishArtifactToConfiguration(
                    variantDependency.getApiElements(), file, builtBy, artifactType);
        }

        if (configTypes.contains(RUNTIME_ELEMENTS)) {
            Preconditions.checkNotNull(
                    variantDependency.getRuntimeElements(),
                    "Publishing to Runtime Element with no RuntimeElement configuration object");
            publishArtifactToConfiguration(
                    variantDependency.getRuntimeElements(), file, builtBy, artifactType);
        }

        if (configTypes.contains(METADATA_ELEMENTS)) {
            Preconditions.checkNotNull(
                    variantDependency.getMetadataElements(),
                    "Publishing to Metadata Element with no MetaDataElement configuration object");
            publishArtifactToConfiguration(
                    variantDependency.getMetadataElements(), file, builtBy, artifactType);
        }
    }

    private void publishArtifactToConfiguration(
            @NonNull Configuration configuration,
            @NonNull File file,
            @NonNull String builtBy,
            @NonNull ArtifactType artifactType) {
        final Project project = globalScope.getProject();
        String type = artifactType.getType();
        configuration.getOutgoing().variants(
                (NamedDomainObjectContainer<ConfigurationVariant> variants) -> {
                    variants.create(type, (variant) ->
                            variant.artifact(file, (artifact) -> {
                                artifact.setType(type);
                                artifact.builtBy(project.getTasks().getByName(builtBy));
                            }));
                });
    }

    @Override
    @NonNull
    public GlobalScope getGlobalScope() {
        return globalScope;
    }

    @Override
    @NonNull
    public BaseVariantData getVariantData() {
        return variantData;
    }

    @Override
    @NonNull
    public GradleVariantConfiguration getVariantConfiguration() {
        return variantData.getVariantConfiguration();
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return getVariantConfiguration().getFullName();
    }

    /** Returns the {@link PostprocessingOptions} if they should be used, null otherwise. */
    @Nullable
    private PostprocessingOptions getPostprocessingOptionsIfUsed() {
        CoreBuildType coreBuildType = getCoreBuildType();

        // This may not be the case with the experimental plugin.
        if (coreBuildType instanceof BuildType) {
            BuildType dslBuildType = (BuildType) coreBuildType;
            if (dslBuildType.getPostprocessingConfiguration() == POSTPROCESSING_BLOCK) {
                return dslBuildType.getPostprocessing();
            }
        }

        return null;
    }

    @NonNull
    private CoreBuildType getCoreBuildType() {
        return getVariantConfiguration().getBuildType();
    }

    @Override
    public boolean useResourceShrinker() {
        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();

        boolean userEnabledShrinkResources;
        if (postprocessingOptions != null) {
            userEnabledShrinkResources = postprocessingOptions.isRemoveUnusedResources();
        } else {
            //noinspection deprecation - this needs to use the old DSL methods.
            userEnabledShrinkResources = getCoreBuildType().isShrinkResources();
        }

        if (!userEnabledShrinkResources) {
            return false;
        }

        if (variantData.getType() == VariantType.LIBRARY) {
            errorReporter.handleSyncError(
                    null,
                    SyncIssue.TYPE_GENERIC,
                    "Resource shrinker cannot be used for libraries.");
            return false;
        }

        if (getCodeShrinker() == null) {
            errorReporter.handleSyncError(
                    null,
                    SyncIssue.TYPE_GENERIC,
                    "Removing unused resources requires unused code shrinking to be turned on. See "
                            + "http://d.android.com/r/tools/shrink-resources.html "
                            + "for more information.");

            return false;
        }

        return true;
    }

    @Override
    public boolean isCrunchPngs() {
        // If set for this build type, respect that.
        Boolean buildTypeOverride = getVariantConfiguration().getBuildType().isCrunchPngs();
        if (buildTypeOverride != null) {
            return buildTypeOverride;
        }
        // Otherwise, if set globally, respect that.
        Boolean globalOverride =
                globalScope.getExtension().getAaptOptions().getCruncherEnabledOverride();
        if (globalOverride != null) {
            return globalOverride;
        }
        // If not overridden, use the default from the build type.
        //noinspection deprecation TODO: Remove once the global cruncher enabled flag goes away.
        return getVariantConfiguration().getBuildType().isCrunchPngsDefault();
    }

    @Nullable
    @Override
    public CodeShrinker getCodeShrinker() {
        boolean isForTesting = getVariantConfiguration().getType().isForTesting();

        //noinspection ConstantConditions - getType() will not return null for a testing variant.
        if (isForTesting && getTestedVariantData().getType() == VariantType.LIBRARY) {
            // For now we seem to include the production library code as both program and library
            // input to the test ProGuard run, which confuses it.
            return null;
        }

        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();

        if (postprocessingOptions == null) { // Old DSL used:
            CoreBuildType coreBuildType = getCoreBuildType();
            //noinspection deprecation - this needs to use the old DSL methods.
            if (!coreBuildType.isMinifyEnabled()) {
                return null;
            }

            CodeShrinker shrinkerForBuildType;

            //noinspection deprecation - this needs to use the old DSL methods.
            Boolean useProguard = coreBuildType.isUseProguard();
            if (useProguard == null) {
                shrinkerForBuildType = getDefaultCodeShrinker();
            } else {
                shrinkerForBuildType = useProguard ? PROGUARD : ANDROID_GRADLE;
            }

            if (!isForTesting) {
                return shrinkerForBuildType;
            } else {
                if (shrinkerForBuildType == PROGUARD) {
                    // ProGuard is used for main app code and we don't know if it gets
                    // obfuscated, so we need to run ProGuard on test code just in case.
                    return PROGUARD;
                } else {
                    return null;
                }
            }
        } else { // New DSL used:
            CodeShrinker chosenShrinker = postprocessingOptions.getCodeShrinkerEnum();
            if (chosenShrinker == null) {
                chosenShrinker = getDefaultCodeShrinker();
            }

            switch (chosenShrinker) {
                case PROGUARD:
                    if (!isForTesting) {
                        boolean somethingToDo =
                                postprocessingOptions.isRemoveUnusedCode()
                                        || postprocessingOptions.isObfuscate()
                                        || postprocessingOptions.isOptimizeCode();
                        return somethingToDo ? PROGUARD : null;
                    } else {
                        // For testing code, we only run ProGuard if main code is obfuscated.
                        return postprocessingOptions.isObfuscate() ? PROGUARD : null;
                    }
                case ANDROID_GRADLE:
                    if (isForTesting) {
                        return null;
                    } else {
                        return postprocessingOptions.isRemoveUnusedCode() ? ANDROID_GRADLE : null;
                    }
                default:
                    throw new AssertionError("Unknown value " + chosenShrinker);
            }
        }
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
        List<File> result =
                gatherProguardFiles(
                        PostprocessingOptions::getProguardFiles, BaseConfig::getProguardFiles);

        if (getPostprocessingOptionsIfUsed() == null) {
            // For backwards compatibility, we keep the old behavior: if there are no files
            // specified, use a default one.
            if (result.isEmpty()) {
                result.add(
                        ProguardFiles.getDefaultProguardFile(
                                ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName, getProject()));
            }
        }

        return result;
    }

    @NonNull
    @Override
    public List<File> getTestProguardFiles() {
        return gatherProguardFiles(
                PostprocessingOptions::getTestProguardFiles, BaseConfig::getTestProguardFiles);
    }

    @NonNull
    @Override
    public List<File> getConsumerProguardFiles() {
        return gatherProguardFiles(
                PostprocessingOptions::getConsumerProguardFiles,
                BaseConfig::getConsumerProguardFiles);
    }

    @NonNull
    private List<File> gatherProguardFiles(
            @NonNull Function<PostprocessingOptions, List<File>> postprocessingGetter,
            @NonNull Function<BaseConfig, Collection<File>> baseConfigGetter) {
        GradleVariantConfiguration variantConfiguration = getVariantConfiguration();

        List<File> result = new ArrayList<>();
        result.addAll(baseConfigGetter.apply(variantConfiguration.getDefaultConfig()));

        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();
        if (postprocessingOptions == null) {
            result.addAll(baseConfigGetter.apply(variantConfiguration.getBuildType()));
        } else {
            result.addAll(postprocessingGetter.apply(postprocessingOptions));
        }

        for (CoreProductFlavor flavor : variantConfiguration.getProductFlavors()) {
            result.addAll(baseConfigGetter.apply(flavor));
        }

        return result;
    }

    @Override
    @Nullable
    public PostprocessingFeatures getPostprocessingFeatures() {
        // If the new DSL block is not used, all these flags need to be in the config files.
        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();
        if (postprocessingOptions != null) {
            return PostprocessingFeatures.create(
                    postprocessingOptions.isRemoveUnusedCode(),
                    postprocessingOptions.isObfuscate(),
                    postprocessingOptions.isOptimizeCode());
        } else {
            return null;
        }
    }

    @NonNull
    private CodeShrinker getDefaultCodeShrinker() {
        if (defaultCodeShrinker == null) {
            if (getInstantRunBuildContext().isInInstantRunMode()) {
                String message = "Using the built-in class shrinker for an Instant Run build.";
                PostprocessingFeatures postprocessingFeatures = getPostprocessingFeatures();
                if (postprocessingFeatures == null || postprocessingFeatures.isObfuscate()) {
                    message += " Build won't be obfuscated.";
                }
                LOGGER.warning(message);

                defaultCodeShrinker = ANDROID_GRADLE;
            } else {
                defaultCodeShrinker = PROGUARD;
            }
        }

        return defaultCodeShrinker;
    }

    /**
     * Determine if the final output should be marked as testOnly to prevent uploading to Play
     * store.
     *
     * <p>Uploading to Play store is disallowed if:
     *
     * <ul>
     *   <li>An injected option is set (usually by the IDE for testing purposes).
     *   <li>compileSdkVersion, minSdkVersion or targetSdkVersion is a preview
     * </ul>
     *
     * <p>This value can be overridden by the OptionalBooleanOption.IDE_TEST_ONLY property.
     */
    @Override
    public boolean isTestOnly() {
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        Boolean isTestOnlyOverride = projectOptions.get(OptionalBooleanOption.IDE_TEST_ONLY);

        if (isTestOnlyOverride != null) {
            return isTestOnlyOverride;
        }

        return !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
                || !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY))
                || projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API) != null
                || globalScope.getAndroidBuilder().isPreviewTarget()
                || getMinSdkVersion().getCodename() != null
                || getVariantConfiguration().getTargetSdkVersion().getCodename() != null;
    }

    /**
     * Determine if the feature module is the base feature module.
     *
     * @return true if this feature module is the base feature module. False otherwise.
     */
    @Override
    public boolean isBaseFeature() {
        return globalScope.getExtension().getBaseFeature();
    }

    @NonNull
    @Override
    public DexingType getDexingType() {
        DexingType dexingType = variantData.getVariantConfiguration().getDexingType();

        if (variantData.getType().isForTesting()
                && getTestedVariantData() != null
                && getTestedVariantData().getType() != VariantType.LIBRARY
                && dexingType == DexingType.LEGACY_MULTIDEX) {
            // for non-library legacy multidex test variants, we want to have exactly one DEX file
            // until the test runner supports multiple dex files in the test apk
            return DexingType.MONO_DEX;
        } else if (isInstantRunDexingTypeOverride()) {
            return DexingType.NATIVE_MULTIDEX;
        }

        return dexingType;
    }

    private boolean isInstantRunDexingTypeOverride() {
        return getInstantRunBuildContext().isInInstantRunMode()
                && InstantRunPatchingPolicy.useMultiApk(
                        getInstantRunBuildContext().getPatchingPolicy());
    }

    @NonNull
    @Override
    public AndroidVersion getMinSdkVersion() {
        return getVariantConfiguration().getMinSdkVersion();
    }

    @NonNull
    @Override
    public String getDirName() {
        return variantData.getVariantConfiguration().getDirName();
    }

    @NonNull
    @Override
    public Collection<String> getDirectorySegments() {
        return variantData.getVariantConfiguration().getDirectorySegments();
    }

    @NonNull
    @Override
    public TransformManager getTransformManager() {
        return transformManager;
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix) {
        return getTaskName(prefix, "");
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return variantData.getTaskName(prefix, suffix);
    }

    @Override
    @Nullable
    public Collection<Object> getNdkBuildable() {
        return ndkBuildable;
    }

    @Override
    public void setNdkBuildable(@NonNull Collection<Object> ndkBuildable) {
        this.ndkBuildable = ndkBuildable;
    }

    @Override
    @Nullable
    public Collection<File> getNdkSoFolder() {
        return ndkSoFolder;
    }

    @Override
    public void setNdkSoFolder(@NonNull Collection<File> ndkSoFolder) {
        this.ndkSoFolder = ndkSoFolder;
    }

    @Override
    @Nullable
    public File getNdkObjFolder() {
        return ndkObjFolder;
    }

    @Override
    public void setNdkObjFolder(@NonNull File ndkObjFolder) {
        this.ndkObjFolder = ndkObjFolder;
    }

    /**
     * Return the folder containing the shared object with debugging symbol for the specified ABI.
     */
    @Override
    @Nullable
    public File getNdkDebuggableLibraryFolders(@NonNull Abi abi) {
        return ndkDebuggableLibraryFolders.get(abi);
    }

    @Override
    public void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath) {
        this.ndkDebuggableLibraryFolders.put(abi, searchPath);
    }

    @Override
    @Nullable
    public BaseVariantData getTestedVariantData() {
        return variantData instanceof TestVariantData ?
                (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData() :
                null;
    }

    @NonNull
    @Override
    public File getBuildInfoOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/build-info/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getReloadDexOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/reload-dex/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRestartDexOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/restart-dex/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/split-apk/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunPastIterationsFolder() {
        return new File(globalScope.getIntermediatesDir(), "/builds/" + getVariantConfiguration().getDirName());
    }

    // Precomputed file paths.

    @Override
    @NonNull
    public FileCollection getJavaClasspath(
            @NonNull ConsumedConfigType configType, @NonNull ArtifactType classesType) {
        return getJavaClasspath(configType, classesType, null);
    }

    @Override
    @NonNull
    public FileCollection getJavaClasspath(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey) {
        FileCollection mainCollection = getArtifactFileCollection(configType, ALL, classesType);

        return mainCollection.plus(getVariantData().getGeneratedBytecode(generatedBytecodeKey));
    }

    @NonNull
    @Override
    public ArtifactCollection getJavaClasspathArtifacts(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey) {
        ArtifactCollection mainCollection = getArtifactCollection(configType, ALL, classesType);

        return ArtifactCollectionWithExtraArtifact.makeExtraCollection(
                mainCollection,
                getVariantData().getGeneratedBytecode(generatedBytecodeKey),
                getProject().getPath());
    }

    @Override
    public boolean keepDefaultBootstrap() {
        // javac 1.8 may generate code that uses class not available in android.jar.  This is fine
        // if desugar is used to compile code for the app or compile task is created only
        // for unit test. In those cases, we want to keep the default bootstrap classpath.
        if (!JavaVersion.current().isJava8Compatible()) {
            return false;
        }

        VariantScope.Java8LangSupport java8LangSupport = getJava8LangSupportType();

        // only if target and source is explicitly specified to 1.8 (and above), we keep the
        // default bootclasspath with Desugar. Otherwise, we use android.jar.
        return java8LangSupport == VariantScope.Java8LangSupport.DESUGAR;
    }

    @Override
    @NonNull
    public File getJavaOutputDir() {
        return new File(globalScope.getIntermediatesDir(), "/classes/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunSupportDir() {
        return new File(globalScope.getIntermediatesDir(), "/instant-run-support/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunSliceSupportDir() {
        return new File(globalScope.getIntermediatesDir(), "/instant-run-slices/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getIncrementalRuntimeSupportJar() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-runtime-classes/" +
                variantData.getVariantConfiguration().getDirName() + "/instant-run.jar");
    }

    @Override
    @NonNull
    public File getIncrementalApplicationSupportDir() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-classes/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunResourcesFile() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "instant-run-resources",
                "resources-" + variantData.getVariantConfiguration().getDirName() + ".ir.ap_");
    }

    @Override
    @NonNull
    public File getIncrementalVerifierDir() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-verifier/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public FileCollection getArtifactFileCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {
        ArtifactCollection artifacts = computeArtifactCollection(configType, scope, artifactType);

        FileCollection fileCollection;

        if (configType == RUNTIME_CLASSPATH
                && getVariantConfiguration().getType() == VariantType.FEATURE
                && artifactType != ArtifactType.FEATURE_TRANSITIVE_DEPS) {
            fileCollection =
                    new FilteredArtifactCollection(
                                    globalScope.getProject(),
                                    artifacts,
                                    computeArtifactCollection(
                                                    RUNTIME_CLASSPATH,
                                                    scope,
                                                    ArtifactType.FEATURE_TRANSITIVE_DEPS)
                                            .getArtifactFiles())
                            .getArtifactFiles();
        } else {
            fileCollection = artifacts.getArtifactFiles();
        }

        if (configType.needsTestedComponents()) {
            return handleTestedComponent(
                    fileCollection,
                    configType,
                    scope,
                    artifactType,
                    (mainCollection, testedCollection, unused) ->
                            mainCollection.plus(testedCollection),
                    (collection, artifactCollection) ->
                            collection.minus(artifactCollection.getArtifactFiles()));
        }

        return fileCollection;
    }

    @Override
    @NonNull
    public ArtifactCollection getArtifactCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {
        ArtifactCollection artifacts = computeArtifactCollection(configType, scope, artifactType);

        if (configType == RUNTIME_CLASSPATH
                && getVariantConfiguration().getType() == VariantType.FEATURE
                && artifactType != ArtifactType.FEATURE_TRANSITIVE_DEPS) {
            artifacts =
                    new FilteredArtifactCollection(
                            globalScope.getProject(),
                            artifacts,
                            computeArtifactCollection(
                                            RUNTIME_CLASSPATH,
                                            scope,
                                            ArtifactType.FEATURE_TRANSITIVE_DEPS)
                                    .getArtifactFiles());
        }

        if (configType.needsTestedComponents()) {
            return handleTestedComponent(
                    artifacts,
                    configType,
                    scope,
                    artifactType,
                    (artifactResults, collection, variantName) ->
                            ArtifactCollectionWithExtraArtifact.makeExtraCollectionForTest(
                                    artifactResults,
                                    collection,
                                    getProject().getPath(),
                                    variantName),
                    SubtractingArtifactCollection::new);
        }

        return artifacts;
    }

    @NonNull
    private ArtifactCollection computeArtifactCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {

        Configuration configuration;
        switch (configType) {
            case COMPILE_CLASSPATH:
                configuration = getVariantData().getVariantDependency().getCompileClasspath();
                break;
            case RUNTIME_CLASSPATH:
                configuration = getVariantData().getVariantDependency().getRuntimeClasspath();
                break;
            case ANNOTATION_PROCESSOR:
                configuration = getVariantData()
                        .getVariantDependency()
                        .getAnnotationProcessorConfiguration();
                break;
            case METADATA_VALUES:
                configuration =
                        getVariantData().getVariantDependency().getMetadataValuesConfiguration();
                break;
            default:
                throw new RuntimeException("unknown ConfigType value");
        }

        Action<AttributeContainer> attributes =
                container -> container.attribute(ARTIFACT_TYPE, artifactType.getType());

        Spec<ComponentIdentifier> filter = getComponentFilter(scope);

        boolean lenientMode =
                Boolean.TRUE.equals(
                        globalScope.getProjectOptions().get(BooleanOption.IDE_BUILD_MODEL_ONLY));

        return configuration
                .getIncoming()
                .artifactView(
                        config -> {
                            config.attributes(attributes);
                            if (filter != null) {
                                config.componentFilter(filter);
                            }
                            // TODO somehow read the unresolved dependencies?
                            config.lenient(lenientMode);
                        })
                .getArtifacts();
    }

    @Nullable
    private static Spec<ComponentIdentifier> getComponentFilter(
            @NonNull AndroidArtifacts.ArtifactScope scope) {
        switch (scope) {
            case ALL:
                return null;
            case EXTERNAL:
                // since we want both Module dependencies and file based dependencies in this case
                // the best thing to do is search for non ProjectComponentIdentifier.
                return id -> !(id instanceof ProjectComponentIdentifier);
            case MODULE:
                return id -> id instanceof ProjectComponentIdentifier;
            default:
                throw new RuntimeException("unknown ArtifactScope value");
        }
    }

    /**
     * Returns the packaged local Jars
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    @Override
    public Supplier<Collection<File>> getLocalPackagedJars() {

        return TaskInputHelper.bypassFileSupplier(
                getLocalJarLambda(getVariantData().getVariantDependency().getRuntimeClasspath()));
    }

    @NonNull
    @Override
    public FileCollection getProvidedOnlyClasspath() {
        FileCollection compile = getArtifactFileCollection(COMPILE_CLASSPATH, ALL, CLASSES);
        FileCollection pkg = getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, CLASSES);
        return compile.minus(pkg);
    }

    @Override
    @NonNull
    public File getIntermediateJarOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/intermediate-jars/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getProguardComponentsJarFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/componentClasses.jar");
    }

    @Override
    @NonNull
    public File getManifestKeepListProguardFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/manifest_keep.txt");
    }

    @Override
    @NonNull
    public File getMainDexListFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/maindexlist.txt");
    }

    @Override
    @NonNull
    public File getRenderscriptSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/rs/" + variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRenderscriptLibOutputDir() {
        return new File(globalScope.getIntermediatesDir(),
                "rs/" + variantData.getVariantConfiguration().getDirName() + "/lib");
    }

    @Override
    @NonNull
    public File getFinalResourcesDir() {
        return MoreObjects.firstNonNull(resourceOutputDir, getDefaultMergeResourcesOutputDir());
    }

    @Override
    public void setResourceOutputDir(@NonNull File resourceOutputDir) {
        this.resourceOutputDir = resourceOutputDir;
    }

    @Override
    @NonNull
    public File getDefaultMergeResourcesOutputDir() {
        return FileUtils.join(
                getGlobalScope().getIntermediatesDir(),
                FD_RES,
                FD_MERGED,
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getMergeResourcesOutputDir() {
        if (mergeResourceOutputDir == null) {
            return getDefaultMergeResourcesOutputDir();
        }
        return mergeResourceOutputDir;
    }

    @Override
    public void setMergeResourceOutputDir(@Nullable File mergeResourceOutputDir) {
        this.mergeResourceOutputDir = mergeResourceOutputDir;
    }

    @Override
    @NonNull
    public File getCompiledResourcesOutputDir() {
        return FileUtils.join(
                getGlobalScope().getIntermediatesDir(),
                FD_RES,
                FD_COMPILED,
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getResourceBlameLogDir() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings(
                        "blame", "res", getDirectorySegments()));
    }

    @NonNull
    @Override
    public File getMergeNativeLibsOutputDir() {
        return FileUtils.join(globalScope.getIntermediatesDir(),
                "/jniLibs/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getMergeShadersOutputDir() {
        return FileUtils.join(globalScope.getIntermediatesDir(),
                "/shaders/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getBuildConfigSourceOutputDir() {
        return new File(globalScope.getBuildDir() + "/"  + FD_GENERATED + "/source/buildConfig/"
                + variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    private File getGeneratedResourcesDir(String name) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                StringHelper.toStrings(
                        "res",
                        name,
                        getDirectorySegments()));
    }

    @NonNull
    private File getGeneratedAssetsDir(String name) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                StringHelper.toStrings(
                        "assets",
                        name,
                        getDirectorySegments()));
    }

    @Override
    @NonNull
    public File getGeneratedResOutputDir() {
        return getGeneratedResourcesDir("resValues");
    }

    @Override
    @NonNull
    public File getGeneratedPngsOutputDir() {
        return getGeneratedResourcesDir("pngs");
    }

    @Override
    @NonNull
    public File getRenderscriptResOutputDir() {
        return getGeneratedResourcesDir("rs");
    }

    @NonNull
    @Override
    public File getRenderscriptObjOutputDir() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings(
                        "rs",
                        getDirectorySegments(),
                        "obj"));
    }

    @NonNull
    @Override
    public File getShadersOutputDir() {
        return getGeneratedAssetsDir("shaders");
    }

    @Override
    @NonNull
    public File getSourceFoldersJavaResDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "sourceFolderJavaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getJavaResourcesDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "javaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRClassSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/r/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getAidlSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/aidl/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getIncrementalDir(String name) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "incremental",
                name);
    }

    @Override
    @NonNull
    public File getPackagedAidlDir() {
        return new File(getBaseBundleDir(), "aidl");
    }

    @NonNull
    @Override
    public File getTypedefFile() {
        return new File(globalScope.getIntermediatesDir(), "typedefs.txt");
    }

    @NonNull
    @Override
    public File getCoverageReportDir() {
        return new File(globalScope.getReportsDir(), "coverage/" + getDirName());
    }

    @Override
    @NonNull
    public File getClassOutputForDataBinding() {
        return new File(globalScope.getGeneratedDir(),
                "source/dataBinding/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getLayoutInfoOutputForDataBinding() {
        return new File(globalScope.getIntermediatesDir() + "/data-binding-info/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getLayoutFolderOutputForDataBinding() {
        return new File(globalScope.getIntermediatesDir() + "/data-binding-layout-out/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getLayoutInputFolderForDataBinding() {
        return new File(
                globalScope.getIntermediatesDir()
                        + "/data-binding-layout-in/"
                        + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getBuildFolderForDataBindingCompiler() {
        return new File(globalScope.getIntermediatesDir() + "/data-binding-compiler/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getGeneratedClassListOutputFileForDataBinding() {
        return new File(getLayoutInfoOutputForDataBinding(), "_generated.txt");
    }

    @NonNull
    @Override
    public File getBundleFolderForDataBinding() {
        return new File(getBaseBundleDir(), DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR);
    }

    @Override
    @NonNull
    public File getProguardOutputFolder() {
        return new File(globalScope.getBuildDir(), "/" + FD_OUTPUTS + "/mapping/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getProcessAndroidResourcesProguardOutputFile() {
        return new File(globalScope.getIntermediatesDir(),
                "/proguard-rules/" + getVariantConfiguration().getDirName() + "/aapt_rules.txt");
    }

    @Override
    @NonNull
    public File getGenerateSplitAbiResOutputDirectory() {
        return new File(
                globalScope.getIntermediatesDir(),
                FileUtils.join("splits", "res", "abi", getVariantConfiguration().getDirName()));
    }

    @Override
    @NonNull
    public File getSplitSupportDirectory() {
        return new File(
                globalScope.getIntermediatesDir(),
                "splits-support/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getSplitDensityOrLanguagesPackagesOutputDirectory() {
        return new File(
                globalScope.getBuildDir(),
                FileUtils.join(
                        FD_OUTPUTS,
                        "splits",
                        "densityLanguage",
                        getVariantConfiguration().getDirName()));
    }

    @NonNull
    @Override
    public File getSplitAbiPackagesOutputDirectory() {
        return new File(
                globalScope.getBuildDir(),
                FileUtils.join(
                        FD_OUTPUTS, "splits", "abi", getVariantConfiguration().getDirName()));
    }

    @NonNull
    @Override
    public File getFullApkPackagesOutputDirectory() {
        return new File(
                globalScope.getBuildDir(),
                FileUtils.join(
                        FD_OUTPUTS, "splits", "full", getVariantConfiguration().getDirName()));
    }

    @NonNull
    @Override
    public File getAaptFriendlyManifestOutputDirectory() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "manifests",
                "aapt",
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunManifestOutputDirectory() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "manifests",
                "instant-run",
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunResourceApkFolder() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "resources",
                "instant-run",
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getMicroApkManifestFile() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "manifests",
                "microapk",
                getVariantConfiguration().getDirName(),
                FN_ANDROID_MANIFEST_XML);
    }

    @NonNull
    @Override
    public File getMicroApkResDirectory() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "res",
                "microapk",
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getCompatibleScreensManifestDirectory() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "manifests",
                "density",
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getManifestOutputDirectory() {
        switch (getVariantConfiguration().getType()) {
            case DEFAULT:
            case FEATURE:
            case LIBRARY:
                return FileUtils.join(
                        getGlobalScope().getIntermediatesDir(),
                        "manifests",
                        "full",
                        getVariantConfiguration().getDirName());
            case ANDROID_TEST:
                return FileUtils.join(
                        getGlobalScope().getIntermediatesDir(),
                        "manifest",
                        getVariantConfiguration().getDirName());
            default:
                throw new RuntimeException(
                        "getManifestOutputDirectory called for an unexpected variant.");
        }
    }

    /**
     * Obtains the location where APKs should be placed.
     *
     * @return the location for APKs
     */
    @NonNull
    @Override
    public File getApkLocation() {
        String override = globalScope.getProjectOptions().get(StringOption.IDE_APK_LOCATION);

        File baseDirectory =
                override != null && variantData.getType() != VariantType.FEATURE
                        ? globalScope.getProject().file(override)
                        : getDefaultApkLocation();

        return new File(baseDirectory, getVariantConfiguration().getDirName());
    }

    /**
     * Obtains the default location for APKs.
     *
     * @return the default location for APKs
     */
    @NonNull
    private File getDefaultApkLocation() {
        return FileUtils.join(globalScope.getBuildDir(), FD_OUTPUTS, "apk");
    }

    private AndroidTask<? extends ManifestProcessorTask> manifestProcessorTask;

    @Override
    public AndroidTask<? extends ManifestProcessorTask> getManifestProcessorTask() {
        return manifestProcessorTask;
    }

    @Override
    public void setManifestProcessorTask(
            AndroidTask<? extends ManifestProcessorTask> manifestProcessorTask) {
        this.manifestProcessorTask = manifestProcessorTask;
    }

    @NonNull
    @Override
    public File getBaseBundleDir() {
        // The base bundle dir must be recomputable from outside of this project.
        // DirName is a set for folders (flavor1/flavor2/buildtype) which is difficult to
        // recompute if all you have is the fullName (flavor1Flavor2Buildtype) as it would
        // require string manipulation which could break if a flavor is using camelcase in
        // its name (myFlavor).
        // So here we use getFullName directly. It's a direct match with the externally visible
        // variant name (which is == to getFullName), and set as the published artifact's
        // classifier.
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                DIR_BUNDLES,
                getVariantConfiguration().getFullName());
    }

    @NonNull
    @Override
    public File getAarLocation() {
        return FileUtils.join(globalScope.getOutputsDir(), BuilderConstants.EXT_LIB_ARCHIVE);
    }

    @NonNull
    @Override
    public File getAnnotationProcessorOutputDir() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "source",
                "apt",
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getMainJarOutputDir() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "packaged",
                getVariantConfiguration().getDirName());
    }
    // Tasks getters/setters.

    @Override
    public AndroidTask<DefaultTask> getAssembleTask() {
        return assembleTask;
    }

    @Override
    public void setAssembleTask(@NonNull AndroidTask<DefaultTask> assembleTask) {
        this.assembleTask = assembleTask;
    }

    @Override
    public AndroidTask<? extends DefaultTask> getPreBuildTask() {
        return preBuildTask;
    }

    @Override
    public void setPreBuildTask(AndroidTask<? extends DefaultTask> preBuildTask) {
        this.preBuildTask = preBuildTask;
    }

    @Override
    public AndroidTask<Task> getSourceGenTask() {
        return sourceGenTask;
    }

    @Override
    public void setSourceGenTask(
            AndroidTask<Task> sourceGenTask) {
        this.sourceGenTask = sourceGenTask;
    }

    @Override
    public AndroidTask<Task> getResourceGenTask() {
        return resourceGenTask;
    }

    @Override
    public void setResourceGenTask(
            AndroidTask<Task> resourceGenTask) {
        this.resourceGenTask = resourceGenTask;
    }

    @Override
    public AndroidTask<Task> getAssetGenTask() {
        return assetGenTask;
    }

    @Override
    public void setAssetGenTask(
            AndroidTask<Task> assetGenTask) {
        this.assetGenTask = assetGenTask;
    }

    @Override
    public AndroidTask<CheckManifest> getCheckManifestTask() {
        return checkManifestTask;
    }

    @Override
    public void setCheckManifestTask(
            AndroidTask<CheckManifest> checkManifestTask) {
        this.checkManifestTask = checkManifestTask;
    }

    @Override
    public AndroidTask<RenderscriptCompile> getRenderscriptCompileTask() {
        return renderscriptCompileTask;
    }

    @Override
    public void setRenderscriptCompileTask(
            AndroidTask<RenderscriptCompile> renderscriptCompileTask) {
        this.renderscriptCompileTask = renderscriptCompileTask;
    }

    @Override
    public AndroidTask<AidlCompile> getAidlCompileTask() {
        return aidlCompileTask;
    }

    @Override
    public void setAidlCompileTask(AndroidTask<AidlCompile> aidlCompileTask) {
        this.aidlCompileTask = aidlCompileTask;
    }

    @Override
    @Nullable
    public AndroidTask<MergeResources> getMergeResourcesTask() {
        return mergeResourcesTask;
    }

    @Override
    public void setMergeResourcesTask(
            @Nullable AndroidTask<MergeResources> mergeResourcesTask) {
        this.mergeResourcesTask = mergeResourcesTask;
    }

    @Override
    @Nullable
    public AndroidTask<MergeSourceSetFolders> getMergeAssetsTask() {
        return mergeAssetsTask;
    }

    @Override
    public void setMergeAssetsTask(
            @Nullable AndroidTask<MergeSourceSetFolders> mergeAssetsTask) {
        this.mergeAssetsTask = mergeAssetsTask;
    }

    @Override
    public AndroidTask<GenerateBuildConfig> getGenerateBuildConfigTask() {
        return generateBuildConfigTask;
    }

    @Override
    public void setGenerateBuildConfigTask(
            AndroidTask<GenerateBuildConfig> generateBuildConfigTask) {
        this.generateBuildConfigTask = generateBuildConfigTask;
    }

    @Override
    public AndroidTask<Sync> getProcessJavaResourcesTask() {
        return processJavaResourcesTask;
    }

    @Override
    public void setProcessJavaResourcesTask(
            AndroidTask<Sync> processJavaResourcesTask) {
        this.processJavaResourcesTask = processJavaResourcesTask;
    }

    @Override
    public void setMergeJavaResourcesTask(
            AndroidTask<TransformTask> mergeJavaResourcesTask) {
        this.mergeJavaResourcesTask = mergeJavaResourcesTask;
    }

    /**
     * Returns the task extracting java resources from libraries and merging those with java
     * resources coming from the variant's source folders.
     * @return the task merging resources.
     */
    @Override
    public AndroidTask<TransformTask> getMergeJavaResourcesTask() {
        return mergeJavaResourcesTask;
    }

    @Override
    @Nullable
    public AndroidTask<? extends  JavaCompile> getJavacTask() {
        return javacTask;
    }

    @Override
    public void setJavacTask(
            @Nullable AndroidTask<? extends JavaCompile> javacTask) {
        this.javacTask = javacTask;
    }

    @Override
    public AndroidTask<Task> getCompileTask() {
        return compileTask;
    }

    @Override
    public void setCompileTask(
            AndroidTask<Task> compileTask) {
        this.compileTask = compileTask;
    }

    @Override
    public AndroidTask<GenerateApkDataTask> getMicroApkTask() {
        return microApkTask;
    }

    @Override
    public void setMicroApkTask(
            AndroidTask<GenerateApkDataTask> microApkTask) {
        this.microApkTask = microApkTask;
    }

    @Override
    public AndroidTask<?> getCoverageReportTask() {
        return coverageReportTask;
    }

    @Override
    public void setCoverageReportTask(AndroidTask<?> coverageReportTask) {
        this.coverageReportTask = coverageReportTask;
    }

    @NonNull private final InstantRunBuildContext instantRunBuildContext;

    @Override
    @NonNull
    public InstantRunBuildContext getInstantRunBuildContext() {
        return instantRunBuildContext;
    }

    @NonNull
    @Override
    public ImmutableList<File> getInstantRunBootClasspath() {
        SdkHandler sdkHandler = getGlobalScope().getSdkHandler();
        AndroidBuilder androidBuilder = globalScope.getAndroidBuilder();
        IAndroidTarget androidBuilderTarget = androidBuilder.getTarget();

        File annotationsJar = sdkHandler.getSdkLoader().getSdkInfo(LOGGER).getAnnotationsJar();

        AndroidVersion targetDeviceVersion =
                DeploymentDevice.getDeploymentDeviceAndroidVersion(
                        getGlobalScope().getProjectOptions());

        if (targetDeviceVersion.equals(androidBuilderTarget.getVersion())) {
            // Compile SDK and the target device match, re-use the target that we have already
            // found earlier.
            return BootClasspathBuilder.computeFullBootClasspath(
                    androidBuilderTarget, annotationsJar);
        }

        IAndroidTarget targetToUse =
                getAndroidTarget(
                        sdkHandler, AndroidTargetHash.getPlatformHashString(targetDeviceVersion));

        if (targetToUse == null) {
            // The device platform is not installed, Studio should have done this already, so fail.
            throw new RuntimeException(
                    String.format(
                            ""
                                    + "In order to use Instant Run with this device running %1$S, "
                                    + "you must install platform %1$S in your SDK",
                            targetDeviceVersion.toString()));
        }

        return BootClasspathBuilder.computeFullBootClasspath(targetToUse, annotationsJar);
    }

    /**
     * Calls the sdklib machinery to construct the {@link IAndroidTarget} for the given hash string.
     *
     * @return appropriate {@link IAndroidTarget} or null if the matching platform package is not
     *         installed.
     */
    @Nullable
    private static IAndroidTarget getAndroidTarget(
            @NonNull SdkHandler sdkHandler,
            @NonNull String targetHash) {
        File sdkLocation = sdkHandler.getSdkFolder();
        ProgressIndicator progressIndicator = new LoggerProgressIndicatorWrapper(LOGGER);
        IAndroidTarget target = AndroidSdkHandler.getInstance(sdkLocation)
                .getAndroidTargetManager(progressIndicator)
                .getTargetFromHashString(targetHash, progressIndicator);
        if (target != null) {
            return target;
        }
        // reset the cached AndroidSdkHandler, next time a target is looked up,
        // this will force the re-parsing of the SDK.
        AndroidSdkHandler.resetInstance(sdkLocation);

        // and let's try immediately, it's possible the platform was installed since the SDK
        // handler was initialized in the this VM, since we reset the instance just above, it's
        // possible we find it.
        return AndroidSdkHandler.getInstance(sdkLocation)
                .getAndroidTargetManager(progressIndicator)
                .getTargetFromHashString(targetHash, progressIndicator);
    }

    @Override
    public void setExternalNativeBuildTask(
            @NonNull AndroidTask<ExternalNativeBuildTask> task) {
        this.externalNativeBuild = task;
    }

    @Nullable
    @Override
    public ExternalNativeJsonGenerator getExternalNativeJsonGenerator() {
        return externalNativeJsonGenerator;
    }

    @Override
    public void setExternalNativeJsonGenerator(@NonNull ExternalNativeJsonGenerator generator) {
        Preconditions.checkState(this.externalNativeJsonGenerator == null,
                "Unexpected overwrite of externalNativeJsonGenerator "
                        + "may result in information loss");
        this.externalNativeJsonGenerator = generator;
    }

    @Nullable
    @Override
    public AndroidTask<ExternalNativeBuildTask> getExternalNativeBuildTask() {
        return externalNativeBuild;
    }

    @Override
    @NonNull
    public List<NativeBuildConfigValue> getExternalNativeBuildConfigValues() {
        return externalNativeBuildConfigValues;
    }

    @Override
    public void addExternalNativeBuildConfigValues(
            @NonNull Collection<NativeBuildConfigValue> values) {
        externalNativeBuildConfigValues.addAll(values);
    }

    @Nullable
    @Override
    public InstantRunTaskManager getInstantRunTaskManager() {
        return instantRunTaskManager;
    }

    @Override
    public void setInstantRunTaskManager(InstantRunTaskManager instantRunTaskManager) {
        this.instantRunTaskManager = instantRunTaskManager;
    }

    @NonNull
    @Override
    public TransformVariantScope getTransformVariantScope() {
        return this;
    }

    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    /**
     * adds or removes the tested artifact and dependencies to ensure the test build is correct.
     *
     * @param collection the collection to add or remove the artifact and dependencies.
     * @param configType the configuration from which to look at dependencies
     * @param artifactType the type of the artifact to add or remove
     * @param plusFunction a function that adds the tested artifact to the collection
     * @param minusFunction a function that removes the tested dependencies from the collection
     * @param <T> the type of the collection
     * @return a new collection containing the result
     */
    @NonNull
    private <T> T handleTestedComponent(
            @NonNull final T collection,
            @NonNull final ConsumedConfigType configType,
            @NonNull final ArtifactScope artifactScope,
            @NonNull final ArtifactType artifactType,
            @NonNull final TriFunction<T, FileCollection, String, T> plusFunction,
            @NonNull final BiFunction<T, ArtifactCollection, T> minusFunction) {
        // this only handles Android Test, not unit tests.
        VariantType variantType = getVariantConfiguration().getType();
        if (!variantType.isForTesting()) {
            return collection;
        }

        T result = collection;

        // get the matching file collection for the tested variant, if any.
        if (variantData instanceof TestVariantData) {
            TestedVariantData tested = ((TestVariantData) variantData).getTestedVariantData();
            final VariantScope testedScope = tested.getScope();

            // we only add the tested component to the MODULE | ALL scopes.
            if (artifactScope == ArtifactScope.MODULE || artifactScope == ALL) {
                VariantPublishingSpec testedSpec =
                        testedScope.getPublishingSpec().getTestingSpec(variantType);

                // get the OutputPublishingSpec from the ArtifactType for this particular variant spec
                OutputPublishingSpec taskOutputSpec = testedSpec.getSpec(artifactType);

                if (taskOutputSpec != null) {
                    Collection<PublishedConfigType> publishedConfigs =
                            taskOutputSpec.getPublishedConfigTypes();

                    // check that we are querying for a config type that the tested artifact
                    // was published to.
                    if (publishedConfigs.contains(configType.getPublishedTo())) {
                        // if it's the case then we add the tested artifact.
                        final OutputType taskOutputType = taskOutputSpec.getOutputType();
                        if (testedScope.hasOutput(taskOutputType)) {
                            result =
                                    plusFunction.apply(
                                            result,
                                            testedScope.getOutput(taskOutputType),
                                            testedScope.getFullVariantName());
                        }
                    }
                }
            }

            // We remove the transitive dependencies coming from the
            // tested app to avoid having the same artifact on each app and tested app.
            // This applies only to the package scope since we do want these in the compile
            // scope in order to compile.
            // We only do this for the AndroidTest.
            // We do have to however keep the Android resources.
            if (tested instanceof ApplicationVariantData
                    && configType == RUNTIME_CLASSPATH
                    && variantType == VariantType.ANDROID_TEST
                    && artifactType != ArtifactType.ANDROID_RES) {
                result =
                        minusFunction.apply(
                                result,
                                testedScope.getArtifactCollection(
                                        configType, artifactScope, artifactType));
            }
        }

        return result;
    }

    @NonNull
    private static Supplier<Collection<File>> getLocalJarLambda(
            @NonNull Configuration configuration) {
        return () -> {
            List<File> files = new ArrayList<>();
            for (Dependency dependency : configuration.getAllDependencies()) {
                if (dependency instanceof SelfResolvingDependency
                        && !(dependency instanceof ProjectDependency)) {
                    files.addAll(((SelfResolvingDependency) dependency).resolve());
                }
            }
            return files;
        };
    }

    @NonNull
    @Override
    public File getProcessResourcePackageOutputDirectory() {
        return FileUtils.join(getGlobalScope().getIntermediatesDir(), FD_RES, getDirName());
    }

    AndroidTask<ProcessAndroidResources> processAndroidResourcesTask;

    @Override
    public void setProcessResourcesTask(
            AndroidTask<ProcessAndroidResources> processAndroidResourcesAndroidTask) {
        this.processAndroidResourcesTask = processAndroidResourcesAndroidTask;
    }

    @Override
    public AndroidTask<ProcessAndroidResources> getProcessResourcesTask() {
        return processAndroidResourcesTask;
    }

    @Override
    public void setDataBindingExportBuildInfoTask(
            AndroidTask<DataBindingExportBuildInfoTask> task) {
        this.dataBindingExportBuildInfoTask = task;
    }

    @Override
    public AndroidTask<DataBindingExportBuildInfoTask> getDataBindingExportBuildInfoTask() {
        return dataBindingExportBuildInfoTask;
    }

    @Override
    @NonNull
    public OutputScope getOutputScope() {
        return variantData.getOutputScope();
    }

    @NonNull
    @Override
    public VariantDependencies getVariantDependencies() {
        return variantData.getVariantDependency();
    }

    @NonNull
    @Override
    public Java8LangSupport getJava8LangSupportType() {
        // in order of precedence
        if (!getGlobalScope()
                .getExtension()
                .getCompileOptions()
                .getTargetCompatibility()
                .isJava8Compatible()) {
            return Java8LangSupport.UNUSED;
        }

        if (globalScope.getProject().getPlugins().hasPlugin("me.tatarka.retrolambda")) {
            return Java8LangSupport.RETROLAMBDA;
        }

        if (globalScope.getProject().getPlugins().hasPlugin("dexguard")) {
            return Java8LangSupport.DEXGUARD;
        }

        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_DESUGAR)) {
            return Java8LangSupport.DESUGAR;
        }

        errorReporter.handleSyncError(
                getVariantConfiguration().getFullName(),
                SyncIssue.TYPE_GENERIC,
                "Please add 'android.enableDesugar=true' to your "
                        + "gradle.properties file to enable Java 8 "
                        + "language support.");
        return Java8LangSupport.INVALID;
    }

    @NonNull
    @Override
    public ConfigurableFileCollection getTryWithResourceRuntimeSupportJar() {
        if (desugarTryWithResourcesRuntimeJar == null) {
            desugarTryWithResourcesRuntimeJar =
                    getProject()
                            .files(
                                    FileUtils.join(
                                            globalScope.getIntermediatesDir(),
                                            "processing-tools",
                                            "runtime-deps",
                                            variantData.getVariantConfiguration().getDirName(),
                                            "desugar_try_with_resources.jar"));
        }
        return desugarTryWithResourcesRuntimeJar;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).addValue(getFullVariantName()).toString();
    }

    @NonNull
    @Override
    public DexerTool getDexer() {
        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_D8)) {
            return DexerTool.D8;
        } else {
            return DexerTool.DX;
        }
    }

    @NonNull
    @Override
    public DexMergerTool getDexMerger() {
        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_D8)) {
            return DexMergerTool.D8;
        } else {
            return DexMergerTool.DX;
        }
    }
}
