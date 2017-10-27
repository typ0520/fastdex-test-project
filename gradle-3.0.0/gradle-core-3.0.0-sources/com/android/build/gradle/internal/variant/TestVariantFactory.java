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

package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_IMPLEMENTATION;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.internal.reflect.Instantiator;

/** Customization of {@link ApplicationVariantFactory} for test-only projects. */
public class TestVariantFactory extends ApplicationVariantFactory {

    public TestVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension) {
        super(globalScope, instantiator, androidBuilder, extension);
    }

    @Override
    public boolean hasTestScope() {
        return false;
    }

    @Override
    public void preVariantWork(final Project project) {
        super.preVariantWork(project);

        TestAndroidConfig testExtension = (TestAndroidConfig) extension;

        String path = testExtension.getTargetProjectPath();
        if (path == null) {
            throw new GradleException(
                    "targetProjectPath cannot be null in test project " + project.getName());
        }

        DependencyHandler handler = project.getDependencies();
        Map<String, String> projectNotation = ImmutableMap.of("path", path);
        handler.add(CONFIG_NAME_IMPLEMENTATION, handler.project(projectNotation));
    }

    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // don't call super as we don't want the default app version.
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(BuilderConstants.DEBUG);
        buildTypes.create(BuilderConstants.DEBUG);
    }
}
