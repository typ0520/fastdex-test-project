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

package com.android.build.gradle.internal.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_APP_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import java.io.File;
import java.io.IOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Task that writes the application-id file and publishes it. */
public class ApplicationIdWriterTask extends BaseTask {

    private String applicationId;

    private FileCollection packageManifest;

    private File outputDirectory;

    @Input
    public String getApplicationId() {
        return applicationId;
    }

    @InputFiles
    @Optional
    public FileCollection getPackageManifest() {
        return packageManifest;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    public void fullTaskAction() throws IOException {
        String packageId;
        if (packageManifest != null && !packageManifest.isEmpty()) {
            packageId = ApplicationId.load(packageManifest.getSingleFile()).getApplicationId();
        } else {
            packageId = applicationId;
        }
        ApplicationId declaration = new ApplicationId(packageId);
        declaration.save(outputDirectory);
    }

    public static class ConfigAction implements TaskConfigAction<ApplicationIdWriterTask> {

        @NonNull protected final VariantScope variantScope;
        @NonNull protected final File outputDirectory;

        public ConfigAction(@NonNull VariantScope variantScope, @NonNull File outputDirectory) {
            this.variantScope = variantScope;
            this.outputDirectory = outputDirectory;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("write", "ApplicationId");
        }

        @NonNull
        @Override
        public Class<ApplicationIdWriterTask> getType() {
            return ApplicationIdWriterTask.class;
        }

        @Override
        public void execute(@NonNull ApplicationIdWriterTask task) {
            task.setVariantName(variantScope.getFullVariantName());
            task.applicationId = variantScope.getVariantConfiguration().getApplicationId();
            task.outputDirectory = outputDirectory;
        }
    }

    public static class BaseFeatureConfigAction extends ConfigAction {

        public BaseFeatureConfigAction(
                @NonNull VariantScope variantScope, @NonNull File outputDirectory) {
            super(variantScope, outputDirectory);
        }

        @Override
        public void execute(@NonNull ApplicationIdWriterTask task) {
            super.execute(task);
            task.packageManifest =
                    variantScope.getArtifactFileCollection(
                            METADATA_VALUES, MODULE, METADATA_APP_ID_DECLARATION);
        }
    }
}
