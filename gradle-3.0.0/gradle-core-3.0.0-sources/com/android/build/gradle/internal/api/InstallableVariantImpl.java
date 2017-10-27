/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.InstallableVariant;
import com.android.build.gradle.internal.variant.InstallableVariantData;
import com.android.builder.core.AndroidBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

/**
 * Implementation of an installable variant.
 */
public abstract class InstallableVariantImpl extends AndroidArtifactVariantImpl implements InstallableVariant {

    protected InstallableVariantImpl(
            @NonNull ObjectFactory objectFactory,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ReadOnlyObjectProvider immutableObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(objectFactory, androidBuilder, immutableObjectProvider, outputs);
    }

    @NonNull
    @Override
    public abstract InstallableVariantData getVariantData();

    @Override
    public DefaultTask getInstall() {
        return getVariantData().installTask;
    }

    @Override
    public DefaultTask getUninstall() {
        return getVariantData().uninstallTask;
    }
}
