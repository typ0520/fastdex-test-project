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
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;

/** Task to write the FeatureSplitPackageIds file. */
public class FeatureSplitPackageIdsWriterTask extends BaseTask {

    FileCollection input;
    File outputDirectory;

    @InputFiles
    FileCollection getInput() {
        return input;
    }

    @OutputDirectory
    File getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    public void fullTaskAction() throws IOException {
        FeatureSplitPackageIds featureSplitPackageIds = new FeatureSplitPackageIds();
        for (File featureSplitDeclaration : input.getAsFileTree().getFiles()) {
            try {
                FeatureSplitDeclaration loaded =
                        FeatureSplitDeclaration.load(featureSplitDeclaration);
                featureSplitPackageIds.addFeatureSplit(loaded.getUniqueIdentifier());
            } catch (FileNotFoundException e) {
                throw new BuildException("Cannot read features split declaration file", e);
            }
        }

        // save the list.
        featureSplitPackageIds.save(outputDirectory);
    }

    public static class ConfigAction implements TaskConfigAction<FeatureSplitPackageIdsWriterTask> {

        @NonNull private final VariantScope variantScope;
        @NonNull private final File outputDirectory;

        public ConfigAction(@NonNull VariantScope variantScope, @NonNull File outputDirectory) {
            this.variantScope = variantScope;
            this.outputDirectory = outputDirectory;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("generate", "FeaturePackageIds");
        }

        @NonNull
        @Override
        public Class<FeatureSplitPackageIdsWriterTask> getType() {
            return FeatureSplitPackageIdsWriterTask.class;
        }

        @Override
        public void execute(@NonNull FeatureSplitPackageIdsWriterTask task) {
            task.setVariantName(variantScope.getFullVariantName());
            task.outputDirectory = outputDirectory;
            task.input =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.METADATA_FEATURE_DECLARATION);
        }
    }
}
