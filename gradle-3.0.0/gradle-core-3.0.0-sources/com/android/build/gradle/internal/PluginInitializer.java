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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.api.AndroidBasePlugin;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.Version;
import com.android.ide.common.util.JvmWideVariable;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.reflect.TypeToken;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.Project;

/**
 * Helper class to perform a few initializations when the plugin is applied to a project.
 *
 * <p>To ensure proper usage, the {@link #initialize(Project, ProjectOptions)} method must be called
 * immediately whenever the plugin is applied to a project.
 */
@ThreadSafe
public final class PluginInitializer {

    /**
     * Map from a project instance to the plugin version that is applied to the project, used to
     * detect if different plugin versions are applied.
     *
     * <p>We use the project instance instead of the project path as the key because, within a
     * build, Gradle might apply the plugin multiple times to different project instances having the
     * same project path. Using project instances as keys helps us tracks this information better.
     *
     * <p>This map will be reset at the end of every build since the scope of the check is per
     * build.
     */
    @NonNull
    private static final ConcurrentMap<Object, String> projectToPluginVersionMap =
            Verify.verifyNotNull(
                    // IMPORTANT: This variable's group, name, and type must not be changed across
                    // plugin versions.
                    new JvmWideVariable<>(
                                    "PLUGIN_VERSION_CHECK",
                                    "PROJECT_TO_PLUGIN_VERSION",
                                    new TypeToken<ConcurrentMap<Object, String>>() {},
                                    ConcurrentHashMap::new)
                            .get());

    /**
     * Reference to the loaded plugin class, used to detect if the plugin is loaded more than once.
     *
     * <p>This reference will be reset at the end of every build since the scope of the check is per
     * build.
     */
    @NonNull
    private static final AtomicReference<Class<?>> loadedPluginClass =
            Verify.verifyNotNull(
                    new JvmWideVariable<>(
                                    PluginInitializer.class.getName(),
                                    "loadedPluginClass",
                                    Version.ANDROID_GRADLE_PLUGIN_VERSION,
                                    new TypeToken<AtomicReference<Class<?>>>() {},
                                    () -> new AtomicReference<>(null))
                            .get());

    /**
     * Performs a few initializations when the plugin is applied to a project. This method must be
     * called immediately whenever the plugin is applied to a project.
     *
     * <p>Currently, the initialization includes:
     *
     * <ol>
     *   <li>Notifying the {@link BuildSessionImpl} singleton object that a new build has started,
     *       as required by that class.
     *   <li>Checking that the same plugin version is applied within a build.
     *   <li>Checking that the plugin is loaded only once within a build (the plugin may be applied
     *       more than once but the plugin's classes must be loaded only once).
     * </ol>
     *
     * <p>Here, a build refers to the entire Gradle build, which includes included builds in the
     * case of composite builds. Note that the Gradle daemon never executes two builds at the same
     * time, although it may execute sub-builds (for sub-projects) or included builds in parallel.
     *
     * <p>The scope of the above plugin checks is per build. It is okay that different plugin
     * versions are applied or the plugin is reloaded across different builds.
     *
     * @param project the project that the plugin is applied to
     * @param projectOptions the options of the project
     * @throws IllegalStateException if any of the plugin checks failed
     */
    public static void initialize(
            @NonNull Project project, @NonNull ProjectOptions projectOptions) {
        // Notifying the BuildSessionImpl singleton object must be done first
        BuildSessionImpl.getSingleton().initialize(project.getGradle());

        // The scope of the plugin checks is per build, so we need to reset the variables for these
        // checks at the end of every build. We register the action early in case the code that
        // follows throws an exception.
        BuildSessionImpl.getSingleton()
                .executeOnceWhenBuildFinished(
                        PluginInitializer.class.getName(),
                        "resetPluginCheckVariables",
                        () -> {
                            projectToPluginVersionMap.clear();
                            loadedPluginClass.set(null);
                        });

        // Check that the same plugin version is applied (the code is synchronized on the shared map
        // to make the method call thread safe across class loaders)
        synchronized (projectToPluginVersionMap) {
            verifySamePluginVersion(
                    projectToPluginVersionMap, project, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        }

        // Check that the plugin is loaded only once (no need to use "synchronized" since the
        // method's implementation is already thread safe across class loaders)
        verifyPluginLoadedOnce(
                loadedPluginClass,
                AndroidBasePlugin.class,
                projectOptions.get(BooleanOption.ENABLE_BUILDSCRIPT_CLASSPATH_CHECK));
    }

    /** Verifies that the same plugin version is applied. */
    @VisibleForTesting
    static void verifySamePluginVersion(
            @NonNull ConcurrentMap<Object, String> projectToPluginVersionMap,
            @NonNull Project project,
            @NonNull String pluginVersion) {
        Preconditions.checkState(
                !projectToPluginVersionMap.containsKey(project),
                String.format(
                        "Android Gradle plugin %1$s must not be applied to project '%2$s'"
                                + " since version %3$s was already applied to this project",
                        pluginVersion,
                        project.getProjectDir().getAbsolutePath(),
                        projectToPluginVersionMap.get(project)));

        projectToPluginVersionMap.put(project, pluginVersion);

        if (projectToPluginVersionMap.values().stream().distinct().count() > 1) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append(
                    "Using multiple versions of the Android Gradle plugin in the same build"
                            + " is not allowed.");
            for (Map.Entry<Object, String> entry : projectToPluginVersionMap.entrySet()) {
                Preconditions.checkState(
                        entry.getKey() instanceof Project,
                        Project.class + " should be loaded only once");
                Project fromProject = (Project) entry.getKey();
                String toPluginVersion = entry.getValue();
                errorMessage.append(
                        String.format(
                                "\n\t'%1$s' is using version %2$s",
                                fromProject.getProjectDir().getAbsolutePath(), toPluginVersion));
            }
            throw new IllegalStateException(errorMessage.toString());
        }
    }

    /** Verifies that the plugin is loaded only once. */
    @VisibleForTesting
    static void verifyPluginLoadedOnce(
            @NonNull AtomicReference<Class<?>> loadedPluginClass,
            @NonNull Class<?> pluginClass,
            boolean checkEnabled) {
        loadedPluginClass.compareAndSet(null, pluginClass);

        if (checkEnabled && pluginClass != loadedPluginClass.get()) {
            throw new IllegalStateException(
                    "Due to a limitation of Gradle’s new variant-aware dependency management, loading the Android Gradle plugin in different class loaders leads to a build error.\n"
                            + "This can occur when the buildscript classpaths that contain the Android Gradle plugin in sub-projects, or included projects in the case of composite builds, are set differently.\n"
                            + "To resolve this issue, add the Android Gradle plugin to only the buildscript classpath of the top-level build.gradle file.\n"
                            + "In the case of composite builds, also make sure the build script classpaths that contain the Android Gradle plugin are identical across the main and included projects.\n"
                            + "If you are using a version of Gradle that has fixed the issue, you can disable this check by setting android.enableBuildScriptClasspathCheck=false in the gradle.properties file.\n"
                            + "To learn more about this issue, go to https://d.android.com/r/tools/buildscript-classpath-check.html.");
        }
    }
}
