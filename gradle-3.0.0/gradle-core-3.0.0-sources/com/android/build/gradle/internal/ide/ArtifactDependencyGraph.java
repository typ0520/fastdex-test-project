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

package com.android.build.gradle.internal.ide;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.EXT_AAR;
import static com.android.SdkConstants.EXT_JAR;
import static com.android.SdkConstants.FD_AAR_LIBS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.build.gradle.internal.ide.ArtifactDependencyGraph.DependencyType.ANDROID;
import static com.android.build.gradle.internal.ide.ArtifactDependencyGraph.DependencyType.JAVA;
import static com.android.build.gradle.internal.ide.ModelBuilder.EMPTY_DEPENDENCIES_IMPL;
import static com.android.build.gradle.internal.ide.ModelBuilder.EMPTY_DEPENDENCY_GRAPH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact;
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs;
import com.android.build.gradle.internal.dependency.VariantAttr;
import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl;
import com.android.build.gradle.internal.ide.level2.FullDependencyGraphsImpl;
import com.android.build.gradle.internal.ide.level2.GraphItemImpl;
import com.android.build.gradle.internal.ide.level2.JavaLibraryImpl;
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl;
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.caching.CreatingCache;
import com.android.utils.FileUtils;
import com.android.utils.ImmutableCollectors;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.component.Artifact;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

/** For creating dependency graph based on {@link ResolvedArtifactResult}. */
public class ArtifactDependencyGraph {

    private static final String LOCAL_AAR_GROUPID = "__local_aars__";

    private static final CreatingCache<HashableResolvedArtifactResult, MavenCoordinates>
            sMavenCoordinatesCache =
                    new CreatingCache<>(ArtifactDependencyGraph::computeMavenCoordinates);

    private static final CreatingCache<HashableResolvedArtifactResult, Library> sLibraryCache =
            new CreatingCache<>(ArtifactDependencyGraph::instantiateLibrary);
    private static final Map<String, Library> sGlobalLibrary = Maps.newHashMap();

    private DependencyFailureHandler dependencyFailureHandler = new DependencyFailureHandler();

    public static void clearCaches() {
        sMavenCoordinatesCache.clear();
        sLibraryCache.clear();
    }

    @NonNull
    private static Library instantiateLibrary(@NonNull HashableResolvedArtifactResult artifact) {
        Library library;
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        String address = ArtifactDependencyGraph.computeAddress(artifact);

        if (!(id instanceof ProjectComponentIdentifier) || artifact.isWrappedModule()) {
            if (artifact.getDependencyType() == ANDROID) {
                File explodedFolder = artifact.getFile();
                library =
                        new AndroidLibraryImpl(
                                address,
                                artifact.bundleResult != null
                                        ? artifact.bundleResult.getFile()
                                        : explodedFolder, // fallback so that the value is non-null
                                explodedFolder,
                                findLocalJarsAsStrings(explodedFolder));
            } else {
                library = new JavaLibraryImpl(address, artifact.getFile());
            }
        } else {
            library =
                    new ModuleLibraryImpl(
                            address,
                            ((ProjectComponentIdentifier) id).getProjectPath(),
                            getVariant(artifact));
        }

        synchronized (sGlobalLibrary) {
            sGlobalLibrary.put(library.getArtifactAddress(), library);
        }

        return library;
    }

    public static Map<String, Library> getGlobalLibMap() {
        return ImmutableMap.copyOf(sGlobalLibrary);
    }

    @Nullable
    public static String getVariant(@NonNull ResolvedArtifactResult artifact) {
        VariantAttr variantAttr =
                artifact.getVariant().getAttributes().getAttribute(VariantAttr.ATTRIBUTE);
        return variantAttr == null ? null : variantAttr.getName();
    }

    @NonNull
    public static String computeAddress(@NonNull HashableResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        if (id instanceof ProjectComponentIdentifier) {
            String variant = getVariant(artifact);
            if (variant == null) {
                return ((ProjectComponentIdentifier) id).getProjectPath().intern();
            } else {
                return (((ProjectComponentIdentifier) id).getProjectPath() + "::" + variant)
                        .intern();
            }
        } else if (id instanceof ModuleComponentIdentifier || id instanceof OpaqueComponentArtifactIdentifier) {
            MavenCoordinates coordinates = sMavenCoordinatesCache.get(artifact);
            checkNotNull(coordinates);
            return coordinates.toString().intern();
        } else {
            throw new RuntimeException(
                    "Don't know how to handle ComponentIdentifier '"
                            + id.getDisplayName()
                            + "'of type "
                            + id.getClass());
        }
    }

    @NonNull
    private static MavenCoordinates computeMavenCoordinates(
            @NonNull ResolvedArtifactResult artifact) {
        // instance should be a hashable.
        HashableResolvedArtifactResult hashableResult = (HashableResolvedArtifactResult) artifact;

        ComponentIdentifier id = artifact.getId().getComponentIdentifier();

        final File artifactFile = artifact.getFile();
        final String fileName = artifactFile.getName();
        String extension = hashableResult.getDependencyType().getExtension();
        if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) id;
            final String module = moduleComponentId.getModule();
            final String version = moduleComponentId.getVersion();
            String classifier = null;

            if (!artifact.getFile().isDirectory()) {
                // attempts to compute classifier based on the filename.
                String pattern = "^" + module + "-" + version + "-(.+)\\." + extension + "$";

                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(fileName);
                if (m.matches()) {
                    classifier = m.group(1);
                }
            }

            return new MavenCoordinatesImpl(
                    moduleComponentId.getGroup(), module, version, extension, classifier);
        } else if (id instanceof ProjectComponentIdentifier) {
            return new MavenCoordinatesImpl(
                    "artifacts", ((ProjectComponentIdentifier) id).getProjectPath(), "unspecified");
        } else if (id instanceof OpaqueComponentArtifactIdentifier) {
            // We have a file based dependency
            if (hashableResult.getDependencyType() == JAVA) {
                return getMavenCoordForLocalFile(artifactFile);
            } else {
                // local aar?
                assert artifactFile.isDirectory();
                return getMavenCoordForLocalFile(artifactFile);
            }
        }

        throw new RuntimeException(
                "Don't know how to compute maven coordinate for artifact '"
                        + artifact.getId().getDisplayName()
                        + "' with component identifier of type '"
                        + id.getClass()
                        + "'.");
    }

    @NonNull
    public static MavenCoordinatesImpl getMavenCoordForLocalFile(File artifactFile) {
        return new MavenCoordinatesImpl(LOCAL_AAR_GROUPID, artifactFile.getPath(), "unspecified");
    }

    /**
     * Returns a set of HashableResolvedArtifactResult where the {@link
     * HashableResolvedArtifactResult#getDependencyType()} and {@link
     * HashableResolvedArtifactResult#isWrappedModule()} fields have been setup properly.
     */
    public static Set<HashableResolvedArtifactResult> getAllArtifacts(
            @NonNull VariantScope variantScope,
            @NonNull AndroidArtifacts.ConsumedConfigType consumedConfigType,
            @Nullable DependencyFailureHandler dependencyFailureHandler) {
        // FIXME change the way we compare dependencies b/64387392

        // we need to figure out the following:
        // - Is it an external dependency or a sub-project?
        // - Is it an android or a java dependency

        // Querying for JAR type gives us all the dependencies we care about, and we can use this
        // to differentiate external vs sub-projects (to a certain degree).
        ArtifactCollection allArtifactList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.JAR);

        // Then we can query for MANIFEST that will give us only the Android project so that we
        // can detect JAVA vs ANDROID.
        ArtifactCollection manifestList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.MANIFEST);

        // We still need to understand wrapped jars and aars. The former is difficult (TBD), but
        // the latter can be done by querying for EXPLODED_AAR. If a sub-project is in this list,
        // then we need to override the type to be external, rather than sub-project.
        // This is why we query for Scope.ALL
        // But we also simply need the exploded AARs for external Android dependencies so that
        // Studio can access the content.
        ArtifactCollection explodedAarList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.EXPLODED_AAR);

        // We also need the actual AARs so that we can get the artifact location and find the source
        // location from it.
        ArtifactCollection aarList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.EXTERNAL,
                        AndroidArtifacts.ArtifactType.AAR);


        // collect dependency resolution failures
        if (dependencyFailureHandler != null) {
            // compute the name of the configuration
            dependencyFailureHandler.addErrors(
                    variantScope.getGlobalScope().getProject().getPath()
                            + "@"
                            + variantScope.getFullVariantName()
                            + "/"
                            + consumedConfigType.getName(),
                    allArtifactList.getFailures());
        }

        // build a list of wrapped AAR, and a map of all the exploded-aar artifacts
        final Set<ComponentIdentifier> wrapperModules = new HashSet<>();
        final Set<ResolvedArtifactResult> explodedAarArtifacts = explodedAarList.getArtifacts();
        final Map<ComponentIdentifier, ResolvedArtifactResult> explodedAarResults =
                Maps.newHashMapWithExpectedSize(explodedAarArtifacts.size());
        for (ResolvedArtifactResult result : explodedAarArtifacts) {
            final ComponentIdentifier componentIdentifier = result.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ProjectComponentIdentifier) {
                wrapperModules.add(componentIdentifier);
            }
            explodedAarResults.put(componentIdentifier, result);
        }

        final Set<ResolvedArtifactResult> aarArtifacts = aarList.getArtifacts();
        final Map<ComponentIdentifier, ResolvedArtifactResult> aarResults =
                Maps.newHashMapWithExpectedSize(aarArtifacts.size());
        for (ResolvedArtifactResult result : aarArtifacts) {
            aarResults.put(result.getId().getComponentIdentifier(), result);
        }

        // build a list of android dependencies based on them publishing a MANIFEST element
        final Set<ResolvedArtifactResult> manifestArtifacts = manifestList.getArtifacts();
        final Set<ComponentIdentifier> manifestIds =
                Sets.newHashSetWithExpectedSize(manifestArtifacts.size());
        for (ResolvedArtifactResult result : manifestArtifacts) {
            manifestIds.add(result.getId().getComponentIdentifier());
        }

        // build the final list, using the main list augmented with data from the previous lists.
        final Set<ResolvedArtifactResult> allArtifacts = allArtifactList.getArtifacts();

        // use a linked hash set to keep the artifact order.
        final Set<HashableResolvedArtifactResult> artifacts =
                Sets.newLinkedHashSetWithExpectedSize(allArtifacts.size());

        for (ResolvedArtifactResult artifact : allArtifacts) {
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier();

            // check if this is a wrapped module
            boolean isWrappedModule = wrapperModules.contains(componentIdentifier);

            // check if this is an android external module. In this case, we want to use the exploded
            // aar as the artifact we depend on rather than just the JAR, so we swap out the
            // ResolvedArtifactResult.
            DependencyType dependencyType = JAVA;
            // optional result that will point to the artifact (AAR) when the current result
            // is the exploded AAR.
            ResolvedArtifactResult aarResult = null;
            if (manifestIds.contains(componentIdentifier)) {
                dependencyType = ANDROID;
                // if it's an android dependency, we swap out the manifest result for the exploded
                // AAR result.
                // If the exploded AAR is null then it's a sub-project and we can keep the manifest
                // as the Library we'll create will be a ModuleLibrary which doesn't care about
                // the artifact file anyway.
                ResolvedArtifactResult explodedAar = explodedAarResults.get(componentIdentifier);
                if (explodedAar != null) {
                    artifact = explodedAar;
                }

                // and we need the AAR itself (if it exists)
                aarResult = aarResults.get(componentIdentifier);
            }

            artifacts.add(
                    new HashableResolvedArtifactResult(
                            artifact, dependencyType, isWrappedModule, aarResult));
        }

        return artifacts;
    }

    @NonNull
    private static ArtifactCollection computeArtifactList(
            @NonNull VariantScope variantScope,
            @NonNull AndroidArtifacts.ConsumedConfigType consumedConfigType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType type) {
        ArtifactCollection artifacts =
                variantScope.getArtifactCollection(consumedConfigType, scope, type);

        // because the ArtifactCollection could be a collection over a test variant which ends
        // up being a ArtifactCollectionWithExtraArtifact, we need to get the actual list
        // without the tested artifact.
        if (artifacts instanceof ArtifactCollectionWithExtraArtifact) {
            return ((ArtifactCollectionWithExtraArtifact) artifacts).getParentArtifacts();
        }

        return artifacts;
    }

    /**
     * Create a level 4 dependency graph.
     *
     * @see AndroidProject#MODEL_LEVEL_4_NEW_DEP_MODEL
     */
    public DependencyGraphs createLevel4DependencyGraph(
            @NonNull VariantScope variantScope,
            boolean withFullDependency,
            boolean downloadSources,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        // FIXME change the way we compare dependencies b/64387392

        try {
            // get the compile artifact first.
            Set<HashableResolvedArtifactResult> compileArtifacts =
                    getAllArtifacts(variantScope, COMPILE_CLASSPATH, dependencyFailureHandler);

            // force download the javadoc/source artifacts of compile scope only, since the
            // the runtime-only is never used from the IDE.
            if (downloadSources) {
                Set<ComponentIdentifier> ids =
                        Sets.newHashSetWithExpectedSize(compileArtifacts.size());
                for (HashableResolvedArtifactResult artifact : compileArtifacts) {
                    ids.add(artifact.getId().getComponentIdentifier());
                }

                handleSources(variantScope.getGlobalScope().getProject(), ids, failureConsumer);
            }

            // In this simpler model, faster computation of the runtime dependencies to get the
            // provided bit.
            if (!withFullDependency) {
                // get the runtime artifacts. We only care about the ComponentIdentifier so we don't
                // need to call getAllArtifacts() which computes a lot more many things, and takes
                // longer on large projects.
                // Instead just get all the jars to get all the dependencies.
                ArtifactCollection runtimeArtifactCollection =
                        computeArtifactList(
                                variantScope,
                                RUNTIME_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.ALL,
                                AndroidArtifacts.ArtifactType.JAR);

                // build a list of the runtime ComponentIdentifiers
                final Set<ResolvedArtifactResult> runtimeArtifacts =
                        runtimeArtifactCollection.getArtifacts();
                final Set<ComponentIdentifier> runtimeIdentifiers =
                        Sets.newHashSetWithExpectedSize(runtimeArtifacts.size());
                for (ResolvedArtifactResult result : runtimeArtifacts) {
                    runtimeIdentifiers.add(result.getId().getComponentIdentifier());
                }

                List<String> providedAddresses = Lists.newArrayList();

                List<GraphItem> compileItems =
                        Lists.newArrayListWithCapacity(compileArtifacts.size());
                for (HashableResolvedArtifactResult artifact : compileArtifacts) {
                    final GraphItemImpl graphItem =
                            new GraphItemImpl(computeAddress(artifact), ImmutableList.of());
                    compileItems.add(graphItem);
                    sLibraryCache.get(artifact);
                    if (!runtimeIdentifiers.contains(artifact.getId().getComponentIdentifier())) {
                        providedAddresses.add(graphItem.getArtifactAddress());
                    }
                }

                return new SimpleDependencyGraphsImpl(compileItems, providedAddresses);
            }

            // now build the list of compile items
            List<GraphItem> compileItems = Lists.newArrayListWithCapacity(compileArtifacts.size());
            for (HashableResolvedArtifactResult artifact : compileArtifacts) {
                compileItems.add(new GraphItemImpl(computeAddress(artifact), ImmutableList.of()));
                sLibraryCache.get(artifact);
            }

            // in this mode, compute GraphItem for the runtime configuration
            // get the runtime artifacts.
            Set<HashableResolvedArtifactResult> runtimeArtifacts =
                    getAllArtifacts(variantScope, RUNTIME_CLASSPATH, dependencyFailureHandler);

            List<GraphItem> runtimeItems = Lists.newArrayListWithCapacity(runtimeArtifacts.size());
            for (HashableResolvedArtifactResult artifact : runtimeArtifacts) {
                runtimeItems.add(new GraphItemImpl(computeAddress(artifact), ImmutableList.of()));
                sLibraryCache.get(artifact);
            }

            // compute the provided dependency list, by comparing the compile and runtime items
            List<GraphItem> providedItems = Lists.newArrayList(compileItems);
            providedItems.removeAll(runtimeItems);
            final ImmutableList<String> providedAddresses =
                    providedItems
                            .stream()
                            .map(GraphItem::getArtifactAddress)
                            .collect(ImmutableCollectors.toImmutableList());

            // FIXME: when full dependency is enabled, this should return a full graph instead of a
            // flat list.

            return new FullDependencyGraphsImpl(
                    compileItems,
                    runtimeItems,
                    providedAddresses,
                    ImmutableList.of()); // FIXME: actually get skip list
        } finally {
            dependencyFailureHandler.collectIssues().forEach(failureConsumer);
        }
    }

    /** Create a level 1 dependency list. */
    @NonNull
    public DependenciesImpl createDependencies(
            @NonNull VariantScope variantScope,
            boolean downloadSources,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        // FIXME change the way we compare dependencies b/64387392

        try {
            ImmutableList.Builder<String> projects = ImmutableList.builder();
            ImmutableList.Builder<AndroidLibrary> androidLibraries = ImmutableList.builder();
            ImmutableList.Builder<JavaLibrary> javaLibrary = ImmutableList.builder();

            // get the runtime artifact. We only care about the ComponentIdentifier so we don't
            // need to call getAllArtifacts() which computes a lot more many things.
            // Instead just get all the jars to get all the dependencies.
            ArtifactCollection runtimeArtifactCollection =
                    computeArtifactList(
                            variantScope,
                            RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.JAR);

            // build a list of the artifacts
            Set<ComponentIdentifier> runtimeIdentifiers =
                    new HashSet<>(runtimeArtifactCollection.getArtifacts().size());
            for (ResolvedArtifactResult result : runtimeArtifactCollection.getArtifacts()) {
                runtimeIdentifiers.add(result.getId().getComponentIdentifier());
            }

            Set<HashableResolvedArtifactResult> artifacts =
                    getAllArtifacts(variantScope, COMPILE_CLASSPATH, dependencyFailureHandler);

            for (HashableResolvedArtifactResult artifact : artifacts) {
                ComponentIdentifier id = artifact.getId().getComponentIdentifier();

                boolean isProvided = !runtimeIdentifiers.contains(id);

                boolean isSubproject = id instanceof ProjectComponentIdentifier;
                String projectPath =
                        isSubproject ? ((ProjectComponentIdentifier) id).getProjectPath() : null;

                if (artifact.getDependencyType() == JAVA) {
                    if (projectPath != null) {
                        projects.add(projectPath);
                        continue;
                    }
                    // FIXME: Dependencies information is not set correctly.
                    javaLibrary.add(
                            new com.android.build.gradle.internal.ide.JavaLibraryImpl(
                                    artifact.getFile(),
                                    null,
                                    ImmutableList.of(), /* dependencies */
                                    null, /* requestedCoordinates */
                                    checkNotNull(sMavenCoordinatesCache.get(artifact)),
                                    false, /* isSkipped */
                                    isProvided));
                } else {
                    if (artifact.isWrappedModule()) {
                        // force external dependency mode.
                        projectPath = null;
                    }

                    final File explodedFolder = artifact.getFile();

                    //noinspection VariableNotUsedInsideIf
                    androidLibraries.add(
                            new com.android.build.gradle.internal.ide.AndroidLibraryImpl(
                                    // FIXME: Dependencies information is not set correctly.
                                    checkNotNull(sMavenCoordinatesCache.get(artifact)),
                                    projectPath,
                                    artifact.bundleResult != null
                                            ? artifact.bundleResult.getFile()
                                            : explodedFolder,
                                    // fallback so that the value is non-null
                                    explodedFolder, /*exploded folder*/
                                    getVariant(artifact),
                                    isProvided,
                                    false, /* dependencyItem.isSkipped() */
                                    ImmutableList.of(), /* androidLibraries */
                                    ImmutableList.of(), /* javaLibraries */
                                    findLocalJarsAsFiles(explodedFolder)));
                }
            }

            // force download the source artifacts of the compile classpath only.
            if (downloadSources) {
                Set<ComponentIdentifier> ids = Sets.newHashSetWithExpectedSize(artifacts.size());
                for (HashableResolvedArtifactResult artifact : artifacts) {
                    ids.add(artifact.getId().getComponentIdentifier());
                }

                handleSources(variantScope.getGlobalScope().getProject(), ids, failureConsumer);
            }

            return new DependenciesImpl(
                    androidLibraries.build(), javaLibrary.build(), projects.build());
        } finally {
            dependencyFailureHandler.collectIssues().forEach(failureConsumer);
        }
    }

    @NonNull
    public static Dependencies clone(@NonNull Dependencies dependencies, int modelLevel) {
        if (modelLevel >= AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
            return EMPTY_DEPENDENCIES_IMPL;
        }

        // these items are already ready for serializable, all we need to clone is
        // the Dependencies instance.
        List<AndroidLibrary> libraries = Collections.emptyList();
        List<JavaLibrary> javaLibraries = Lists.newArrayList(dependencies.getJavaLibraries());
        List<String> projects = Collections.emptyList();

        return new DependenciesImpl(libraries, javaLibraries, projects);
    }

    public static DependencyGraphs clone(
            @NonNull DependencyGraphs dependencyGraphs,
            int modelLevel,
            boolean modelWithFullDependency) {
        if (modelLevel < AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
            return EMPTY_DEPENDENCY_GRAPH;
        }

        Preconditions.checkState(dependencyGraphs instanceof ConfigurationDependencyGraphs);
        ConfigurationDependencyGraphs cdg = (ConfigurationDependencyGraphs) dependencyGraphs;

        // these items are already ready for serializable, all we need to clone is
        // the DependencyGraphs instance.

        List<Library> libs = cdg.getLibraries();
        synchronized (sGlobalLibrary) {
            for (Library library : libs) {
                sGlobalLibrary.put(library.getArtifactAddress(), library);
            }
        }

        final List<GraphItem> nodes = cdg.getCompileDependencies();

        if (modelWithFullDependency) {
            return new FullDependencyGraphsImpl(
                    nodes, nodes, ImmutableList.of(), ImmutableList.of());
        }

        // just need to register the libraries in the global libraries.
        return new SimpleDependencyGraphsImpl(nodes, cdg.getProvidedLibraries());
    }

    private static void handleSources(
            @NonNull Project project,
            @NonNull Set<ComponentIdentifier> artifacts,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        final DependencyHandler dependencies = project.getDependencies();

        try {
            ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery();
            query.forComponents(artifacts);

            @SuppressWarnings("unchecked")
            Class<? extends Artifact>[] artifactTypesArray =
                    (Class<? extends Artifact>[]) new Class<?>[] {SourcesArtifact.class};
            query.withArtifacts(JvmLibrary.class, artifactTypesArray);
            query.execute().getResolvedComponents();
        } catch (Throwable t) {
            DependencyFailureHandlerKt.processDependencyThrowable(
                    t,
                    s -> null,
                    (data, messages) ->
                            failureConsumer.accept(
                                    new SyncIssueImpl(
                                            SyncIssue.TYPE_GENERIC,
                                            SyncIssue.SEVERITY_WARNING,
                                            null,
                                            String.format(
                                                    "Unable to download sources: %s",
                                                    messages.get(0)),
                                            messages)));
        }
    }

    public enum DependencyType {
        JAVA(EXT_JAR),
        ANDROID(EXT_AAR);

        @NonNull private final String extension;

        DependencyType(@NonNull String extension) {
            this.extension = extension;
        }

        @NonNull
        public String getExtension() {
            return extension;
        }
    }

    @NonNull
    private static List<String> findLocalJarsAsStrings(@NonNull File folder) {
        File localJarRoot = FileUtils.join(folder, FD_JARS, FD_AAR_LIBS);

        if (!localJarRoot.isDirectory()) {
            return ImmutableList.of();
        }

        String[] jarFiles = localJarRoot.list((dir, name) -> name.endsWith(DOT_JAR));
        if (jarFiles != null && jarFiles.length > 0) {
            List<String> list = Lists.newArrayListWithCapacity(jarFiles.length);
            for (String jarFile : jarFiles) {
                list.add(FD_JARS + File.separatorChar + FD_AAR_LIBS + File.separatorChar + jarFile);
            }

            return list;
        }

        return ImmutableList.of();
    }

    @NonNull
    private static List<File> findLocalJarsAsFiles(@NonNull File folder) {
        File localJarRoot = FileUtils.join(folder, FD_JARS, FD_AAR_LIBS);

        if (!localJarRoot.isDirectory()) {
            return ImmutableList.of();
        }

        File[] jarFiles = localJarRoot.listFiles((dir, name) -> name.endsWith(DOT_JAR));
        if (jarFiles != null && jarFiles.length > 0) {
            return ImmutableList.copyOf(jarFiles);
        }

        return ImmutableList.of();
    }

    public static class HashableResolvedArtifactResult implements ResolvedArtifactResult {
        @NonNull private final ResolvedArtifactResult delegate;
        @NonNull private final DependencyType dependencyType;
        private final boolean wrappedModule;
        /**
         * An optional sub-result that represents the bundle file, when the current result
         * represents an exploded aar
         */
        private final ResolvedArtifactResult bundleResult;

        public HashableResolvedArtifactResult(
                @NonNull ResolvedArtifactResult delegate,
                @NonNull DependencyType dependencyType,
                boolean wrappedModule,
                @Nullable ResolvedArtifactResult bundleResult) {
            this.delegate = delegate;
            this.dependencyType = dependencyType;
            this.wrappedModule = wrappedModule;
            this.bundleResult = bundleResult;
        }

        @Override
        public File getFile() {
            return delegate.getFile();
        }

        @Override
        public ResolvedVariantResult getVariant() {
            return delegate.getVariant();
        }

        @Override
        public ComponentArtifactIdentifier getId() {
            return delegate.getId();
        }

        @Override
        public Class<? extends Artifact> getType() {
            return delegate.getType();
        }

        @NonNull
        public DependencyType getDependencyType() {
            return dependencyType;
        }

        public boolean isWrappedModule() {
            return wrappedModule;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HashableResolvedArtifactResult that = (HashableResolvedArtifactResult) o;
            return wrappedModule == that.wrappedModule
                    && dependencyType == that.dependencyType
                    && Objects.equal(getFile(), that.getFile())
                    && Objects.equal(getId(), that.getId())
                    && Objects.equal(getType(), that.getType());
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(delegate, dependencyType, wrappedModule);
        }
    }
}
