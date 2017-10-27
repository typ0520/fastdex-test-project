/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_JAVA;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.LibraryTaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AbstractAndroidCompile;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.tasks.annotations.Extractor;
import com.android.build.gradle.tasks.annotations.TypedefRemover;
import com.android.builder.core.AndroidBuilder;
import com.android.tools.lint.LintCoreApplicationEnvironment;
import com.android.tools.lint.LintCoreProjectEnvironment;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiJavaFile;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Task which extracts annotations from the source files, and writes them to one of two possible
 * destinations:
 *
 * <ul>
 *   <li>A "external annotations" file (pointed to by {@link ExtractAnnotations#output}) which
 *       records the annotations in a zipped XML format for use by the IDE and by lint to associate
 *       the (source retention) annotations back with the compiled code
 * </ul>
 *
 * We typically only extract external annotations when building libraries; ProGuard annotations are
 * extracted when building libraries (to record in the AAR), <b>or</b> when building an app module
 * where ProGuarding is enabled.
 */
@CacheableTask
public class ExtractAnnotations extends AbstractAndroidCompile {
    private BaseVariantData variant;

    private Supplier<List<String>> bootClasspath;

    private File typedefFile;

    private File output;

    private String encoding;

    private FileCollection classDir;

    private ArtifactCollection libraries;

    public void setVariant(BaseVariantData variant) {
        this.variant = variant;
    }

    /** Boot classpath: typically android.jar */
    @CompileClasspath
    public List<String> getBootClasspath() {
        return bootClasspath.get();
    }

    public void setBootClasspath(Supplier<List<String>> bootClasspath) {
        this.bootClasspath = bootClasspath;
    }

    @CompileClasspath
    public FileCollection getLibraries() {
        return libraries.getArtifactFiles();
    }

    /** The output .zip file to write the annotations database to, if any */
    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    /**
     * The output .txt file to write the typedef recipe file to. A "recipe" file
     * is a file which describes typedef classes, typically ones that should
     * be deleted. It is generated by this {@link ExtractAnnotations} task and
     * consumed by the {@link TypedefRemover}.
     */
    @OutputFile
    public File getTypedefFile() {
        return typedefFile;
    }

    public void setTypedefFile(File typedefFile) {
        this.typedefFile = typedefFile;
    }

    /**
     * The encoding to use when reading source files. The output file will ignore this and will
     * always be a UTF-8 encoded .xml file inside the annotations zip file.
     */
    @NonNull
    @Input
    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Location of class files. If set, any non-public typedef source retention annotations will be
     * removed prior to .jar packaging.
     */
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getClassDir() {
        return classDir;
    }

    public void setClassDir(FileCollection classDir) {
        this.classDir = classDir;
    }

    @Override
    @TaskAction
    protected void compile() {
        if (!hasAndroidAnnotations()) {
            try {
                Files.write(
                        typedefFile.toPath(),
                        Collections.singletonList(""),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return;
        }

        LintCoreApplicationEnvironment appEnv = LintCoreApplicationEnvironment.get();
        Disposable parentDisposable = Disposer.newDisposable();

        try {
            LintCoreProjectEnvironment projectEnvironment =
                    LintCoreProjectEnvironment.create(parentDisposable, appEnv);

            List<PsiJavaFile> parsedUnits = parseSources(projectEnvironment);

            boolean displayInfo = getLogger().isEnabled(LogLevel.INFO);

            Extractor extractor =
                    new Extractor(null, classDir.getSingleFile(), displayInfo, false, false);

            extractor.extractFromProjectSource(parsedUnits);
            extractor.export(output, null);
            extractor.writeTypedefFile(typedefFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            Disposer.dispose(parentDisposable);
            LintCoreApplicationEnvironment.clearAccessorCache();
        }
    }

    @Input
    public boolean hasAndroidAnnotations() {
        for (ResolvedArtifactResult artifact : libraries.getArtifacts()) {
            ComponentIdentifier id = artifact.getId()
                    .getComponentIdentifier();
            // because we only ask for external dependencies, we should be able to cast
            // this always
            if (id instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) id;

                if (moduleId.getModule().equals("support-annotations") &&
                        moduleId.getGroup().equals("com.android.support")) {
                    return true;
                }
            }

        }

        return false;
    }

    @NonNull
    private List<PsiJavaFile> parseSources(LintCoreProjectEnvironment projectEnvironment) {
        SourceFileVisitor fileVisitor = new SourceFileVisitor();
        getSource().visit(fileVisitor);
        List<File> sourceFiles = fileVisitor.sourceUnits;
        List<File> sourceRoots = fileVisitor.getSourceRoots();

        if (getClasspath() != null) {
            for (File jar : getClasspath()) {
                sourceRoots.add(jar);
            }
        }
        for (String path : getBootClasspath()) {
            sourceRoots.add(new File(path));
        }

        projectEnvironment.registerPaths(sourceRoots);

        return Extractor.createUnitsForFiles(projectEnvironment.getProject(), sourceFiles);
    }

    public static class ConfigAction implements TaskConfigAction<ExtractAnnotations> {

        @NonNull private AndroidConfig extension;
        @NonNull private VariantScope variantScope;
        private File outputFile;

        public ConfigAction(
                @NonNull AndroidConfig extension,
                @NonNull VariantScope variantScope) {
            this.extension = extension;
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("extract", "Annotations");
        }

        @NonNull
        @Override
        public Class<ExtractAnnotations> getType() {
            return ExtractAnnotations.class;
        }

        @Override
        public void execute(@NonNull ExtractAnnotations task) {
            final GradleVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
            final AndroidBuilder androidBuilder = variantScope.getGlobalScope().getAndroidBuilder();

            task.setDescription(
                    "Extracts Android annotations for the "
                            + variantConfig.getFullName()
                            + " variant into the archive file");
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setVariant(variantScope.getVariantData());
            task.setDestinationDir(
                    new File(
                            variantScope.getGlobalScope().getIntermediatesDir(),
                            LibraryTaskManager.ANNOTATIONS + "/" + variantConfig.getDirName()));
            outputFile = new File(task.getDestinationDir(), SdkConstants.FN_ANNOTATIONS_ZIP);
            task.setOutput(outputFile);
            task.setTypedefFile(variantScope.getTypedefFile());

            // FIXME Replace with TaskOutputHolder.AnchorOutputType.ALL_CLASSES
            // https://issuetracker.google.com/64344432
            task.setClassDir(variantScope.getOutput(TaskOutputHolder.TaskOutputType.JAVAC));

            task.setSource(variantScope.getVariantData().getJavaSources());
            task.setEncoding(extension.getCompileOptions().getEncoding());
            task.setSourceCompatibility(
                    extension.getCompileOptions().getSourceCompatibility().toString());
            task.setClasspath(variantScope.getJavaClasspath(COMPILE_CLASSPATH, CLASSES));

            task.libraries = variantScope.getArtifactCollection(
                    COMPILE_CLASSPATH, EXTERNAL, CLASSES);

            // Setup the boot classpath just before the task actually runs since this will
            // force the sdk to be parsed. (Same as in compileTask)
            task.setBootClasspath(() -> androidBuilder.getBootClasspathAsStrings(false));

            ((LibraryVariantData) variantScope.getVariantData()).generateAnnotationsTask = task;
        }
    }

    /**
     * Visitor which gathers a series of individual source files as well as inferring the
     * set of source roots
     */
    private static class SourceFileVisitor extends EmptyFileVisitor {
        private final List<File> sourceUnits = Lists.newArrayListWithExpectedSize(100);
        private final List<File> sourceRoots = Lists.newArrayList();

        private String mostRecentRoot = "\000";

        public SourceFileVisitor() {
        }

        public List<File> getSourceFiles() {
            return sourceUnits;
        }

        public List<File> getSourceRoots() {
            return sourceRoots;
        }

        private static final String BUILD_GENERATED = File.separator + "build" + File.separator
                + "generated" + File.separator;

        @Override
        public void visitFile(FileVisitDetails details) {
            File file = details.getFile();
            String path = file.getPath();
            if (path.endsWith(DOT_JAVA) && !path.contains(BUILD_GENERATED)) {
                // Infer the source roots. These are available as relative paths
                // on the file visit details.
                if (!path.startsWith(mostRecentRoot)) {
                    RelativePath relativePath = details.getRelativePath();
                    String pathString = relativePath.getPathString();
                    // The above method always uses / as a file separator but for
                    // comparisons with the path we need to use the native separator:
                    pathString = pathString.replace('/', File.separatorChar);

                    if (path.endsWith(pathString)) {
                        String root = path.substring(0, path.length() - pathString.length());
                        File rootFile = new File(root);
                        if (!sourceRoots.contains(rootFile)) {
                            mostRecentRoot = rootFile.getPath();
                            sourceRoots.add(rootFile);
                        }
                    }
                }

                sourceUnits.add(file);
            }
        }
    }
}
