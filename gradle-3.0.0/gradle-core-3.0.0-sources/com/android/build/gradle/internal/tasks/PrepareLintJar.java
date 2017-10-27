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
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.utils.FileUtils;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Task that takes the configuration result, and check that it's correct.
 *
 * <p>Then copies it in the build folder to (re)publish it. This is not super efficient but because
 * publishing is done at config time when we don't know yet what lint.jar file we're going to
 * publish, we have to do this.
 */
public class PrepareLintJar extends DefaultTask {
    public static final String NAME = "prepareLintJar";

    private FileCollection lintChecks;
    private File outputLintJar;

    @InputFiles
    public FileCollection getLintChecks() {
        return lintChecks;
    }

    @OutputFile
    public File getOutputLintJar() {
        return outputLintJar;
    }

    @TaskAction
    public void prepare() throws IOException {
        // there could be more than one files if the dependency is on a sub-projects that
        // publishes its compile dependencies. Rather than query getSingleFile and fail with
        // a weird message, do a manual check
        Set<File> files = lintChecks.getFiles();
        if (files.size() > 1) {
            throw new RuntimeException(
                    "Found more than one jar in the '"
                            + VariantDependencies.CONFIG_NAME_LINTCHECKS
                            + "' configuration. Only one file is supported. If using a separate Gradle project, make sure compilation dependencies are using compileOnly");
        }

        if (files.isEmpty()) {
            if (outputLintJar.isFile()) {
                FileUtils.delete(outputLintJar);
            }
        } else {
            FileUtils.mkdirs(outputLintJar.getParentFile());
            Files.copy(Iterables.getOnlyElement(files), outputLintJar);
        }
    }

    public static class ConfigAction implements TaskConfigAction<PrepareLintJar> {

        @NonNull private final GlobalScope scope;
        @NonNull private final File destFile;

        public ConfigAction(@NonNull GlobalScope scope, @NonNull File destFile) {
            this.scope = scope;
            this.destFile = destFile;
        }

        @NonNull
        @Override
        public String getName() {
            return NAME;
        }

        @NonNull
        @Override
        public Class<PrepareLintJar> getType() {
            return PrepareLintJar.class;
        }

        @Override
        public void execute(@NonNull PrepareLintJar task) {
            task.outputLintJar = destFile;
            task.lintChecks = scope.getLocalCustomLintChecks();
        }
    }
}
