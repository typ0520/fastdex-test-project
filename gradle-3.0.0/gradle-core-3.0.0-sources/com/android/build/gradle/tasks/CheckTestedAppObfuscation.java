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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.CombinedInput;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import java.io.File;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Task to check if Proguard needs to be enabled for test plugin. */
public class CheckTestedAppObfuscation extends BaseTask {
    FileCollection mappingFile;

    @InputFiles
    public FileCollection getMappingFile() {
        return mappingFile;
    }

    /** Dummy output to allow up-to-date check */
    @SuppressWarnings("MethodMayBeStatic")
    @OutputFile
    @Optional
    public File getDummyOutput() {
        return null;
    }

    @TaskAction
    void checkIfAppIsObfuscated() {
        if (!mappingFile.isEmpty()) {
            throw new RuntimeException(
                    "Mapping file found in tested application. Proguard must also be enabled in "
                            + "test plugin with:\n"
                            + "android {\n"
                            + "    buildTypes {\n"
                            + "        "
                            + getVariantName()
                            + " {\n"
                            + "            minifyEnabled true\n"
                            + "            useProguard true\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n");
        }
    }

    public static class ConfigAction implements TaskConfigAction<CheckTestedAppObfuscation> {

        private final VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("checkTestedAppObfuscation");
        }

        @NonNull
        @Override
        public Class<CheckTestedAppObfuscation> getType() {
            return CheckTestedAppObfuscation.class;
        }

        @Override
        public void execute(@NonNull CheckTestedAppObfuscation task) {
            task.setVariantName(scope.getFullVariantName());

            task.mappingFile =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.APK_MAPPING);
        }
    }

    // Workaround for https://issuetracker.google.com/67418335
    @Input
    @NonNull
    public String getCombinedInput() {
        return new CombinedInput().add("dummyOutput", getDummyOutput()).toString();
    }
}
