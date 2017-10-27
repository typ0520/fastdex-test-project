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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.VariantOutput;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.ide.common.build.ApkData;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.reflect.Instantiator;

/**
 * Factory for the {@link BaseVariantOutput} for each variant output that will be added to the
 * public API
 */
public class VariantOutputFactory {

    @NonNull private final Class<? extends BaseVariantOutput> targetClass;
    @NonNull private final Instantiator instantiator;
    @Nullable private final BaseVariantImpl variantPublicApi;
    @NonNull private final TaskContainer taskContainer;
    @NonNull private final AndroidConfig androidConfig;

    public VariantOutputFactory(
            @NonNull Class<? extends BaseVariantOutput> targetClass,
            @NonNull Instantiator instantiator,
            @NonNull AndroidConfig androidConfig,
            @Nullable BaseVariantImpl variantPublicApi,
            @NonNull TaskContainer taskContainer) {
        this.targetClass = targetClass;
        this.instantiator = instantiator;
        this.variantPublicApi = variantPublicApi;
        this.taskContainer = taskContainer;
        this.androidConfig = androidConfig;
    }

    public VariantOutput create(ApkData apkData) {
        BaseVariantOutput variantOutput =
                instantiator.newInstance(targetClass, apkData, taskContainer);
        androidConfig.getBuildOutputs().add(variantOutput);
        if (variantPublicApi != null) {
            variantPublicApi.addOutputs(ImmutableList.of(variantOutput));
        }
        return variantOutput;
    }
}
