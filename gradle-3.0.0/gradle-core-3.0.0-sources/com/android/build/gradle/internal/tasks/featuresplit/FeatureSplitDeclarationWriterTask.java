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

package com.android.build.gradle.internal.tasks.featuresplit;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import java.io.File;
import java.io.IOException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Task that writes the FeatureSplitDeclaration file and publish it for other modules to consume.
 */
public class FeatureSplitDeclarationWriterTask extends BaseTask {

    @Input String uniqueIdentifier;

    @OutputDirectory File outputDirectory;

    @TaskAction
    public void fullTaskAction() throws IOException {
        FeatureSplitDeclaration declaration = new FeatureSplitDeclaration(uniqueIdentifier);
        declaration.save(outputDirectory);
    }

    public static class ConfigAction
            implements TaskConfigAction<FeatureSplitDeclarationWriterTask> {

        @NonNull private final VariantScope variantScope;
        @NonNull private final File outputDirectory;

        public ConfigAction(@NonNull VariantScope variantScope, @NonNull File outputDirectory) {
            this.variantScope = variantScope;
            this.outputDirectory = outputDirectory;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("feature", "Writer");
        }

        @NonNull
        @Override
        public Class<FeatureSplitDeclarationWriterTask> getType() {
            return FeatureSplitDeclarationWriterTask.class;
        }

        @Override
        public void execute(@NonNull FeatureSplitDeclarationWriterTask task) {
            task.setVariantName(variantScope.getFullVariantName());
            task.uniqueIdentifier = variantScope.getGlobalScope().getProject().getPath();
            task.outputDirectory = outputDirectory;
        }
    }
}
