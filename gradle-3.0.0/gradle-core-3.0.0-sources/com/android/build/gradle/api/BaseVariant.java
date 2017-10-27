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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.model.BuildType;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SourceProvider;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.compile.JavaCompile;


/**
 * A Build variant and all its public data. This is the base class for items common to apps,
 * test apps, and libraries
 */
public interface BaseVariant {

    /**
     * Returns the name of the variant. Guaranteed to be unique.
     */
    @NonNull
    String getName();

    /**
     * Returns a description for the build variant.
     */
    @NonNull
    String getDescription();

    /**
     * Returns a subfolder name for the variant. Guaranteed to be unique.
     *
     * This is usually a mix of build type and flavor(s) (if applicable).
     * For instance this could be:
     * "debug"
     * "debug/myflavor"
     * "release/Flavor1Flavor2"
     */
    @NonNull
    String getDirName();

    /**
     * Returns the base name for the output of the variant. Guaranteed to be unique.
     */
    @NonNull
    String getBaseName();

    /**
     * Returns the flavor name of the variant. This is a concatenation of all the
     * applied flavors
     * @return the name of the flavors, or an empty string if there is not flavors.
     */
    @NonNull
    String getFlavorName();

    /**
     * Returns the variant outputs. There should always be at least one output.
     *
     * @return a non-null list of variants.
     */
    @NonNull
    DomainObjectCollection<BaseVariantOutput> getOutputs();

    /**
     * Returns the {@link com.android.builder.core.DefaultBuildType} for this build variant.
     */
    @NonNull
    BuildType getBuildType();

    /**
     * Returns a {@link com.android.builder.core.DefaultProductFlavor} that represents the merging
     * of the default config and the flavors of this build variant.
     */
    @NonNull
    ProductFlavor getMergedFlavor();

    /**
     * Returns a {@link JavaCompileOptions} that represents the java compile settings for this build
     * variant.
     */
    @NonNull
    JavaCompileOptions getJavaCompileOptions();

    /**
     * Returns the list of {@link com.android.builder.core.DefaultProductFlavor} for this build
     * variant.
     *
     * <p>This is always non-null but could be empty.
     */
    @NonNull
    List<ProductFlavor> getProductFlavors();

    /**
     * Returns a list of sorted SourceProvider in order of ascending order, meaning, the earlier
     * items are meant to be overridden by later items.
     *
     * @return a list of source provider
     */
    @NonNull
    List<SourceProvider> getSourceSets();

    /**
     * Returns a list of FileCollection representing the source folders.
     *
     * @param folderType the type of folder to return.
     * @return a list of folder + dependency as file collections.
     */
    @NonNull
    List<ConfigurableFileTree> getSourceFolders(@NonNull SourceKind folderType);

    /** Returns the configuration object for the compilation */
    @NonNull
    Configuration getCompileConfiguration();

    /** Returns the configuration object for the annotation processor. */
    @NonNull
    Configuration getAnnotationProcessorConfiguration();

    /** Returns the configuration object for the runtime */
    @NonNull
    Configuration getRuntimeConfiguration();

    /** Returns the applicationId of the variant. */
    @NonNull
    String getApplicationId();

    /** Returns the pre-build anchor task */
    @NonNull
    Task getPreBuild();

    /**
     * Returns the check manifest task.
     */
    @NonNull
    Task getCheckManifest();

    /**
     * Returns the AIDL compilation task.
     */
    @NonNull
    AidlCompile getAidlCompile();

    /**
     * Returns the Renderscript compilation task.
     */
    @NonNull
    RenderscriptCompile getRenderscriptCompile();

    /**
     * Returns the resource merging task.
     */
    @Nullable
    MergeResources getMergeResources();

    /**
     * Returns the asset merging task.
     */
    @Nullable
    MergeSourceSetFolders getMergeAssets();

    /**
     * Returns the BuildConfig generation task.
     */
    @Nullable
    GenerateBuildConfig getGenerateBuildConfig();

    /**
     * Returns the Java Compilation task if javac was configured to compile the source files.
     * @deprecated prefer {@link #getJavaCompiler} which always return the java compiler task
     * irrespective of which tool chain (javac or jack) used.
     */
    @Nullable
    @Deprecated
    JavaCompile getJavaCompile() throws IllegalStateException;

    /**
     * Returns the Java Compiler task which can be either javac or jack depending on the project
     * configuration.
     */
    @NonNull
    Task getJavaCompiler();

    /**
     * Returns the java compilation classpath.
     *
     * <p>The provided key allows controlling how much of the classpath is returned.
     *
     * <ul>
     *   <li>if <code>null</code>, the full classpath is returned
     *   <li>Otherwise the method returns the classpath up to the generated bytecode associated with
     *       the key
     * </ul>
     *
     * @param key the key
     * @see #registerGeneratedBytecode(FileCollection)
     */
    @NonNull
    FileCollection getCompileClasspath(@Nullable Object key);

    /**
     * Returns the java compilation classpath as an ArtifactCollection
     *
     * <p>The provided key allows controlling how much of the classpath is returned.
     *
     * <ul>
     *   <li>if <code>null</code>, the full classpath is returned
     *   <li>Otherwise the method returns the classpath up to the generated bytecode associated with
     *       the key
     * </ul>
     *
     * @param key the key
     * @see #registerGeneratedBytecode(FileCollection)
     */
    @NonNull
    ArtifactCollection getCompileClasspathArtifacts(@Nullable Object key);

    /**
     * Returns the file and task dependency of the folder containing all the merged data-binding
     * artifacts coming from the dependency.
     *
     * <p>If data-binding is not enabled the file collection will be empty.
     *
     * @return the file collection containing the folder or nothing.
     */
    @NonNull
    FileCollection getDataBindingDependencyArtifacts();

    /** Returns the NDK Compilation task. */
    @NonNull
    NdkCompile getNdkCompile();

    /**
     * Returns the tasks for building external native projects.
     */
    Collection<ExternalNativeBuildTask> getExternalNativeBuildTasks();

    /**
     * Returns the obfuscation task. This can be null if obfuscation is not enabled.
     */
    @Nullable
    Task getObfuscation();

    /**
     * Returns the obfuscation mapping file. This can be null if obfuscation is not enabled.
     */
    @Nullable
    File getMappingFile();

    /**
     * Returns the Java resource processing task.
     */
    @NonNull
    AbstractCopyTask getProcessJavaResources();

    /**
     * Returns the assemble task for all this variant's output
     */
    @Nullable
    Task getAssemble();

    /**
     * Adds new Java source folders to the model.
     *
     * These source folders will not be used for the default build
     * system, but will be passed along the default Java source folders
     * to whoever queries the model.
     *
     * @param sourceFolders the source folders where the generated source code is.
     */
    void addJavaSourceFoldersToModel(@NonNull File... sourceFolders);

    /**
     * Adds new Java source folders to the model.
     *
     * These source folders will not be used for the default build
     * system, but will be passed along the default Java source folders
     * to whoever queries the model.
     *
     * @param sourceFolders the source folders where the generated source code is.
     */
    void addJavaSourceFoldersToModel(@NonNull Collection<File> sourceFolders);

    /**
     * Adds to the variant a task that generates Java source code.
     *
     * This will make the generate[Variant]Sources task depend on this task and add the
     * new source folders as compilation inputs.
     *
     * The new source folders are also added to the model.
     *
     * @param task the task
     * @param sourceFolders the source folders where the generated source code is.
     */
    void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... sourceFolders);

    /**
     * Adds to the variant a task that generates Java source code.
     *
     * This will make the generate[Variant]Sources task depend on this task and add the
     * new source folders as compilation inputs.
     *
     * The new source folders are also added to the model.
     *
     * @param task the task
     * @param sourceFolders the source folders where the generated source code is.
     */
    void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> sourceFolders);

    /**
     * Register the output of an external annotation processor.
     *
     * <p>The output is passed to the javac task, but the source generation hooks does not depend on
     * this.
     *
     * <p>In order to properly wire up tasks, the FileTree object must include dependency
     * information about the task that generates the content of this folders.
     *
     * @param folder a ConfigurableFileTree that contains a single folder and the task dependency
     *     information
     */
    void registerExternalAptJavaOutput(@NonNull ConfigurableFileTree folder);

    /**
     * Adds to the variant new generated resource folders.
     *
     * <p>In order to properly wire up tasks, the FileCollection object must include dependency
     * information about the task that generates the content of this folders.
     *
     * @param folders a FileCollection that contains the folders and the task dependency information
     */
    void registerGeneratedResFolders(@NonNull FileCollection folders);

    /**
     * Adds to the variant a task that generates Resources.
     *
     * This will make the generate[Variant]Resources task depend on this task and add the
     * new Resource folders as Resource merge inputs.
     *
     * The Resource folders are also added to the model.
     *
     * @param task the task
     * @param resFolders the folders where the generated resources are.
     *
     * @deprecated Use {@link #registerGeneratedResFolders(FileCollection)}
     */
    @Deprecated
    void registerResGeneratingTask(@NonNull Task task, @NonNull File... resFolders);

    /**
     * Adds to the variant a task that generates Resources.
     *
     * This will make the generate[Variant]Resources task depend on this task and add the
     * new Resource folders as Resource merge inputs.
     *
     * The Resource folders are also added to the model.
     *
     * @param task the task
     * @param resFolders the folders where the generated resources are.
     *
     * @deprecated Use {@link #registerGeneratedResFolders(FileCollection)}
     */
    @Deprecated
    void registerResGeneratingTask(@NonNull Task task, @NonNull Collection<File> resFolders);

    /**
     * Adds to the variant new generated Java byte-code.
     *
     * <p>This bytecode is passed to the javac classpath. This is typically used by compilers for
     * languages that generate bytecode ahead of javac.
     *
     * <p>The file collection can contain either a folder of class files or jars.
     *
     * <p>In order to properly wire up tasks, the FileCollection object must include dependency
     * information about the task that generates the content of these folders. This is generally
     * setup using {@link org.gradle.api.file.ConfigurableFileCollection#builtBy(Object...)}
     *
     * <p>The generated byte-code will also be added to the transform pipeline as a {@link
     * com.android.build.api.transform.QualifiedContent.Scope#PROJECT} stream.
     *
     * <p>The method returns a key that can be used to query for the compilation classpath. This
     * allows each successive call to {@link #registerPreJavacGeneratedBytecode(FileCollection)} to
     * be associated with a classpath containing everything <strong>before</strong> the added
     * bytecode.
     *
     * @param fileCollection a FileCollection that contains the files and the task dependency
     *     information
     * @return a key for calls to {@link #registerGeneratedBytecode(FileCollection)}
     */
    Object registerPreJavacGeneratedBytecode(@NonNull FileCollection fileCollection);

    /** @deprecated use {@link #registerPreJavacGeneratedBytecode(FileCollection)} */
    @Deprecated
    Object registerGeneratedBytecode(@NonNull FileCollection fileCollection);

    /**
     * Adds to the variant new generated Java byte-code.
     *
     * <p>This bytecode is meant to be post javac, which means javac does not have it on its
     * classpath. It's is only added to the java compilation task's classpath and will be added to
     * the transform pipeline as a {@link
     * com.android.build.api.transform.QualifiedContent.Scope#PROJECT} stream.
     *
     * <p>The file collection can contain either a folder of class files or jars.
     *
     * <p>In order to properly wire up tasks, the FileCollection object must include dependency
     * information about the task that generates the content of these folders. This is generally
     * setup using {@link org.gradle.api.file.ConfigurableFileCollection#builtBy(Object...)}
     *
     * @param fileCollection a FileCollection that contains the files and the task dependency
     *     information
     */
    void registerPostJavacGeneratedBytecode(@NonNull FileCollection fileCollection);

    /**
     * Adds a variant-specific BuildConfig field.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    void buildConfigField(@NonNull String type, @NonNull String name, @NonNull String value);

    /**
     * Adds a variant-specific res value.
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    void resValue(@NonNull String type, @NonNull String name, @NonNull String value);

    /**
     * Set up a new matching request for a given flavor dimension and value.
     *
     * <p>To learn more, read <a href="d.android.com/r/tools/use-flavorSelection.html">Select
     * default flavors for missing dimensions</a>.
     *
     * @param dimension the flavor dimension
     * @param requestedValue the flavor name
     */
    void missingDimensionStrategy(@NonNull String dimension, @NonNull String requestedValue);

    /**
     * Set up a new matching request for a given flavor dimension and value.
     *
     * <p>To learn more, read <a href="d.android.com/r/tools/use-flavorSelection.html">Select
     * default flavors for missing dimensions</a>.
     *
     * @param dimension the flavor dimension
     * @param requestedValues the flavor name and fallbacks
     */
    void missingDimensionStrategy(@NonNull String dimension, @NonNull String... requestedValues);

    /**
     * Set up a new matching request for a given flavor dimension and value.
     *
     * <p>To learn more, read <a href="d.android.com/r/tools/use-flavorSelection.html">Select
     * default flavors for missing dimensions</a>.
     *
     * @param dimension the flavor dimension
     * @param requestedValues the flavor name and fallbacks
     */
    void missingDimensionStrategy(@NonNull String dimension, @NonNull List<String> requestedValues);

    /**
     * If true, variant outputs will be considered signed. Only set if you manually set the outputs
     * to point to signed files built by other tasks.
     */
    void setOutputsAreSigned(boolean isSigned);

    /**
     * @see #setOutputsAreSigned(boolean)
     */
    boolean getOutputsAreSigned();

}
