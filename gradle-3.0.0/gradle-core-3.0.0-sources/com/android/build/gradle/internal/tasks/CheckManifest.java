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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import java.io.File;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Class that checks the presence of the manifest. */
@CacheableTask
public class CheckManifest extends DefaultAndroidTask {

    private File manifest;
    private Boolean isOptional;
    private File fakeOutputDir;

    @Optional
    @Input // we don't care about the content, just that the file is there.
    public File getManifest() {
        return manifest;
    }

    @Input // force rerunning the task if the manifest shows up or disappears.
    public boolean getManifestPresence() {
        return manifest != null && manifest.isFile();
    }

    public void setManifest(@NonNull File manifest) {
        this.manifest = manifest;
    }

    @Input
    public Boolean getOptional() {
        return isOptional;
    }

    public void setOptional(Boolean optional) {
        isOptional = optional;
    }

    @OutputDirectory
    public File getFakeOutputDir() {
        return fakeOutputDir;
    }

    @TaskAction
    void check() {
        if (!isOptional && manifest != null && !manifest.isFile()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Main Manifest missing for variant %1$s. Expected path: %2$s",
                            getVariantName(), getManifest().getAbsolutePath()));
        }
    }

    public static class ConfigAction implements TaskConfigAction<CheckManifest> {

        private final VariantScope scope;
        private final boolean isManifestOptional;

        public ConfigAction(@NonNull VariantScope scope, boolean isManifestOptional) {
            this.scope = scope;
            this.isManifestOptional = isManifestOptional;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("check", "Manifest");
        }

        @NonNull
        @Override
        public Class<CheckManifest> getType() {
            return CheckManifest.class;
        }

        @Override
        public void execute(@NonNull CheckManifest checkManifestTask) {
            scope.getVariantData().checkManifestTask = checkManifestTask;
            checkManifestTask.setVariantName(
                    scope.getVariantData().getVariantConfiguration().getFullName());
            checkManifestTask.setOptional(isManifestOptional);
            checkManifestTask.manifest =
                    scope.getVariantData().getVariantConfiguration().getMainManifest();

            checkManifestTask.fakeOutputDir =
                    new File(
                            scope.getGlobalScope().getIntermediatesDir(),
                            "check-manifest/" + scope.getVariantConfiguration().getDirName());
        }
    }
}
