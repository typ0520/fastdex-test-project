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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_INTERMEDIATE_FULL_JAR;
import static com.android.SdkConstants.FN_INTERMEDIATE_RES_JAR;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.JAVAC;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesConfigAction;
import com.android.build.gradle.internal.tasks.MergeFileTask;
import com.android.build.gradle.internal.tasks.PackageRenderscriptConfigAction;
import com.android.build.gradle.internal.transforms.LibraryAarJarsTransform;
import com.android.build.gradle.internal.transforms.LibraryBaseTransform;
import com.android.build.gradle.internal.transforms.LibraryIntermediateJarsTransform;
import com.android.build.gradle.internal.transforms.LibraryJniLibsTransform;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.AndroidZip;
import com.android.build.gradle.tasks.ExtractAnnotations;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask;
import com.android.build.gradle.tasks.ZipMergingTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android library project. */
public class LibraryTaskManager extends TaskManager {

    public static final String ANNOTATIONS = "annotations";

    private Task assembleDefault;

    public LibraryTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantScope(
            @NonNull final TaskFactory tasks, @NonNull final VariantScope variantScope) {
        final LibraryVariantData libVariantData =
                (LibraryVariantData) variantScope.getVariantData();
        final GradleVariantConfiguration variantConfig = variantScope.getVariantConfiguration();

        GlobalScope globalScope = variantScope.getGlobalScope();

        final File intermediatesDir = globalScope.getIntermediatesDir();
        final Collection<String> variantDirectorySegments = variantConfig.getDirectorySegments();
        final File variantBundleDir = variantScope.getBaseBundleDir();

        final String projectPath = project.getPath();
        final String variantName = variantScope.getFullVariantName();

        createAnchorTasks(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        createCheckManifestTask(tasks, variantScope);

        // Add a task to create the res values
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                projectPath,
                variantName,
                () -> createGenerateResValuesTask(tasks, variantScope));

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                projectPath,
                variantName,
                () -> createMergeLibManifestsTask(tasks, variantScope));

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                projectPath,
                variantName,
                () -> createRenderscriptTask(tasks, variantScope));

        AndroidTask<MergeResources> packageRes =
                recorder.record(
                        ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                        projectPath,
                        variantName,
                        () -> createMergeResourcesTask(tasks, variantScope, variantBundleDir));

        // Add a task to merge the assets folders
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                projectPath,
                variantName,
                () -> createMergeAssetsTask(tasks, variantScope, null));

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                projectPath,
                variantName,
                () -> createBuildConfigTask(tasks, variantScope));

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                projectPath,
                variantName,
                () -> {
                    // Add a task to generate resource source files, directing the location
                    // of the r.txt file to be directly in the bundle.
                    createProcessResTask(
                            tasks,
                            variantScope,
                            () -> variantBundleDir,
                            variantScope.getProcessResourcePackageOutputDirectory(),
                            // Switch to package where possible so we stop merging resources in
                            // libraries
                            projectOptions.get(BooleanOption.ENABLE_NEW_RESOURCE_PROCESSING)
                                            && projectOptions.get(
                                                    BooleanOption.DISABLE_RES_MERGE_IN_LIBRARY)
                                    ? MergeType.PACKAGE
                                    : MergeType.MERGE,
                            globalScope.getProjectBaseName());

                    // Only verify resources if in Release.
                    if (!variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
                        createVerifyLibraryResTask(tasks, variantScope, MergeType.MERGE);
                    }

                    // process java resources only, the merge is setup after
                    // the task to generate intermediate jars for project to project publishing.
                    createProcessJavaResTask(tasks, variantScope);
                });

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_AIDL_TASK,
                projectPath,
                variantName,
                () -> {
                    AndroidTask<AidlCompile> task = createAidlTask(tasks, variantScope);

                    // publish intermediate aidl folder
                    variantScope.addTaskOutput(
                            TaskOutputType.AIDL_PARCELABLE,
                            new File(variantBundleDir, FD_AIDL),
                            task.getName());
                });

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_SHADER_TASK,
                projectPath,
                variantName,
                () -> createShaderTask(tasks, variantScope));

        // Add a compile task
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_COMPILE_TASK,
                projectPath,
                variantName,
                () -> {
                    // create data binding merge task before the javac task so that it can
                    // parse jars before any consumer
                    createDataBindingMergeArtifactsTaskIfNecessary(tasks, variantScope);

                    // Add data binding tasks if enabled
                    createDataBindingTasksIfNecessary(tasks, variantScope);

                    AndroidTask<? extends JavaCompile> javacTask =
                            createJavacTask(tasks, variantScope);
                    addJavacClassesStream(variantScope);
                    TaskManager.setJavaCompilerTask(javacTask, tasks, variantScope);
                });

        // Add dependencies on NDK tasks if NDK plugin is applied.
        if (!isComponentModelPlugin()) {
            // Add NDK tasks
            recorder.record(
                    ExecutionType.LIB_TASK_MANAGER_CREATE_NDK_TASK,
                    projectPath,
                    variantName,
                    () -> createNdkTasks(tasks, variantScope));
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantScope.getVariantData()));

        // External native build
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                projectPath,
                variantName,
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(tasks, variantScope);
                });

        // TODO not sure what to do about this...
        createMergeJniLibFoldersTasks(tasks, variantScope);
        createStripNativeLibraryTask(tasks, variantScope);

        // package the renderscript header files files into the bundle folder
        File rsFolder = new File(variantScope.getBaseBundleDir(), SdkConstants.FD_RENDERSCRIPT);
        AndroidTask<Sync> packageRenderscriptTask =
                recorder.record(
                        ExecutionType.LIB_TASK_MANAGER_CREATE_PACKAGING_TASK,
                        projectPath,
                        variantName,
                        () -> {
                            AndroidTask<Sync> task =
                                    getAndroidTasks()
                                            .create(
                                                    tasks,
                                                    new PackageRenderscriptConfigAction(
                                                            variantScope, rsFolder));

                            // publish the renderscript intermediate files
                            variantScope.addTaskOutput(
                                    TaskOutputType.RENDERSCRIPT_HEADERS, rsFolder, task.getName());

                            return task;
                        });

        // merge consumer proguard files from different build types and flavors
        AndroidTask<MergeFileTask> mergeProguardFilesTask =
                recorder.record(
                        ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_PROGUARD_FILE_TASK,
                        projectPath,
                        variantName,
                        () -> createMergeFileTask(tasks, variantScope));

        final AndroidZip bundle =
                project.getTasks().create(variantScope.getTaskName("bundle"), AndroidZip.class);
        libVariantData.addTask(TaskContainer.TaskKind.PACKAGE_ANDROID_ARTIFACT, bundle);

        bundle.from(variantScope.getGlobalScope().getOutput(TaskOutputType.LINT_JAR));

        AndroidTask<ExtractAnnotations> extractAnnotationsTask;
        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (projectOptions.get(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS)) {
            extractAnnotationsTask =
                    getAndroidTasks()
                            .create(
                                    tasks,
                                    new ExtractAnnotations.ConfigAction(extension, variantScope));

            // publish intermediate annotation data
            variantScope.addTaskOutput(
                    TaskOutputType.ANNOTATIONS_ZIP,
                    // FIXME with proper value?
                    new File(variantBundleDir, FN_ANNOTATIONS_ZIP),
                    extractAnnotationsTask.getName());

            bundle.dependsOn(extractAnnotationsTask.getName());
        } else {
            extractAnnotationsTask = null;
        }

        final boolean instrumented =
                variantConfig.getBuildType().isTestCoverageEnabled()
                        && !variantScope.getInstantRunBuildContext().isInInstantRunMode();

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_POST_COMPILATION_TASK,
                projectPath,
                variantName,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        TransformManager transformManager = variantScope.getTransformManager();

                        // ----- Code Coverage first -----
                        if (instrumented) {
                            createJacocoTransform(tasks, variantScope);
                        }

                        // ----- External Transforms -----
                        // apply all the external transforms.
                        List<Transform> customTransforms = extension.getTransforms();
                        List<List<Object>> customTransformsDependencies =
                                extension.getTransformsDependencies();

                        for (int i = 0, count = customTransforms.size(); i < count; i++) {
                            Transform transform = customTransforms.get(i);

                            // Check the transform only applies to supported scopes for libraries:
                            // We cannot transform scopes that are not packaged in the library
                            // itself.
                            Sets.SetView<? super Scope> difference =
                                    Sets.difference(
                                            transform.getScopes(), TransformManager.PROJECT_ONLY);
                            if (!difference.isEmpty()) {
                                String scopes = difference.toString();
                                androidBuilder
                                        .getErrorReporter()
                                        .handleSyncError(
                                                "",
                                                SyncIssue.TYPE_GENERIC,
                                                String.format(
                                                        "Transforms with scopes '%s' cannot be applied to library projects.",
                                                        scopes));
                            }

                            List<Object> deps = customTransformsDependencies.get(i);
                            transformManager
                                    .addTransform(tasks, variantScope, transform)
                                    .ifPresent(
                                            t -> {
                                                if (!deps.isEmpty()) {
                                                    t.dependsOn(tasks, deps);
                                                }

                                                // if the task is a no-op then we make assemble task
                                                // depend on it.
                                                if (transform.getScopes().isEmpty()) {
                                                    variantScope
                                                            .getAssembleTask()
                                                            .dependsOn(tasks, t);
                                                }
                                            });
                        }

                        String packageName = variantConfig.getPackageFromManifest();
                        if (packageName == null) {
                            throw new BuildException("Failed to read manifest", null);
                        }

                        // Now add transforms for intermediate publishing (projects to projects).
                        File jarOutputFolder = variantScope.getIntermediateJarOutputFolder();
                        File mainClassJar = new File(jarOutputFolder, FN_CLASSES_JAR);
                        File mainResJar = new File(jarOutputFolder, FN_INTERMEDIATE_RES_JAR);
                        LibraryIntermediateJarsTransform intermediateTransform =
                                new LibraryIntermediateJarsTransform(
                                        mainClassJar,
                                        mainResJar,
                                        null,
                                        packageName,
                                        extension.getPackageBuildConfig());
                        excludeDataBindingClassesIfNecessary(variantScope, intermediateTransform);

                        Optional<AndroidTask<TransformTask>> intermediateTransformTask =
                                transformManager.addTransform(
                                        tasks, variantScope, intermediateTransform);

                        intermediateTransformTask.ifPresent(
                                t -> {
                                    // publish the intermediate classes.jar.
                                    variantScope.addTaskOutput(
                                            TaskOutputType.LIBRARY_CLASSES,
                                            mainClassJar,
                                            t.getName());
                                    // publish the res jar
                                    variantScope.addTaskOutput(
                                            TaskOutputType.LIBRARY_JAVA_RES,
                                            mainResJar,
                                            t.getName());
                                });

                        // Create a jar with both classes and java resources.  This artifact is not
                        // used by the Android application plugin and the task usually don't need to
                        // be executed.  The artifact is useful for other Gradle users who needs the
                        // 'jar' artifact as API dependency.
                        File mainFullJar = new File(jarOutputFolder, FN_INTERMEDIATE_FULL_JAR);
                        AndroidTask<ZipMergingTask> zipMerger =
                                androidTasks.create(
                                        tasks,
                                        new ZipMergingTask.ConfigAction(variantScope, mainFullJar));

                        variantScope.addTaskOutput(
                                TaskOutputType.FULL_JAR, mainFullJar, zipMerger.getName());

                        // now add a transform that will take all the native libs and package
                        // them into an intermediary folder. This processes only the PROJECT
                        // scope.
                        final File intermediateJniLibsFolder = new File(jarOutputFolder, FD_JNI);

                        LibraryJniLibsTransform intermediateJniTransform =
                                new LibraryJniLibsTransform(
                                        "intermediateJniLibs",
                                        intermediateJniLibsFolder,
                                        TransformManager.PROJECT_ONLY);
                        Optional<AndroidTask<TransformTask>> task =
                                transformManager.addTransform(
                                        tasks, variantScope, intermediateJniTransform);
                        task.ifPresent(
                                t -> {
                                    // publish the jni folder as intermediate
                                    variantScope.addTaskOutput(
                                            TaskOutputType.LIBRARY_JNI,
                                            intermediateJniLibsFolder,
                                            t.getName());
                                });

                        // Now go back to fill the pipeline with transforms used when
                        // publishing the AAR

                        // first merge the resources. This takes the PROJECT and LOCAL_DEPS
                        // and merges them together.
                        createMergeJavaResTransform(tasks, variantScope);

                        // ----- Minify next -----
                        maybeCreateJavaCodeShrinkerTransform(tasks, variantScope);
                        maybeCreateResourcesShrinkerTransform(tasks, variantScope);

                        // now add a transform that will take all the class/res and package them
                        // into the main and secondary jar files that goes in the AAR.
                        // This transform technically does not use its transform output, but that's
                        // ok. We use the transform mechanism to get incremental data from
                        // the streams.
                        // This is used for building the AAR.

                        LibraryAarJarsTransform transform =
                                new LibraryAarJarsTransform(
                                        new File(variantBundleDir, FN_CLASSES_JAR),
                                        new File(variantBundleDir, LIBS_FOLDER),
                                        extractAnnotationsTask != null
                                                ? variantScope.getTypedefFile()
                                                : null,
                                        packageName,
                                        extension.getPackageBuildConfig());
                        excludeDataBindingClassesIfNecessary(variantScope, transform);

                        Optional<AndroidTask<TransformTask>> libraryJarTransformTask =
                                transformManager.addTransform(tasks, variantScope, transform);
                        libraryJarTransformTask.ifPresent(
                                t -> {
                                    bundle.dependsOn(t.getName());
                                    t.optionalDependsOn(tasks, extractAnnotationsTask);
                                });

                        // now add a transform that will take all the native libs and package
                        // them into the libs folder of the bundle. This processes both the PROJECT
                        // and the LOCAL_PROJECT scopes
                        final File jniLibsFolder = new File(variantBundleDir, FD_JNI);
                        LibraryJniLibsTransform jniTransform =
                                new LibraryJniLibsTransform(
                                        "syncJniLibs",
                                        jniLibsFolder,
                                        TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS);
                        Optional<AndroidTask<TransformTask>> jniPackagingTask =
                                transformManager.addTransform(tasks, variantScope, jniTransform);
                        jniPackagingTask.ifPresent(t -> bundle.dependsOn(t.getName()));

                        return null;
                    }
                });

        bundle.dependsOn(
                packageRes.getName(),
                packageRenderscriptTask.getName(),
                mergeProguardFilesTask.getName(),
                // The below dependencies are redundant in a normal build as
                // generateSources depends on them. When generateSourcesOnly is injected they are
                // needed explicitly, as bundle no longer depends on compileJava
                variantScope.getAidlCompileTask().getName(),
                variantScope.getMergeAssetsTask().getName());
        bundle.dependsOn(variantScope.getNdkBuildable());

        Preconditions.checkNotNull(variantScope.getOutputScope().getMainSplit());
        bundle.setDescription(
                "Assembles a bundle containing the library in "
                        + variantConfig.getFullName()
                        + ".");
        bundle.setDestinationDir(variantScope.getAarLocation());
        bundle.setArchiveNameSupplier(
                () -> variantScope.getOutputScope().getMainSplit().getOutputFileName());
        bundle.setExtension(BuilderConstants.EXT_LIB_ARCHIVE);
        bundle.from(variantScope.getOutput(TaskOutputType.LIBRARY_MANIFEST));
        bundle.from(variantBundleDir);
        bundle.from(
                FileUtils.join(
                        intermediatesDir,
                        StringHelper.toStrings(ANNOTATIONS, variantDirectorySegments)));

        variantScope.addTaskOutput(
                TaskOutputType.AAR,
                (Callable<File>)
                        () ->
                                new File(
                                        variantScope.getAarLocation(),
                                        variantScope
                                                .getOutputScope()
                                                .getMainSplit()
                                                .getOutputFileName()),
                bundle.getName());

        libVariantData.packageLibTask = bundle;

        variantScope.getAssembleTask().dependsOn(tasks, bundle);

        // if the variant is the default published, then publish the aar
        // FIXME: only generate the tasks if this is the default published variant?
        if (extension.getDefaultPublishConfig().equals(variantConfig.getFullName())) {
            VariantHelper.setupArchivesConfig(
                    project, variantScope.getVariantDependencies().getRuntimeClasspath());

            // add the artifact that will be published.
            // it must be default so that it can be found by other library modules during
            // publishing to a maven repo. Adding it to "archives" only allows the current
            // module to be published by not to be found by consumer who are themselves published
            // (leading to their pom not containing dependencies).
            project.getArtifacts().add("default", bundle);
        }

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_LINT_TASK,
                projectPath,
                variantName,
                () -> createLintTasks(tasks, variantScope));
    }

    @Override
    protected void createDependencyStreams(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        super.createDependencyStreams(tasks, variantScope);

        // add the same jars twice in the same stream as the EXTERNAL_LIB in the task manager
        // so that filtering of duplicates in proguard can work.
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-deps-classes")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setJars(variantScope.getLocalPackagedJars())
                                .build());

        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-deps-native")
                                .addContentTypes(
                                        DefaultContentType.RESOURCES,
                                        ExtendedContentType.NATIVE_LIBS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setJars(variantScope.getLocalPackagedJars())
                                .build());
    }

    @NonNull
    private AndroidTask<MergeFileTask> createMergeFileTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        File outputFile = new File(variantScope.getBaseBundleDir(), SdkConstants.FN_PROGUARD_TXT);

        final AndroidTask<MergeFileTask> task =
                getAndroidTasks()
                        .create(
                                tasks,
                                new MergeConsumerProguardFilesConfigAction(
                                        project,
                                        androidBuilder.getErrorReporter(),
                                        variantScope,
                                        outputFile));

        variantScope.addTaskOutput(
                TaskOutputType.CONSUMER_PROGUARD_FILE, outputFile, task.getName());

        return task;
    }

    @NonNull
    private AndroidTask<MergeResources> createMergeResourcesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @NonNull File variantBundleDir) {
        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        File resFolder = FileUtils.join(variantBundleDir, FD_RES);
        AndroidTask<MergeResources> mergeResourceTask =
                basicCreateMergeResourcesTask(
                        tasks, variantScope, MergeType.PACKAGE, resFolder, false, false, false);

        // Add a task to merge the resource folders, including the libraries, in order to
        // generate the R.txt file with all the symbols, including the ones from
        // the dependencies.
        createMergeResourcesTask(tasks, variantScope, false /*processResources*/);

        File publicTxt = new File(variantBundleDir, FN_PUBLIC_TXT);

        mergeResourceTask.configure(tasks, task -> task.setPublicFile(publicTxt));

        // publish the intermediate public res file
        variantScope.addTaskOutput(
                TaskOutputType.PUBLIC_RES, publicTxt, mergeResourceTask.getName());
        return mergeResourceTask;
    }

    @Override
    protected void postJavacCreation(
            @NonNull final TaskFactory tasks, @NonNull VariantScope scope) {
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection fileCollection =
                scope.createAnchorOutput(TaskOutputHolder.AnchorOutputType.ALL_CLASSES);
        fileCollection.from(scope.getOutput(JAVAC));
        fileCollection.from(scope.getVariantData().getAllPreJavacGeneratedBytecode());
        fileCollection.from(scope.getVariantData().getAllPostJavacGeneratedBytecode());
    }

    private void excludeDataBindingClassesIfNecessary(
            @NonNull VariantScope variantScope, @NonNull LibraryBaseTransform transform) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        transform.addExcludeListProvider(
                () -> {
                    File excludeFile =
                            variantScope.getVariantData().getType().isExportDataBindingClassList()
                                    ? variantScope.getGeneratedClassListOutputFileForDataBinding()
                                    : null;
                    File dataBindingFolder = variantScope.getBuildFolderForDataBindingCompiler();
                    return dataBindingBuilder.getJarExcludeList(
                            variantScope.getVariantData().getLayoutXmlProcessor(),
                            excludeFile,
                            dataBindingFolder);
                });
    }

    @NonNull
    @Override
    protected Set<? super Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        if (variantScope.getTestedVariantData() != null) {
            return TransformManager.SCOPE_FULL_PROJECT;
        }
        return TransformManager.PROJECT_ONLY;
    }

    @Override
    protected boolean isLibrary() {
        return true;
    }

    private Task getAssembleDefault() {
        if (assembleDefault == null) {
            assembleDefault = project.getTasks().findByName("assembleDefault");
        }
        return assembleDefault;
    }

    public void createVerifyLibraryResTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope scope, @NonNull MergeType mergeType) {
        AndroidTask<VerifyLibraryResourcesTask> verifyLibraryResources =
                androidTasks.create(
                        tasks, new VerifyLibraryResourcesTask.ConfigAction(scope, mergeType));

        verifyLibraryResources.dependsOn(tasks, scope.getMergeResourcesTask());
        scope.getAssembleTask().dependsOn(tasks, verifyLibraryResources);
    }
}
