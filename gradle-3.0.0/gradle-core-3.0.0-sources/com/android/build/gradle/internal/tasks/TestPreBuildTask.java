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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Pre build task that does some checks for application variants */
@CacheableTask
public class TestPreBuildTask extends DefaultAndroidTask {

    // list of Android only compile and runtime classpath.
    private ArtifactCollection testedRuntimeClasspath;
    private ArtifactCollection testRuntimeClasspath;
    // fake output dir so that the task doesn't run unless an input has changed.
    private File fakeOutputDirectory;

    // even though the files are jars, we don't care about changes to the files, only if files
    // are removed or added. We need to find a better way to declare this.
    @CompileClasspath
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getTestedRuntimeClasspath() {
        return testedRuntimeClasspath.getArtifactFiles();
    }

    // even though the files are jars, we don't care about changes to the files, only if files
    // are removed or added. We need to find a better way to declare this.
    @CompileClasspath
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getTestRuntimeClasspath() {
        return testRuntimeClasspath.getArtifactFiles();
    }

    @OutputDirectory
    public File getFakeOutputDirectory() {
        return fakeOutputDirectory;
    }

    @TaskAction
    void run() {
        Set<ResolvedArtifactResult> testedArtifacts = testedRuntimeClasspath.getArtifacts();
        Set<ResolvedArtifactResult> testArtifacts = testRuntimeClasspath.getArtifacts();

        // Store a map of groupId -> (artifactId -> versions)
        Map<String, Map<String, String>> testedIds =
                Maps.newHashMapWithExpectedSize(testedArtifacts.size());

        // build a list of the runtime artifacts
        for (ResolvedArtifactResult artifact : testedArtifacts) {
            // only care about external dependencies to compare versions.
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId =
                        (ModuleComponentIdentifier) componentIdentifier;

                // get the sub-map, creating it if needed.
                Map<String, String> subMap =
                        testedIds.computeIfAbsent(moduleId.getGroup(), s -> new HashMap<>());

                subMap.put(moduleId.getModule(), moduleId.getVersion());
            }
        }

        // run through the compile ones to check for provided only.
        for (ResolvedArtifactResult artifact : testArtifacts) {
            // only care about external dependencies to compare versions.
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId =
                        (ModuleComponentIdentifier) componentIdentifier;

                Map<String, String> subMap = testedIds.get(moduleId.getGroup());
                if (subMap != null) {
                    String testedVersion = subMap.get(moduleId.getModule());
                    if (testedVersion != null) {
                        if (!testedVersion.equals(moduleId.getVersion())) {
                            throw new GradleException(
                                    String.format(
                                            "Conflict with dependency '%s:%s' in project '%s'. Resolved versions for"
                                                    + " app (%s) and test app (%s) differ. See"
                                                    + " https://d.android.com/r/tools/test-apk-dependency-conflicts.html"
                                                    + " for details.",
                                            moduleId.getGroup(),
                                            moduleId.getModule(),
                                            getProject().getPath(),
                                            testedVersion,
                                            moduleId.getVersion()));
                        }
                    }
                }
            }
        }
    }

    public static class ConfigAction implements TaskConfigAction<TestPreBuildTask> {

        @NonNull private final VariantScope variantScope;

        public ConfigAction(@NonNull VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("pre", "Build");
        }

        @NonNull
        @Override
        public Class<TestPreBuildTask> getType() {
            return TestPreBuildTask.class;
        }

        @Override
        public void execute(@NonNull TestPreBuildTask task) {
            task.setVariantName(variantScope.getFullVariantName());

            task.testedRuntimeClasspath =
                    variantScope
                            .getTestedVariantData()
                            .getScope()
                            .getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, CLASSES);
            task.testRuntimeClasspath =
                    variantScope.getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, CLASSES);

            task.fakeOutputDirectory =
                    new File(
                            variantScope.getGlobalScope().getIntermediatesDir(),
                            "prebuild/" + variantScope.getVariantConfiguration().getDirName());

            variantScope.getVariantData().preBuildTask = task;
        }
    }
}
