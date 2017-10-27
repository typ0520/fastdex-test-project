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

package com.android.build.gradle.internal.variant;

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.ApplicationVariantImpl;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Set;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;

/**
 * An implementation of VariantFactory for a project that generates APKs.
 *
 * <p>This can be an app project, or a test-only project, though the default behavior is app.
 */
public class ApplicationVariantFactory extends BaseVariantFactory implements VariantFactory {

    public ApplicationVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension) {
        super(globalScope, androidBuilder, instantiator, extension);
    }

    @Override
    @NonNull
    public BaseVariantData createVariantData(
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull TaskManager taskManager,
            @NonNull Recorder recorder) {
        ApplicationVariantData variant =
                new ApplicationVariantData(
                        globalScope,
                        extension,
                        variantConfiguration,
                        taskManager,
                        androidBuilder.getErrorReporter(),
                        recorder);

        variant.calculateFilters(extension.getSplits());

        Set<String> densities = variant.getFilters(OutputFile.FilterType.DENSITY);
        Set<String> abis = variant.getFilters(OutputFile.FilterType.ABI);

        if (!densities.isEmpty()) {
            variant.setCompatibleScreens(extension.getSplits().getDensity()
                    .getCompatibleScreens());
        }

        OutputScope outputScope = variant.getOutputScope();
        OutputFactory outputFactory = variant.getOutputFactory();

        // create its output
        if (outputScope.getMultiOutputPolicy() == MultiOutputPolicy.MULTI_APK) {

            // if the abi list is not empty and we must generate a universal apk, add it.
            if (abis.isEmpty()) {
                // create the main APK or universal APK depending on whether or not we are going
                // to produce full splits.
                if (densities.isEmpty()) {
                    outputFactory.addMainApk();
                } else {
                    outputFactory.addUniversalApk();
                }
            } else {
                if (extension.getSplits().getAbi().isEnable()
                        && extension.getSplits().getAbi().isUniversalApk()) {
                    outputFactory.addUniversalApk();
                }
                // for each ABI, create a specific split that will contain all densities.
                abis.forEach(
                        abi ->
                                outputFactory.addFullSplit(
                                        ImmutableList.of(Pair.of(OutputFile.FilterType.ABI, abi))));
            }

            // create its outputs
            for (String density : densities) {
                if (!abis.isEmpty()) {
                    for (String abi : abis) {
                        outputFactory.addFullSplit(
                                ImmutableList.of(
                                        Pair.of(OutputFile.FilterType.ABI, abi),
                                        Pair.of(OutputFile.FilterType.DENSITY, density)));
                    }
                } else {
                    outputFactory.addFullSplit(
                            ImmutableList.of(Pair.of(OutputFile.FilterType.DENSITY, density)));
                }
            }
        } else {
            outputFactory.addMainApk();
        }

        return variant;
    }

    @Override
    @NonNull
    public Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData) {
        return ApplicationVariantImpl.class;
    }

    @NonNull
    @Override
    public Collection<VariantType> getVariantConfigurationTypes() {
        return ImmutableList.of(VariantType.DEFAULT);
    }

    @Override
    public boolean hasTestScope() {
        return true;
    }

    @Override
    public void validateModel(@NonNull VariantModel model){
        // No additional checks for ApplicationVariantFactory, so just return.
    }

    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(DEBUG);
        buildTypes.create(DEBUG);
        buildTypes.create(RELEASE);
    }
}
