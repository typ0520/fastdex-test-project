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

package com.android.build.gradle.internal.publishing;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.METADATA_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.AnchorOutputType.ALL_CLASSES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.AIDL_PARCELABLE;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.APK;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.APK_MAPPING;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.APP_CLASSES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.CONSUMER_PROGUARD_FILE;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.FEATURE_APPLICATION_ID_DECLARATION;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.FEATURE_CLASSES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.FEATURE_IDS_DECLARATION;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.FEATURE_RESOURCE_PKG;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.FEATURE_TRANSITIVE_DEPS;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.FULL_JAR;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.JAVA_RES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.LIBRARY_CLASSES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.LIBRARY_JAVA_RES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.LIBRARY_JNI;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.LIBRARY_MANIFEST;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.LINT_JAR;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.MANIFEST_METADATA;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.METADADA_FEATURE_MANIFEST;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.METADATA_APP_ID_DECLARATION;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.METADATA_FEATURE_DECLARATION;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.PACKAGED_RES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.PUBLIC_RES;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.RENDERSCRIPT_HEADERS;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.SYMBOL_LIST;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.SYMBOL_LIST_WITH_PACKAGE_NAME;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.internal.scope.TaskOutputHolder.OutputType;
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType;
import com.android.builder.core.VariantType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Publishing spec for variants and tasks outputs.
 *
 * <p>This builds a bi-directional mapping between task outputs and published artifacts (for project
 * to project publication), as well as where to publish the artifact (which {@link
 * com.android.tools.lint.client.api.Configuration} via the {@link PublishedConfigType} enum.)
 *
 * <p>This mapping is per {@link VariantType} to allow for different task outputs to be published
 * under the same {@link ArtifactType}.
 *
 * <p>This mapping also offers reverse mapping override for tests (per {@link VariantType} as well),
 * allowing a test variant to not use exactly the published artifact of the tested variant but a
 * different version. This allows for instance the unit tests of libraries to use the full Java
 * classes, including the R class for unit testing, while the published artifact does not contain
 * the R class. Similarly, the override can extend the published scope (api vs runtime), which is
 * needed to run the unit tests.
 */
public class VariantPublishingSpec {

    private static final ImmutableList<PublishedConfigType> API_ELEMENTS_ONLY =
            ImmutableList.of(API_ELEMENTS);
    private static final ImmutableList<PublishedConfigType> RUNTIME_ELEMENTS_ONLY =
            ImmutableList.of(RUNTIME_ELEMENTS);
    private static final ImmutableList<PublishedConfigType> API_AND_RUNTIME_ELEMENTS =
            ImmutableList.of(API_ELEMENTS, RUNTIME_ELEMENTS);
    private static final ImmutableList<PublishedConfigType> METADATA_ELEMENTS_ONLY =
            ImmutableList.of(METADATA_ELEMENTS);

    private static final Map<VariantType, VariantPublishingSpec> variantMap =
            Maps.newEnumMap(VariantType.class);

    @Nullable private final VariantPublishingSpec parentSpec;
    @NonNull private final VariantType variantType;
    @NonNull private final Set<OutputPublishingSpec> taskSpecs;

    @NonNull
    private final Map<VariantType, VariantPublishingSpec> testingSpecs =
            Maps.newEnumMap(VariantType.class);

    private Map<ArtifactType, OutputPublishingSpec> artifactMap;
    private Map<OutputType, OutputPublishingSpec> outputMap;


    static {
        variantSpec(
                        VariantType.DEFAULT,
                        outputSpec(
                                MANIFEST_METADATA,
                                ArtifactType.MANIFEST_METADATA,
                                API_ELEMENTS_ONLY),
                        outputSpec(
                                APP_CLASSES,
                                // use TYPE_JAR to give access to this via the model for now,
                                // the JarTransform will convert it back to CLASSES
                                // FIXME: stop using TYPE_JAR for APK_CLASSES
                                ArtifactType.JAR,
                                API_ELEMENTS_ONLY),
                        outputSpec(APK, ArtifactType.APK, RUNTIME_ELEMENTS_ONLY),
                        outputSpec(APK_MAPPING, ArtifactType.APK_MAPPING, API_ELEMENTS_ONLY),
                        outputSpec(
                                METADATA_APP_ID_DECLARATION,
                                ArtifactType.METADATA_APP_ID_DECLARATION,
                                METADATA_ELEMENTS_ONLY))
                .withTestingSpec(
                        VariantType.ANDROID_TEST,
                        // java output query is done via CLASSES instead of JAR, so provide
                        // the right backward mapping
                        outputSpec(APP_CLASSES, ArtifactType.CLASSES, API_ELEMENTS_ONLY))
                .withTestingSpec(
                        VariantType.UNIT_TEST,
                        // java output query is done via CLASSES instead of JAR, so provide
                        // the right backward mapping. Also add it to the runtime as it's
                        // needed to run the tests!
                        outputSpec(ALL_CLASSES, ArtifactType.CLASSES, API_AND_RUNTIME_ELEMENTS),
                        // JAVA_RES isn't published by the app, but we need it for the unit tests
                        outputSpec(JAVA_RES, ArtifactType.JAVA_RES, API_AND_RUNTIME_ELEMENTS));

        variantSpec(
                        VariantType.LIBRARY,
                        // manifest is published to both to compare and detect provided-only library
                        // dependencies.
                        outputSpec(
                                LIBRARY_MANIFEST, ArtifactType.MANIFEST, API_AND_RUNTIME_ELEMENTS),
                        outputSpec(MERGED_ASSETS, ArtifactType.ASSETS, RUNTIME_ELEMENTS_ONLY),
                        outputSpec(PACKAGED_RES, ArtifactType.ANDROID_RES, RUNTIME_ELEMENTS_ONLY),
                        outputSpec(PUBLIC_RES, ArtifactType.PUBLIC_RES, RUNTIME_ELEMENTS_ONLY),
                        outputSpec(SYMBOL_LIST, ArtifactType.SYMBOL_LIST, RUNTIME_ELEMENTS_ONLY),
                        outputSpec(
                                SYMBOL_LIST_WITH_PACKAGE_NAME,
                                ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME,
                                RUNTIME_ELEMENTS_ONLY),
                        outputSpec(AIDL_PARCELABLE, ArtifactType.AIDL, API_ELEMENTS_ONLY),
                        outputSpec(
                                RENDERSCRIPT_HEADERS, ArtifactType.RENDERSCRIPT, API_ELEMENTS_ONLY),
                        outputSpec(
                                TaskOutputType.DATA_BINDING_ARTIFACT,
                                ArtifactType.DATA_BINDING_ARTIFACT,
                                API_ELEMENTS_ONLY),
                        outputSpec(LIBRARY_CLASSES, ArtifactType.CLASSES, API_AND_RUNTIME_ELEMENTS),
                        outputSpec(FULL_JAR, ArtifactType.JAR, API_AND_RUNTIME_ELEMENTS),
                        outputSpec(LIBRARY_JAVA_RES, ArtifactType.JAVA_RES, RUNTIME_ELEMENTS_ONLY),
                        outputSpec(
                                CONSUMER_PROGUARD_FILE,
                                ArtifactType.PROGUARD_RULES,
                                RUNTIME_ELEMENTS_ONLY),
                        outputSpec(LIBRARY_JNI, ArtifactType.JNI, RUNTIME_ELEMENTS_ONLY),
                        outputSpec(LINT_JAR, ArtifactType.LINT, RUNTIME_ELEMENTS_ONLY))
                .withTestingSpec(
                        VariantType.UNIT_TEST,
                        // unit test need ALL_CLASSES instead of LIBRARY_CLASSES to get
                        // access to the R class. Also scope should be API+Runtime.
                        outputSpec(ALL_CLASSES, ArtifactType.CLASSES, API_AND_RUNTIME_ELEMENTS));

        variantSpec(
                VariantType.FEATURE,
                outputSpec(
                        METADATA_FEATURE_DECLARATION,
                        ArtifactType.METADATA_FEATURE_DECLARATION,
                        METADATA_ELEMENTS_ONLY),
                outputSpec(
                        METADADA_FEATURE_MANIFEST,
                        ArtifactType.METADATA_FEATURE_MANIFEST,
                        METADATA_ELEMENTS_ONLY),
                outputSpec(
                        FEATURE_IDS_DECLARATION,
                        ArtifactType.FEATURE_IDS_DECLARATION,
                        API_ELEMENTS_ONLY),
                outputSpec(
                        FEATURE_APPLICATION_ID_DECLARATION,
                        ArtifactType.FEATURE_APPLICATION_ID_DECLARATION,
                        API_ELEMENTS_ONLY),
                outputSpec(
                        FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG, API_ELEMENTS_ONLY),
                outputSpec(
                        FEATURE_TRANSITIVE_DEPS,
                        ArtifactType.FEATURE_TRANSITIVE_DEPS,
                        RUNTIME_ELEMENTS_ONLY),
                outputSpec(FEATURE_CLASSES, ArtifactType.CLASSES, API_ELEMENTS_ONLY),
                outputSpec(APK, ArtifactType.APK, RUNTIME_ELEMENTS_ONLY));

        // empty specs
        variantSpec(VariantType.ANDROID_TEST);
        variantSpec(VariantType.UNIT_TEST);
        variantSpec(VariantType.INSTANTAPP);
    }

    public static VariantPublishingSpec getVariantSpec(@NonNull VariantType variantType) {
        return variantMap.get(variantType);
    }

    public VariantPublishingSpec getTestingSpec(@NonNull VariantType variantType) {
        Preconditions.checkState(variantType.isForTesting());

        VariantPublishingSpec testingSpec = testingSpecs.get(variantType);
        if (testingSpec != null) {
            return testingSpec;
        }

        return this;
    }

    public OutputPublishingSpec getSpec(@NonNull ArtifactType artifactType) {
        if (artifactMap == null) {
            artifactMap = Maps.newEnumMap(ArtifactType.class);
            for (OutputPublishingSpec taskSpec : taskSpecs) {
                artifactMap.put(taskSpec.artifactType, taskSpec);
            }
        }

        final OutputPublishingSpec spec = artifactMap.get(artifactType);
        if (spec != null) {
            return spec;
        }

        if (parentSpec != null) {
            return parentSpec.getSpec(artifactType);
        }

        return null;
    }

    public OutputPublishingSpec getSpec(@NonNull OutputType taskOutputType) {
        if (outputMap == null) {
            outputMap = Maps.newHashMap();
            for (OutputPublishingSpec taskSpec : taskSpecs) {
                outputMap.put(taskSpec.outputType, taskSpec);
            }
        }

        final OutputPublishingSpec spec = outputMap.get(taskOutputType);
        if (spec != null) {
            return spec;
        }

        if (parentSpec != null) {
            return parentSpec.getSpec(taskOutputType);
        }

        return null;
    }

    private static VariantPublishingSpec variantSpec(
            @NonNull VariantType variantType, @NonNull OutputPublishingSpec... taskSpecs) {
        final VariantPublishingSpec spec =
                new VariantPublishingSpec(variantType, ImmutableSet.copyOf(taskSpecs));
        variantMap.put(spec.variantType, spec);

        return spec;
    }

    private VariantPublishingSpec withTestingSpec(
            @NonNull VariantType variantType, @NonNull OutputPublishingSpec... taskSpecs) {
        Preconditions.checkState(!this.variantType.isForTesting());
        Preconditions.checkState(variantType.isForTesting());
        Preconditions.checkState(!testingSpecs.containsKey(variantType));

        final VariantPublishingSpec spec =
                new VariantPublishingSpec(this, variantType, ImmutableSet.copyOf(taskSpecs));

        testingSpecs.put(variantType, spec);

        return this;
    }

    private static OutputPublishingSpec outputSpec(
            @NonNull OutputType taskOutputType,
            @NonNull ArtifactType artifactType,
            @NonNull ImmutableList<PublishedConfigType> publishedConfigTypes) {
        return new OutputPublishingSpec(taskOutputType, artifactType, publishedConfigTypes);
    }

    private VariantPublishingSpec(
            @NonNull VariantType variantType, @NonNull Set<OutputPublishingSpec> taskSpecs) {
        this(null, variantType, taskSpecs);
    }

    private VariantPublishingSpec(
            @Nullable VariantPublishingSpec parentSpec,
            @NonNull VariantType variantType,
            @NonNull Set<OutputPublishingSpec> taskSpecs) {
        this.parentSpec = parentSpec;
        this.variantType = variantType;
        this.taskSpecs = taskSpecs;
    }

    @VisibleForTesting
    static Map<VariantType, VariantPublishingSpec> getVariantMap() {
        return variantMap;
    }

    public static final class OutputPublishingSpec {
        @NonNull private final OutputType outputType;
        @NonNull private final ArtifactType artifactType;
        @NonNull private final List<PublishedConfigType> publishedConfigTypes;

        private OutputPublishingSpec(
                @NonNull OutputType outputType,
                @NonNull ArtifactType artifactType,
                @NonNull ImmutableList<PublishedConfigType> publishedConfigTypes) {
            this.outputType = outputType;
            this.artifactType = artifactType;
            this.publishedConfigTypes = publishedConfigTypes;
        }

        @NonNull
        public OutputType getOutputType() {
            return outputType;
        }

        @NonNull
        public ArtifactType getArtifactType() {
            return artifactType;
        }

        @NonNull
        public Collection<PublishedConfigType> getPublishedConfigTypes() {
            return publishedConfigTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            OutputPublishingSpec that = (OutputPublishingSpec) o;
            return outputType == that.outputType
                    && artifactType == that.artifactType
                    && Objects.equals(publishedConfigTypes, that.publishedConfigTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outputType, artifactType, publishedConfigTypes);
        }
    }
}
