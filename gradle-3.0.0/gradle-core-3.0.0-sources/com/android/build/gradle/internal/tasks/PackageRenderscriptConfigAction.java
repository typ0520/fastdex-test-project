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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import java.io.File;
import org.gradle.api.tasks.Sync;

/** Configuration action for a package-renderscript task. */
public class PackageRenderscriptConfigAction implements TaskConfigAction<Sync> {

    @NonNull private VariantScope variantScope;
    private File destDir;

    public PackageRenderscriptConfigAction(
            @NonNull VariantScope variantScope,
            @NonNull File destDir) {
        this.variantScope = variantScope;
        this.destDir = destDir;
    }

    @NonNull
    @Override
    public String getName() {
        return variantScope.getTaskName("package", "Renderscript");
    }

    @NonNull
    @Override
    public Class<Sync> getType() {
        return Sync.class;
    }

    @Override
    public void execute(@NonNull Sync packageRenderscript) {
        // package from 3 sources. the order is important to make sure the override works well.
        packageRenderscript
                .from(variantScope.getVariantConfiguration().getRenderscriptSourceList())
                .include("**/*.rsh");
        packageRenderscript.into(destDir);
    }
}
