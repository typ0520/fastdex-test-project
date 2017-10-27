package com.android.build.gradle.internal;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.SUPPORT_LIB_ARTIFACT;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.Variant;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Project;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.w3c.dom.Document;

/**
 * An implementation of Lint's {@link Project} class wrapping a Gradle model (project or
 * library)
 */
public class LintGradleProject extends Project {
    protected AndroidVersion minSdkVersion;
    protected AndroidVersion targetSdkVersion;

    private LintGradleProject(
            @NonNull LintGradleClient client,
            @NonNull File dir,
            @NonNull File referenceDir,
            @Nullable File manifest) {
        super(client, dir, referenceDir);
        gradleProject = true;
        mergeManifests = true;
        directLibraries = Lists.newArrayList();
        if (manifest != null) {
            readManifest(manifest);
        }
    }

    private static void addJarsFromJavaLibrariesTransitively(
            @NonNull Collection<? extends JavaLibrary> libraries,
            @NonNull List<File> list,
            boolean skipProvided) {
        for (JavaLibrary library : libraries) {
            if (library.isSkipped()) {
                continue;
            }
            if (skipProvided && library.isProvided()) {
                continue;
            }

            File jar = library.getJarFile();
            if (!list.contains(jar)) {
                if (jar.exists()) {
                    list.add(jar);
                }
            }

            addJarsFromJavaLibrariesTransitively(library.getDependencies(), list, skipProvided);
        }
    }

    private static void addJarsFromAndroidLibrariesTransitively(
            @NonNull Collection<? extends AndroidLibrary> libraries,
            @NonNull List<File> list,
            boolean skipProvided) {
        for (AndroidLibrary library : libraries) {
            if (library.getProject() != null) {
                continue;
            }
            if (library.isSkipped()) {
                continue;
            }
            if (skipProvided && library.isProvided()) {
                continue;
            }

            File jar = library.getJarFile();
            if (!list.contains(jar)) {
                if (jar.exists()) {
                    list.add(jar);
                }
            }

            addJarsFromJavaLibrariesTransitively(library.getJavaDependencies(), list, skipProvided);
            addJarsFromAndroidLibrariesTransitively(library.getLibraryDependencies(), list, skipProvided);
        }
    }

    @Override
    protected void initialize() {
        // Deliberately not calling super; that code is for ADT compatibility
    }

    protected void readManifest(File manifest) {
        if (manifest.exists()) {
            try {
                String xml = Files.toString(manifest, Charsets.UTF_8);
                Document document = XmlUtils.parseDocumentSilently(xml, true);
                if (document != null) {
                    readManifest(document);
                }
            } catch (IOException e) {
                client.log(e, "Could not read manifest %1$s", manifest);
            }
        }
    }

    @Override
    public boolean isGradleProject() {
        return true;
    }

    protected static boolean dependsOn(@NonNull Dependencies dependencies,
            @NonNull String artifact) {
        for (AndroidLibrary library : dependencies.getLibraries()) {
            if (dependsOn(library, artifact)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean dependsOn(@NonNull AndroidLibrary library, @NonNull String artifact) {
        if (SUPPORT_LIB_ARTIFACT.equals(artifact)) {
            if (library.getJarFile().getName().startsWith("support-v4-")) {
                return true;
            }

        } else if (APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
            if (library.getName() != null && library.getName().startsWith(artifact)) {
                return true;
            }
        }

        for (AndroidLibrary dependency : library.getLibraryDependencies()) {
            if (dependsOn(dependency, artifact)) {
                return true;
            }
        }

        return false;
    }

    void addDirectLibrary(@NonNull Project project) {
        directLibraries.add(project);
    }

    // TODO: Rename: this isn't really an "App" project (it could be a library) too; it's a "project"
    // (e.g. not a remote artifact) - LocalGradleProject
    private static class AppGradleProject extends LintGradleProject {
        private final AndroidProject mProject;
        private final Variant mVariant;
        private List<SourceProvider> mProviders;
        private List<SourceProvider> mTestProviders;

        private AppGradleProject(
                @NonNull LintGradleClient client,
                @NonNull File dir,
                @NonNull File referenceDir,
                @NonNull AndroidProject project,
                @NonNull Variant variant) {
            //TODO FIXME: handle multi-apk
            super(client, dir, referenceDir,
                    variant.getMainArtifact().getOutputs().iterator().next().getGeneratedManifest());

            mProject = project;
            mVariant = variant;
        }

        @Override
        public boolean isLibrary() {
            return mProject.isLibrary();
        }

        @Override
        public AndroidProject getGradleProjectModel() {
            return mProject;
        }

        @Override
        public Variant getCurrentVariant() {
            return mVariant;
        }

        private List<SourceProvider> getSourceProviders() {
            if (mProviders == null) {
                mProviders = LintUtils.getSourceProviders(mProject, mVariant);
            }

            return mProviders;
        }

        private List<SourceProvider> getTestSourceProviders() {
            if (mTestProviders == null) {
                mTestProviders = LintUtils.getTestSourceProviders(mProject, mVariant);
            }

            return mTestProviders;
        }

        @NonNull
        @Override
        public List<File> getManifestFiles() {
            if (manifestFiles == null) {
                manifestFiles = Lists.newArrayList();
                for (SourceProvider provider : getSourceProviders()) {
                    File manifestFile = provider.getManifestFile();
                    if (manifestFile.exists()) { // model returns path whether or not it exists
                        manifestFiles.add(manifestFile);
                    }
                }
            }

            return manifestFiles;
        }

        @NonNull
        @Override
        public List<File> getProguardFiles() {
            if (proguardFiles == null) {
                ProductFlavor flavor = mProject.getDefaultConfig().getProductFlavor();
                proguardFiles = flavor.getProguardFiles().stream()
                        .filter(File::exists)
                        .collect(Collectors.toList());
                try {
                    proguardFiles.addAll(
                            flavor.getConsumerProguardFiles().stream()
                                    .filter(File::exists)
                                    .collect(Collectors.toList()));
                } catch (Throwable t) {
                    // On some models, this threw
                    //   org.gradle.tooling.model.UnsupportedMethodException:
                    //    Unsupported method: BaseConfig.getConsumerProguardFiles().
                    // Playing it safe for a while.
                }
            }

            return proguardFiles;
        }

        @NonNull
        @Override
        public List<File> getResourceFolders() {
            if (resourceFolders == null) {
                resourceFolders = Lists.newArrayList();
                for (SourceProvider provider : getSourceProviders()) {
                    Collection<File> resDirs = provider.getResDirectories();
                    // model returns path whether or not it exists
                    resourceFolders.addAll(resDirs.stream()
                            .filter(File::exists)
                            .collect(Collectors.toList()));
                }

                resourceFolders.addAll(
                        mVariant.getMainArtifact().getGeneratedResourceFolders().stream()
                                .filter(File::exists)
                                .collect(Collectors.toList()));
            }

            return resourceFolders;
        }

        @NonNull
        @Override
        public List<File> getAssetFolders() {
            if (assetFolders == null) {
                assetFolders = Lists.newArrayList();
                for (SourceProvider provider : getSourceProviders()) {
                    Collection<File> dirs = provider.getAssetsDirectories();
                    // model returns path whether or not it exists
                    assetFolders.addAll(dirs.stream()
                            .filter(File::exists)
                            .collect(Collectors.toList()));
                }
            }

            return assetFolders;
        }

        @NonNull
        @Override
        public List<File> getJavaSourceFolders() {
            if (javaSourceFolders == null) {
                javaSourceFolders = Lists.newArrayList();
                for (SourceProvider provider : getSourceProviders()) {
                    Collection<File> srcDirs = provider.getJavaDirectories();
                    // model returns path whether or not it exists
                    javaSourceFolders.addAll(srcDirs.stream()
                            .filter(File::exists)
                            .collect(Collectors.toList()));
                }
            }

            return javaSourceFolders;
        }

        @NonNull
        @Override
        public List<File> getGeneratedSourceFolders() {
            if (generatedSourceFolders == null) {
                AndroidArtifact artifact = mVariant.getMainArtifact();
                generatedSourceFolders = artifact.getGeneratedSourceFolders().stream()
                                .filter(File::exists)
                                .collect(Collectors.toList());
            }

            return generatedSourceFolders;
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders() {
            if (testSourceFolders == null) {
                testSourceFolders = Lists.newArrayList();
                for (SourceProvider provider : getTestSourceProviders()) {
                    // model returns path whether or not it exists
                    testSourceFolders.addAll(provider.getJavaDirectories().stream()
                            .filter(File::exists)
                            .collect(Collectors.toList()));
                }
            }

            return testSourceFolders;
        }

        @NonNull
        @Override
        public List<File> getJavaClassFolders() {
            if (javaClassFolders == null) {
                javaClassFolders = new ArrayList<>(1);
                File outputClassFolder = mVariant.getMainArtifact().getClassesFolder();
                if (outputClassFolder.exists()) {
                    javaClassFolders.add(outputClassFolder);
                } else if (isLibrary()) {
                    // For libraries we build the release variant instead
                    for (Variant variant : mProject.getVariants()) {
                        if (variant != mVariant) {
                            outputClassFolder = variant.getMainArtifact().getClassesFolder();
                            if (outputClassFolder.exists()) {
                                javaClassFolders.add(outputClassFolder);
                                break;
                            }
                        }
                    }
                }
            }

            return javaClassFolders;
        }

        @NonNull
        @Override
        public List<File> getJavaLibraries(boolean includeProvided) {
            if (includeProvided) {
                if (javaLibraries == null) {
                    Dependencies dependencies = mVariant.getMainArtifact().getDependencies();
                    Collection<JavaLibrary> libs = dependencies.getJavaLibraries();
                    javaLibraries = Lists.newArrayListWithExpectedSize(libs.size());
                    for (JavaLibrary lib : libs) {
                        File jar = lib.getJarFile();
                        if (jar.exists()) {
                            javaLibraries.add(jar);
                        }
                    }
                }
                return javaLibraries;
            } else {
                // Skip provided libraries?
                if (nonProvidedJavaLibraries == null) {
                    Dependencies dependencies = mVariant.getMainArtifact().getDependencies();
                    Collection<JavaLibrary> libs = dependencies.getJavaLibraries();
                    nonProvidedJavaLibraries = Lists.newArrayListWithExpectedSize(libs.size());
                    for (JavaLibrary lib : libs) {
                        File jar = lib.getJarFile();
                        if (jar.exists()) {
                            if (lib.isProvided()) {
                                continue;
                            }

                            nonProvidedJavaLibraries.add(jar);
                        }
                    }
                }
                return nonProvidedJavaLibraries;
            }
        }

        @NonNull
        @Override
        public List<File> getTestLibraries() {
            if (testLibraries == null) {
                testLibraries = Lists.newArrayListWithExpectedSize(6);
                for (AndroidArtifact artifact : mVariant.getExtraAndroidArtifacts()) {
                    if (AndroidProject.ARTIFACT_ANDROID_TEST.equals(artifact.getName())
                            || AndroidProject.ARTIFACT_UNIT_TEST.equals(artifact.getName())) {
                        Dependencies dependencies = artifact.getDependencies();

                        addJarsFromJavaLibrariesTransitively(dependencies.getJavaLibraries(),
                                testLibraries, false);
                        // Note that we don't include these for getJavaLibraries, but we need to
                        // for tests since we don't keep them otherwise
                        addJarsFromAndroidLibrariesTransitively(dependencies.getLibraries(),
                                testLibraries, false);
                    }
                }
            }
            return testLibraries;
        }

        @Nullable
        @Override
        public String getPackage() {
            // For now, lint only needs the manifest package; not the potentially variant specific
            // package. As part of the Gradle work on the Lint API we should make two separate
            // package lookup methods -- one for the manifest package, one for the build package
            if (pkg == null) { // only used as a fallback in case manifest somehow is null
                String packageName = mProject.getDefaultConfig().getProductFlavor()
                        .getApplicationId();
                if (packageName != null) {
                    return packageName;
                }
            }

            return pkg; // from manifest
        }

        @Override
        @NonNull
        public AndroidVersion getMinSdkVersion() {
            if (minSdkVersion == null) {
                ApiVersion minSdk = mVariant.getMergedFlavor().getMinSdkVersion();
                if (minSdk == null) {
                    ProductFlavor flavor = mProject.getDefaultConfig().getProductFlavor();
                    minSdk = flavor.getMinSdkVersion();
                }
                if (minSdk != null) {
                    minSdkVersion = LintUtils.convertVersion(minSdk, client.getTargets());
                } else {
                    minSdkVersion = super.getMinSdkVersion(); // from manifest
                }
            }

            return minSdkVersion;
        }

        @Override
        @NonNull
        public AndroidVersion getTargetSdkVersion() {
            if (targetSdkVersion == null) {
                ApiVersion targetSdk = mVariant.getMergedFlavor().getTargetSdkVersion();
                if (targetSdk == null) {
                    ProductFlavor flavor = mProject.getDefaultConfig().getProductFlavor();
                    targetSdk = flavor.getTargetSdkVersion();
                }
                if (targetSdk != null) {
                    targetSdkVersion = LintUtils.convertVersion(targetSdk, client.getTargets());
                } else {
                    targetSdkVersion = super.getTargetSdkVersion(); // from manifest
                }
            }

            return targetSdkVersion;
        }

        @Override
        public int getBuildSdk() {
            String compileTarget = mProject.getCompileTarget();
            AndroidVersion version = AndroidTargetHash.getPlatformVersion(compileTarget);
            if (version != null) {
                return version.getFeatureLevel();
            }

            return super.getBuildSdk();
        }

        @Nullable
        @Override
        public String getBuildTargetHash() {
            return mProject.getCompileTarget();
        }

        @Nullable
        @Override
        public Boolean dependsOn(@NonNull String artifact) {
            if (SUPPORT_LIB_ARTIFACT.equals(artifact)) {
                if (supportLib == null) {
                    Dependencies dependencies = mVariant.getMainArtifact().getDependencies();
                    supportLib = dependsOn(dependencies, artifact);
                }
                return supportLib;
            } else if (APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
                if (appCompat == null) {
                    Dependencies dependencies = mVariant.getMainArtifact().getDependencies();
                    appCompat = dependsOn(dependencies, artifact);
                }
                return appCompat;
            } else {
                return super.dependsOn(artifact);
            }
        }
    }

    // FIXME: Remove this once instant apps no longer output an AndroidProject.
    private static class InstantAppGradleProject extends LintGradleProject {
        private InstantAppGradleProject(
                @NonNull LintGradleClient client, @NonNull File dir, @NonNull File referenceDir) {
            super(client, dir, referenceDir, null);
        }
    }

    private static class LibraryProject extends LintGradleProject {
        private final AndroidLibrary mLibrary;

        private LibraryProject(
                @NonNull LintGradleClient client,
                @NonNull File dir,
                @NonNull File referenceDir,
                @NonNull AndroidLibrary library) {
            super(client, dir, referenceDir, library.getManifest());
            mLibrary = library;

            // TODO: Make sure we don't use this project for any source library projects!
            reportIssues = false;
        }

        @Override
        public boolean isLibrary() {
            return true;
        }

        @Override
        public AndroidLibrary getGradleLibraryModel() {
            return mLibrary;
        }

        @Override
        public Variant getCurrentVariant() {
            return null;
        }

        @NonNull
        @Override
        public List<File> getManifestFiles() {
            if (manifestFiles == null) {
                File manifest = mLibrary.getManifest();
                if (manifest.exists()) {
                    manifestFiles = Collections.singletonList(manifest);
                } else {
                    manifestFiles = Collections.emptyList();
                }
            }

            return manifestFiles;
        }

        @NonNull
        @Override
        public List<File> getProguardFiles() {
            if (proguardFiles == null) {
                File proguardRules = mLibrary.getProguardRules();
                if (proguardRules.exists()) {
                    proguardFiles = Collections.singletonList(proguardRules);
                } else {
                    proguardFiles = Collections.emptyList();
                }
            }

            return proguardFiles;
        }

        @NonNull
        @Override
        public List<File> getResourceFolders() {
            if (resourceFolders == null) {
                File folder = mLibrary.getResFolder();
                if (folder.exists()) {
                    resourceFolders = Collections.singletonList(folder);
                } else {
                    resourceFolders = Collections.emptyList();
                }
            }

            return resourceFolders;
        }

        @NonNull
        @Override
        public List<File> getAssetFolders() {
            if (assetFolders == null) {
                File folder = mLibrary.getAssetsFolder();
                if (folder.exists()) {
                    assetFolders = Collections.singletonList(folder);
                } else {
                    assetFolders = Collections.emptyList();
                }
            }

            return assetFolders;
        }

        @NonNull
        @Override
        public List<File> getJavaSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getGeneratedSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaClassFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaLibraries(boolean includeProvided) {
            if (!includeProvided && mLibrary.isProvided()) {
                return Collections.emptyList();
            }

            if (javaLibraries == null) {
                javaLibraries =
                        Stream.concat(
                                Stream.of(mLibrary.getJarFile()),
                                mLibrary.getLocalJars().stream())
                        .filter(File::exists)
                        .collect(Collectors.toList());
            }

            return javaLibraries;
        }

        @Nullable
        @Override
        public Boolean dependsOn(@NonNull String artifact) {
            if (SUPPORT_LIB_ARTIFACT.equals(artifact)) {
                if (supportLib == null) {
                    supportLib = dependsOn(mLibrary, artifact);
                }
                return supportLib;
            } else if (APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
                if (appCompat == null) {
                    appCompat = dependsOn(mLibrary, artifact);
                }
                return appCompat;
            } else {
                return super.dependsOn(artifact);
            }
        }
    }

    /**
     * Class which creates a lint project hierarchy based on a corresponding
     * Gradle project hierarchy, looking up project dependencies by name, creating
     * wrapper projects for Java libraries, looking up tooling models for each project,
     * etc.
     */
    static class ProjectSearch {
        public final Map<AndroidProject,Project> appProjects = Maps.newHashMap();
        public final Map<AndroidLibrary,Project> libraryProjects = Maps.newHashMap();
        public final Map<MavenCoordinates,Project> libraryProjectsByCoordinate = Maps.newHashMap();
        public final Map<String,Project> namedProjects = Maps.newHashMap();
        public final Map<JavaLibrary,Project> javaLibraryProjects = Maps.newHashMap();
        public final Map<MavenCoordinates,Project> javaLibraryProjectsByCoordinate = Maps.newHashMap();
        public final Map<org.gradle.api.Project,AndroidProject> gradleProjects = Maps.newHashMap();
        private final Set<Object> mSeen = Sets.newHashSet();

        public ProjectSearch() {
        }

        @Nullable
        private static AndroidProject createAndroidProject(
                @NonNull org.gradle.api.Project gradleProject) {
            PluginContainer pluginContainer = gradleProject.getPlugins();
            for (Plugin p : pluginContainer) {
                if (p instanceof ToolingRegistryProvider) {
                    ToolingModelBuilderRegistry registry;
                    registry = ((ToolingRegistryProvider) p).getModelBuilderRegistry();
                    String modelName = AndroidProject.class.getName();
                    ToolingModelBuilder builder = registry.getBuilder(modelName);
                    assert builder.canBuild(modelName) : modelName;

                    // setup the level 3 sync.
                    final ExtraPropertiesExtension ext =
                            gradleProject.getExtensions().getExtraProperties();
                    // Ensure that projects are constructed serially since otherwise
                    // it's possible for a race condition on the below property
                    // to trigger occasional NPE's like the one in b.android.com/38117575
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (ext) {
                        ext.set(
                                AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED,
                                Integer.toString(
                                        AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD));
                        ext.set(AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD, true);

                        try {
                            return (AndroidProject) builder.buildAll(modelName, gradleProject);
                        } finally {
                            ext.set(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED, null);
                            ext.set(AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD, null);
                        }
                    }
                }
            }

            return null;
        }

        @Nullable
        private AndroidProject getAndroidProject(@NonNull org.gradle.api.Project gradleProject) {
            AndroidProject androidProject = gradleProjects.get(gradleProject);
            if (androidProject == null) {
                androidProject = createAndroidProject(gradleProject);
                if (androidProject != null) {
                    gradleProjects.put(gradleProject, androidProject);
                }
            }
            return androidProject;
        }

        public Project getProject(
                @NonNull LintGradleClient lintClient,
                @NonNull org.gradle.api.Project gradleProject,
                @NonNull String variantName) {
            AndroidProject androidProject = getAndroidProject(gradleProject);
            if (androidProject != null) {
                Collection<Variant> variants = androidProject.getVariants();
                for (Variant variant : variants) {
                    if (variantName.equals(variant.getName())) {
                        return getProject(lintClient, androidProject, variant, gradleProject);
                    }
                }

                // Just use the default variant.
                // TODO: Use DSL to designate the default variants for this (not
                // yet available, but planned.)
                if (!variants.isEmpty()) {
                    Variant defaultVariant = variants.iterator().next();
                    return getProject(lintClient, androidProject, defaultVariant, gradleProject);
                }

                // This shouldn't happen; we didn't get an AndroidProject for an expected
                // variant name
                assert false : variantName;
            }

            // Make plain vanilla project; this is what happens for Java projects (which
            // don't have a Gradle model)

            JavaPluginConvention convention = gradleProject.getConvention()
                    .findPlugin(JavaPluginConvention.class);
            if (convention == null) {
                return null;
            }

            // Language level: Currently not needed. The way to get it is via
            //   convention.getSourceCompatibility()

            // Sources
            SourceSetContainer sourceSets = convention.getSourceSets();
            if (sourceSets != null) {
                final List<File> sources = Lists.newArrayList();
                final List<File> classes = Lists.newArrayList();
                final List<File> libs = Lists.newArrayList();
                final List<File> tests = Lists.newArrayList();
                for (SourceSet sourceSet : sourceSets) {
                    if (sourceSet.getName().equals(SourceSet.TEST_SOURCE_SET_NAME)) {
                        // We don't model the full test source set yet (e.g. its dependencies),
                        // only its source files
                        SourceDirectorySet javaSrc = sourceSet.getJava();
                        if (javaSrc != null) {
                            tests.addAll(javaSrc.getSrcDirs());
                        }
                        continue;
                    }

                    SourceDirectorySet javaSrc = sourceSet.getJava();
                    if (javaSrc != null) {
                        // There are also resource directories, in case we want to
                        // model those here eventually
                        sources.addAll(javaSrc.getSrcDirs());
                    }
                    SourceSetOutput output = sourceSet.getOutput();
                    if (output != null) {
                        classes.add(output.getClassesDir());
                    }

                    libs.addAll(sourceSet.getCompileClasspath().getFiles());

                    // TODO: Boot classpath? We don't have access to that here, so for
                    // now EcjParser just falls back to the running Gradle JVM and looks
                    // up its class path.
                }

                File projectDir = gradleProject.getProjectDir();
                final List<Project> dependencies = Lists.newArrayList();
                Project project = new Project(lintClient, projectDir, projectDir) {
                    @Override
                    protected void initialize() {
                        // Deliberately not calling super; that code is for ADT compatibility
                        gradleProject = true;
                        mergeManifests = true;
                        directLibraries = dependencies;
                        javaSourceFolders = sources;
                        javaClassFolders = classes;
                        javaLibraries = libs;
                        testSourceFolders = tests;
                    }

                    @Override
                    public boolean isGradleProject() {
                        return true;
                    }

                    @Override
                    public boolean isAndroidProject() {
                        return false;
                    }

                    @Nullable
                    @Override
                    public IAndroidTarget getBuildTarget() {
                        return null;
                    }
                };

                // Dependencies
                ConfigurationContainer configurations = gradleProject.getConfigurations();
                Configuration compileConfiguration = configurations.getByName("compileClasspath");
                for (Dependency dependency : compileConfiguration.getAllDependencies()) {
                    if (dependency instanceof ProjectDependency) {
                        org.gradle.api.Project p =
                                ((ProjectDependency) dependency).getDependencyProject();
                        if (p != null) {
                            Project lintProject = getProject(lintClient, p.getPath(), p,
                                    variantName);
                            if (lintProject != null) {
                                dependencies.add(lintProject);
                            }
                        }
                    } else if (dependency instanceof ExternalDependency) {
                        String group = dependency.getGroup();
                        String name = dependency.getName();
                        String version = dependency.getVersion();
                        if (name == null || group == null || version == null) {
                            // This will be the case for example if you use something like
                            //    repositories { flatDir { dirs 'myjars' } }
                            //    dependencies { compile name: 'guava-18.0' }
                            continue;
                        }
                        MavenCoordinatesImpl coordinates = new MavenCoordinatesImpl(group,
                                name, version);
                        Project javaLib = javaLibraryProjectsByCoordinate.get(coordinates);
                        //noinspection StatementWithEmptyBody
                        if (javaLib != null) {
                            dependencies.add(javaLib);
                        } else {
                            // Else: Create wrapper here. Unfortunately, we don't have a
                            // pointer to the actual .jar file to add (getArtifacts()
                            // typically returns an empty set), so we can't create
                            // a real artifact (and creating a fake one and placing it here
                            // is dangerous; it would mean putting one into the
                            // map that would prevent a real definition from being inserted.
                        }
                    } else if (dependency instanceof FileCollectionDependency) {
                        Set<File> files = ((FileCollectionDependency) dependency).resolve();
                        if (files != null) {
                            libs.addAll(files);
                        }
                    }
                }

                return project;
            }

            return null;
        }

        public Project getProject(
                @NonNull LintGradleClient client,
                @NonNull AndroidProject project,
                @NonNull Variant variant,
                @NonNull org.gradle.api.Project gradleProject) {
            Project cached = appProjects.get(project);
            if (cached != null) {
                return cached;
            }
            mSeen.add(project);
            File dir = gradleProject.getProjectDir();
            LintGradleProject lintProject;
            if (project.getProjectType() == AndroidProject.PROJECT_TYPE_INSTANTAPP) {
                lintProject = new InstantAppGradleProject(client, dir, dir);
            } else {
                lintProject = new AppGradleProject(client, dir, dir, project, variant);
            }
            appProjects.put(project, lintProject);

            // DELIBERATELY calling getDependencies here (and Dependencies#getProjects() below) :
            // the new hierarchical model is not working yet.
            //noinspection deprecation
            Dependencies dependencies = variant.getMainArtifact().getDependencies();
            for (AndroidLibrary library : dependencies.getLibraries()) {
                if (library.getProject() != null) {
                    // Handled below
                    continue;
                }
                lintProject.addDirectLibrary(getLibrary(client, library, gradleProject, variant));
            }

            // Dependencies.getProjects() no longer passes project names in all cases, so
            // look up from Gradle project directly
            List<String> processedProjects = null;
            ConfigurationContainer configurations = gradleProject.getConfigurations();
            Configuration compileConfiguration = configurations
                    .getByName(variant.getName() + "CompileClasspath");
            for (Dependency dependency : compileConfiguration.getAllDependencies()) {
                if (dependency instanceof ProjectDependency) {
                    org.gradle.api.Project p =
                            ((ProjectDependency) dependency).getDependencyProject();
                    if (p != null) {
                        // Libraries don't have to use the same variant name as the
                        // consuming app. In fact they're typically not: libraries generally
                        // use the release variant. We can look up the variant name
                        // in AndroidBundle#getProjectVariant, though it's always null
                        // at the moment. So as a fallback, search for existing
                        // code.
                        Project depProject = getProject(client, p, variant.getName());
                        if (depProject != null) {
                            if (processedProjects == null) {
                                processedProjects = Lists.newArrayList();
                            }
                            processedProjects.add(p.getPath());
                            lintProject.addDirectLibrary(depProject);
                        }
                    }
                }
            }

            for (JavaLibrary library : dependencies.getJavaLibraries()) {
                String projectName = library.getProject();
                if (projectName != null) {
                    if (processedProjects != null && processedProjects.contains(projectName)) {
                        continue;
                    }
                    Project libLintProject = getProject(client, projectName, gradleProject,
                            variant.getName());
                    if (libLintProject != null) {
                        lintProject.addDirectLibrary(libLintProject);
                        continue;
                    }
                }
                lintProject.addDirectLibrary(getLibrary(client, library));
            }

            return lintProject;
        }

        @Nullable
        private Project getProject(
                @NonNull LintGradleClient client,
                @NonNull String path,
                @NonNull org.gradle.api.Project gradleProject,
                @NonNull String variantName) {
            Project cached = namedProjects.get(path);
            if (cached != null) {
                // TODO: Are names unique across siblings?
                return cached;
            }
            org.gradle.api.Project namedProject = gradleProject.findProject(path);
            if (namedProject != null) {
                Project project = getProject(client, namedProject, variantName);
                if (project != null) {
                    namedProjects.put(path, project);
                    return project;
                }
            }

            return null;
        }

        @NonNull
        private Project getLibrary(
                @NonNull LintGradleClient client,
                @NonNull AndroidLibrary library,
                @NonNull org.gradle.api.Project gradleProject,
                @NonNull Variant variant) {
            Project cached = libraryProjects.get(library);
            if (cached != null) {
                return cached;
            }

            MavenCoordinates coordinates = library.getResolvedCoordinates();
            cached = libraryProjectsByCoordinate.get(coordinates);
            if (cached != null) {
                return cached;
            }

            if (library.getProject() != null) {
                Project project = getProject(client, library.getProject(), gradleProject,
                        variant.getName());
                if (project != null) {
                    libraryProjects.put(library, project);
                    return project;
                }
            }

            mSeen.add(library);
            File dir = library.getFolder();
            LibraryProject project = new LibraryProject(client, dir, dir, library);
            libraryProjects.put(library, project);
            libraryProjectsByCoordinate.put(coordinates, project);

            for (AndroidLibrary dependent : library.getLibraryDependencies()) {
                project.addDirectLibrary(getLibrary(client, dependent, gradleProject, variant));
            }

            return project;
        }

        @NonNull
        private Project getLibrary(
                @NonNull LintGradleClient client,
                @NonNull JavaLibrary library) {
            Project cached = javaLibraryProjects.get(library);
            if (cached != null) {
                return cached;
            }

            MavenCoordinates coordinates = library.getResolvedCoordinates();
            cached = javaLibraryProjectsByCoordinate.get(coordinates);
            if (cached != null) {
                return cached;
            }

            mSeen.add(library);
            File dir = library.getJarFile();
            JavaLibraryProject project = new JavaLibraryProject(client, dir, dir, library);
            javaLibraryProjects.put(library, project);
            javaLibraryProjectsByCoordinate.put(coordinates, project);

            for (JavaLibrary dependent : library.getDependencies()) {
                project.addDirectLibrary(getLibrary(client, dependent));
            }

            return project;
        }
    }

    private static class JavaLibraryProject extends LintGradleProject {
        private final JavaLibrary mLibrary;

        private JavaLibraryProject(
                @NonNull LintGradleClient client,
                @NonNull File dir,
                @NonNull File referenceDir,
                @NonNull JavaLibrary library) {
            super(client, dir, referenceDir, null);
            mLibrary = library;
            reportIssues = false;
        }

        @Override
        public boolean isLibrary() {
            return true;
        }

        @NonNull
        @Override
        public List<File> getManifestFiles() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getProguardFiles() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getResourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getAssetFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getGeneratedSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaClassFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaLibraries(boolean includeProvided) {
            if (!includeProvided && mLibrary.isProvided()) {
                return Collections.emptyList();
            }

            if (javaLibraries == null) {
                javaLibraries = Lists.newArrayList();
                javaLibraries.add(mLibrary.getJarFile());
            }

            return javaLibraries;
        }
    }
}
