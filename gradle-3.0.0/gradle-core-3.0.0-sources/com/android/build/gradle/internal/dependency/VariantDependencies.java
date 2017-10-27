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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.attributes.BuildTypeAttr;
import com.android.build.api.attributes.ProductFlavorAttr;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.VariantType;
import com.android.builder.model.SyncIssue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;

/**
 * Object that represents the dependencies of variant.
 *
 * <p>The dependencies are expressed as composite Gradle configuration objects that extends
 * all the configuration objects of the "configs".</p>
 *
 * <p>It optionally contains the dependencies for a test config for the given config.</p>
 */
public class VariantDependencies {

    public static final String CONFIG_NAME_COMPILE = "compile";
    public static final String CONFIG_NAME_S_COMPILE = "%sCompile";
    public static final String CONFIG_NAME_PUBLISH = "publish";
    public static final String CONFIG_NAME_S_PUBLISH = "%sPublish";
    public static final String CONFIG_NAME_APK = "apk";
    public static final String CONFIG_NAME_S_APK = "%sApk";
    public static final String CONFIG_NAME_PROVIDED = "provided";
    public static final String CONFIG_NAME_S_PROVIDED = "%sProvided";
    public static final String CONFIG_NAME_WEAR_APP = "wearApp";
    public static final String CONFIG_NAME_ANNOTATION_PROCESSOR = "annotationProcessor";
    public static final String CONFIG_NAME_S_WEAR_APP = "%sWearApp";
    public static final String CONFIG_NAME_S_ANNOTATION_PROCESSOR = "%sAnnotationProcessor";

    public static final String CONFIG_NAME_API = "api";
    public static final String CONFIG_NAME_S_API = "%sApi";
    public static final String CONFIG_NAME_COMPILE_ONLY = "compileOnly";
    public static final String CONFIG_NAME_S_COMPILE_ONLY = "%sCompileOnly";
    public static final String CONFIG_NAME_IMPLEMENTATION = "implementation";
    public static final String CONFIG_NAME_S_IMPLEMENTATION = "%sImplementation";
    public static final String CONFIG_NAME_RUNTIME_ONLY = "runtimeOnly";
    public static final String CONFIG_NAME_S_RUNTIME_ONLY = "%sRuntimeOnly";
    public static final String CONFIG_NAME_FEATURE = "feature";
    public static final String CONFIG_NAME_APPLICATION = "application";

    public static final String CONFIG_NAME_LINTCHECKS = "lintChecks";

    public static final String USAGE_LINT = "android-lint-checks";

    @NonNull private final String variantName;

    @NonNull private final Configuration compileClasspath;
    @NonNull private final Configuration runtimeClasspath;

    @Nullable private final Configuration apiElements;
    @Nullable private final Configuration runtimeElements;

    @NonNull private final Configuration annotationProcessorConfiguration;

    @Nullable private final Configuration metadataElements;

    @Nullable private final Configuration wearAppConfiguration;
    @Nullable private final Configuration metadataValuesConfiguration;

    /**
     *  Whether we have a direct dependency on com.android.support:support-annotations; this
     * is used to drive whether we extract annotations when building libraries for example
     */
    private boolean annotationsPresent;

    public static final class Builder {
        @NonNull private final Project project;
        @NonNull private final ErrorReporter errorReporter;
        @NonNull private final GradleVariantConfiguration variantConfiguration;
        private boolean baseSplit = false;
        private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection;

        private AndroidTypeAttr consumeType;
        private AndroidTypeAttr publishType;

        // default size should be enough. It's going to be rare for a variant to include
        // more than a few configurations (main, build-type, flavors...)
        // At most it's going to be flavor dimension count + 5:
        // variant-specific, build type, multi-flavor, flavor1, ..., flavorN, defaultConfig, test.
        // Default hash-map size of 16 (w/ load factor of .75) should be enough.
        private final Set<Configuration> compileClasspaths = Sets.newHashSet();
        private final Set<Configuration> apiClasspaths = Sets.newHashSet();
        private final Set<Configuration> runtimeClasspaths = Sets.newHashSet();
        private final Set<Configuration> annotationConfigs = Sets.newHashSet();
        private final Set<Configuration> wearAppConfigs = Sets.newHashSet();

        protected Builder(
                @NonNull Project project,
                @NonNull ErrorReporter errorReporter,
                @NonNull GradleVariantConfiguration variantConfiguration) {
            this.project = project;
            this.errorReporter = errorReporter;
            this.variantConfiguration = variantConfiguration;
        }

        public Builder setPublishType(@NonNull AndroidTypeAttr publishType) {
            this.publishType = publishType;
            return this;
        }

        public Builder setConsumeType(@NonNull AndroidTypeAttr consumeType) {
            this.consumeType = consumeType;
            return this;
        }

        public Builder addSourceSets(@NonNull DefaultAndroidSourceSet... sourceSets) {
            for (DefaultAndroidSourceSet sourceSet : sourceSets) {
                addSourceSet(sourceSet);
            }
            return this;
        }

        public Builder addSourceSets(@NonNull Collection<DefaultAndroidSourceSet> sourceSets) {
            for (DefaultAndroidSourceSet sourceSet : sourceSets) {
                addSourceSet(sourceSet);
            }
            return this;
        }

        public Builder setBaseSplit(boolean baseSplit) {
            this.baseSplit = baseSplit;
            return this;
        }

        public Builder addSourceSet(@Nullable DefaultAndroidSourceSet sourceSet) {
            if (sourceSet != null) {

                final ConfigurationContainer configs = project.getConfigurations();

                compileClasspaths.add(configs.getByName(sourceSet.getCompileOnlyConfigurationName()));
                runtimeClasspaths.add(configs.getByName(sourceSet.getRuntimeOnlyConfigurationName()));

                final Configuration implementationConfig = configs.getByName(sourceSet.getImplementationConfigurationName());
                compileClasspaths.add(implementationConfig);
                runtimeClasspaths.add(implementationConfig);

                String apiConfigName = sourceSet.getApiConfigurationName();
                if (apiConfigName != null) {
                    apiClasspaths.add(configs.getByName(apiConfigName));
                }

                annotationConfigs.add(configs.getByName(sourceSet.getAnnotationProcessorConfigurationName()));
                wearAppConfigs.add(configs.getByName(sourceSet.getWearAppConfigurationName()));
            }

            return this;
        }

        public Builder setFlavorSelection(
                @NonNull Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection) {
            this.flavorSelection = flavorSelection;
            return this;
        }

        public VariantDependencies build() {
            Preconditions.checkNotNull(consumeType);

            ObjectFactory factory = project.getObjects();

            final Usage apiUsage = factory.named(Usage.class, Usage.JAVA_API);
            final Usage runtimeUsage = factory.named(Usage.class, Usage.JAVA_RUNTIME);

            String variantName = variantConfiguration.getFullName();
            VariantType variantType = variantConfiguration.getType();
            String buildType = variantConfiguration.getBuildType().getName();
            Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> consumptionFlavorMap =
                    getFlavorAttributes(flavorSelection);

            final ConfigurationContainer configurations = project.getConfigurations();

            final String compileClasspathName = variantName + "CompileClasspath";
            Configuration compileClasspath = configurations.maybeCreate(compileClasspathName);
            compileClasspath.setVisible(false);
            compileClasspath.setDescription("Resolved configuration for compilation for variant: " + variantName);
            compileClasspath.setExtendsFrom(compileClasspaths);
            compileClasspath.setCanBeConsumed(false);
            compileClasspath.getResolutionStrategy().sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST);
            final AttributeContainer compileAttributes = compileClasspath.getAttributes();
            applyVariantAttributes(compileAttributes, buildType, consumptionFlavorMap);
            compileAttributes.attribute(Usage.USAGE_ATTRIBUTE, apiUsage);
            compileAttributes.attribute(AndroidTypeAttr.ATTRIBUTE, consumeType);

            Configuration annotationProcessor =
                    configurations.maybeCreate(variantName + "AnnotationProcessorClasspath");
            annotationProcessor.setVisible(false);
            annotationProcessor.setDescription("Resolved configuration for annotation-processor for variant: " + variantName);
            annotationProcessor.setExtendsFrom(annotationConfigs);
            annotationProcessor.setCanBeConsumed(false);
            // the annotation processor is using its dependencies for running the processor, so we need
            // all the runtime graph.
            final AttributeContainer annotationAttributes = annotationProcessor.getAttributes();
            annotationAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
            applyVariantAttributes(annotationAttributes, buildType, consumptionFlavorMap);

            final String runtimeClasspathName = variantName + "RuntimeClasspath";
            Configuration runtimeClasspath = configurations.maybeCreate(runtimeClasspathName);
            runtimeClasspath.setVisible(false);
            runtimeClasspath.setDescription("Resolved configuration for runtime for variant: " + variantName);
            runtimeClasspath.setExtendsFrom(runtimeClasspaths);
            runtimeClasspath.setCanBeConsumed(false);
            runtimeClasspath.getResolutionStrategy().sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST);
            final AttributeContainer runtimeAttributes = runtimeClasspath.getAttributes();
            applyVariantAttributes(runtimeAttributes, buildType, consumptionFlavorMap);
            runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
            runtimeAttributes.attribute(AndroidTypeAttr.ATTRIBUTE, consumeType);

            Configuration wearApp = null;
            if (publishType != null && publishType.getName().equals(AndroidTypeAttr.APK)) {
                wearApp = configurations.maybeCreate(variantName + "WearBundling");
                wearApp.setDescription(
                        "Resolved Configuration for wear app bundling for variant: " + variantName);
                wearApp.setExtendsFrom(wearAppConfigs);
                wearApp.setCanBeConsumed(false);
                final AttributeContainer wearAttributes = wearApp.getAttributes();
                applyVariantAttributes(wearAttributes, buildType, consumptionFlavorMap);
                // because the APK is published to Runtime, then we need to make sure this one consumes RUNTIME as well.
                wearAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                wearAttributes.attribute(
                        AndroidTypeAttr.ATTRIBUTE,
                        factory.named(AndroidTypeAttr.class, AndroidTypeAttr.APK));
            }

            Configuration apiElements = null;
            Configuration runtimeElements = null;
            Configuration metadataElements = null;
            Configuration metadataValues = null;

            if (publishType != null) {
                Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> publicationFlavorMap =
                        getFlavorAttributes(null);

                // this is the configuration that contains the artifacts for inter-module
                // dependencies.
                runtimeElements = configurations.maybeCreate(variantName + "RuntimeElements");
                runtimeElements.setDescription("Runtime elements for " + variantName);
                runtimeElements.setCanBeResolved(false);

                final AttributeContainer runtimeElementsAttributes =
                        runtimeElements.getAttributes();
                applyVariantAttributes(runtimeElementsAttributes, buildType, publicationFlavorMap);
                VariantAttr variantNameAttr = factory.named(VariantAttr.class, variantName);
                runtimeElementsAttributes.attribute(VariantAttr.ATTRIBUTE, variantNameAttr);
                runtimeElementsAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                runtimeElementsAttributes.attribute(AndroidTypeAttr.ATTRIBUTE, publishType);

                // if the variant is not a library, then the publishing configuration should
                // not extend from anything. It's mostly there to access the artifacts from
                // another project but it shouldn't bring any dependencies with it.
                if (variantType == VariantType.LIBRARY) {
                    runtimeElements.extendsFrom(runtimeClasspath);
                }

                apiElements = configurations.maybeCreate(variantName + "ApiElements");
                apiElements.setDescription("API elements for " + variantName);
                apiElements.setCanBeResolved(false);
                final AttributeContainer apiElementsAttributes = apiElements.getAttributes();
                applyVariantAttributes(apiElementsAttributes, buildType, publicationFlavorMap);
                apiElementsAttributes.attribute(VariantAttr.ATTRIBUTE, variantNameAttr);
                apiElementsAttributes.attribute(Usage.USAGE_ATTRIBUTE, apiUsage);
                apiElementsAttributes.attribute(AndroidTypeAttr.ATTRIBUTE, publishType);
                // apiElements only extends the api classpaths.
                apiElements.setExtendsFrom(apiClasspaths);

                if (variantType != VariantType.LIBRARY) {
                    // FIXME: we should create this configuration unconditionally. This is a quick
                    // workaround so we don't end up with ambiguous resolution between feature
                    // and libraries. This is fine for now as libraries do not publish anything
                    // there.

                    metadataElements = configurations.maybeCreate(variantName + "MetadataElements");
                    metadataElements.setDescription("Metadata elements for " + variantName);
                    metadataElements.setCanBeResolved(false);
                    final AttributeContainer metadataElementsAttributes =
                            metadataElements.getAttributes();
                    applyVariantAttributes(
                            metadataElementsAttributes, buildType, publicationFlavorMap);
                    metadataElementsAttributes.attribute(
                            AndroidTypeAttr.ATTRIBUTE,
                            factory.named(AndroidTypeAttr.class, AndroidTypeAttr.METADATA));
                    metadataElementsAttributes.attribute(VariantAttr.ATTRIBUTE, variantNameAttr);
                }

                if (variantType == VariantType.FEATURE && baseSplit) {
                    // The variant-specific configuration that will contain the non-base feature
                    // metadata and the application metadata. It's per-variant to contain the
                    // right attribute. It'll be used to get the applicationId and to consume
                    // the manifest.
                    metadataValues = configurations.maybeCreate(variantName + "MetadataValues");
                    metadataValues.extendsFrom(
                            configurations.getByName(CONFIG_NAME_FEATURE),
                            configurations.getByName(CONFIG_NAME_APPLICATION));
                    metadataValues.setDescription(
                            "Metadata Values dependencies for the base Split");
                    metadataValues.setCanBeConsumed(false);
                    final AttributeContainer featureMetadataAttributes =
                            metadataValues.getAttributes();
                    featureMetadataAttributes.attribute(
                            AndroidTypeAttr.ATTRIBUTE,
                            factory.named(AndroidTypeAttr.class, AndroidTypeAttr.METADATA));
                    applyVariantAttributes(
                            featureMetadataAttributes, buildType, consumptionFlavorMap);
                }
            }

            // TODO remove after a while?
            checkOldConfigurations(
                    configurations, "_" + variantName + "Compile", compileClasspathName);
            checkOldConfigurations(configurations, "_" + variantName + "Apk", runtimeClasspathName);
            checkOldConfigurations(
                    configurations, "_" + variantName + "Publish", runtimeClasspathName);

            return new VariantDependencies(
                    variantName,
                    compileClasspath,
                    runtimeClasspath,
                    apiElements,
                    runtimeElements,
                    annotationProcessor,
                    metadataElements,
                    metadataValues,
                    wearApp);
        }

        private static void checkOldConfigurations(
                @NonNull ConfigurationContainer configurations,
                @NonNull String oldConfigName,
                @NonNull String newConfigName) {
            if (configurations.findByName(oldConfigName) != null) {
                throw new RuntimeException(
                        String.format(
                                "Configuration with old name %s found. Use new name %s instead.",
                                oldConfigName, newConfigName));
            }
        }

        /**
         * Returns a map of Configuration attributes containing all the flavor values.
         *
         * @param flavorSelection a list of override for flavor matching or for new attributes.
         */
        @NonNull
        private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> getFlavorAttributes(
                @Nullable Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection) {
            List<CoreProductFlavor> productFlavors = variantConfiguration.getProductFlavors();
            Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> map = Maps.newHashMapWithExpectedSize(productFlavors.size());

            // during a sync, it's possible that the flavors don't have dimension names because
            // the variant manager is lenient about it.
            // In that case we're going to avoid resolving the dependencies anyway, so we can just
            // skip this.
            if (errorReporter.hasSyncIssue(SyncIssue.TYPE_UNNAMED_FLAVOR_DIMENSION)) {
                return map;
            }

            final ObjectFactory objectFactory = project.getObjects();

            // first go through the product flavors and add matching attributes
            for (CoreProductFlavor f : productFlavors) {
                assert f.getDimension() != null;

                map.put(
                        Attribute.of(f.getDimension(), ProductFlavorAttr.class),
                        objectFactory.named(ProductFlavorAttr.class, f.getName()));
            }

            // then go through the override or new attributes.
            if (flavorSelection != null) {
                map.putAll(flavorSelection);
            }

            return map;
        }

        private void applyVariantAttributes(
                @NonNull AttributeContainer attributeContainer,
                @NonNull String buildType,
                @NonNull Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMap) {
            attributeContainer.attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    project.getObjects().named(BuildTypeAttr.class, buildType));
            for (Map.Entry<Attribute<ProductFlavorAttr>, ProductFlavorAttr> entry : flavorMap.entrySet()) {
                attributeContainer.attribute(entry.getKey(), entry.getValue());
            }
        }
    }

    public static Builder builder(
            @NonNull Project project,
            @NonNull ErrorReporter errorReporter,
            @NonNull GradleVariantConfiguration variantConfiguration) {
        return new Builder(project, errorReporter, variantConfiguration);
    }

    private VariantDependencies(
            @NonNull String variantName,
            @NonNull Configuration compileClasspath,
            @NonNull Configuration runtimeClasspath,
            @Nullable Configuration apiElements,
            @Nullable Configuration runtimeElements,
            @NonNull Configuration annotationProcessorConfiguration,
            @Nullable Configuration metadataElements,
            @Nullable Configuration metadataValuesConfiguration,
            @Nullable Configuration wearAppConfiguration) {
        this.variantName = variantName;
        this.compileClasspath = compileClasspath;
        this.runtimeClasspath = runtimeClasspath;
        this.apiElements = apiElements;
        this.runtimeElements = runtimeElements;
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.metadataElements = metadataElements;
        this.metadataValuesConfiguration = metadataValuesConfiguration;
        this.wearAppConfiguration = wearAppConfiguration;
    }

    public String getName() {
        return variantName;
    }

    @NonNull
    public Configuration getCompileClasspath() {
        return compileClasspath;
    }

    @NonNull
    public Configuration getRuntimeClasspath() {
        return runtimeClasspath;
    }

    @Nullable
    public Configuration getApiElements() {
        return apiElements;
    }

    @Nullable
    public Configuration getRuntimeElements() {
        return runtimeElements;
    }

    @NonNull
    public Configuration getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration;
    }

    @Nullable
    public Configuration getMetadataElements() {
        return metadataElements;
    }

    @Nullable
    public Configuration getWearAppConfiguration() {
        return wearAppConfiguration;
    }

    @Nullable
    public Configuration getMetadataValuesConfiguration() {
        return metadataValuesConfiguration;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", variantName)
                .toString();
    }
}
