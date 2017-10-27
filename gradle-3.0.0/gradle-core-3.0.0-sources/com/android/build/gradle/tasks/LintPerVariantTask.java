/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.utils.StringHelper;
import java.io.IOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public class LintPerVariantTask extends LintBaseTask {

    private VariantInputs variantInputs;

    @InputFiles
    @Optional
    public FileCollection getVariantInputs() {
        return variantInputs.getAllInputs();
    }

    @TaskAction
    public void lint() throws IOException {
        AndroidProject modelProject = createAndroidProject(getProject());
        for (Variant variant : modelProject.getVariants()) {
            if (variant.getName().equals(getVariantName())) {
                lintSingleVariant(modelProject, variant);
                break;
            }
        }
    }

    /** Runs lint on a single specified variant */
    public void lintSingleVariant(@NonNull AndroidProject modelProject, @NonNull Variant variant) {
        runLint(modelProject, variant, variantInputs, true);
    }

    public static class ConfigAction extends BaseConfigAction<LintPerVariantTask> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            super(scope.getGlobalScope());
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("lint");
        }

        @Override
        @NonNull
        public Class<LintPerVariantTask> getType() {
            return LintPerVariantTask.class;
        }

        @Override
        public void execute(@NonNull LintPerVariantTask lint) {
            super.execute(lint);

            lint.setVariantName(scope.getVariantConfiguration().getFullName());

            lint.variantInputs = new VariantInputs(scope);

            lint.setDescription(
                    "Runs lint on the "
                            + StringHelper.capitalize(scope.getVariantConfiguration().getFullName())
                            + " build.");
        }
    }

    public static class VitalConfigAction extends BaseConfigAction<LintPerVariantTask> {

        private final VariantScope scope;

        public VitalConfigAction(@NonNull VariantScope scope) {
            super(scope.getGlobalScope());
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("lintVital");
        }

        @NonNull
        @Override
        public Class<LintPerVariantTask> getType() {
            return LintPerVariantTask.class;
        }

        @Override
        public void execute(@NonNull LintPerVariantTask task) {
            super.execute(task);

            String variantName = scope.getVariantData().getVariantConfiguration().getFullName();
            task.setVariantName(variantName);

            task.variantInputs = new VariantInputs(scope);

            task.setFatalOnly(true);
            task.setDescription(
                    "Runs lint on just the fatal issues in the " + variantName + " build.");
        }
    }
}
