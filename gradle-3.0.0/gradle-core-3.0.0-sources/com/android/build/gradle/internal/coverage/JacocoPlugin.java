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

package com.android.build.gradle.internal.coverage;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.LoggerWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jacoco.core.JaCoCo;

/**
 * Jacoco plugin. This is very similar to the built-in support for Jacoco but we dup it in order
 * to control it as we need our own offline instrumentation.
 *
 * This may disappear if we can ever reuse the built-in support.
 *
 */
public class JacocoPlugin implements Plugin<Project> {
    public static final String ANT_CONFIGURATION_NAME = "androidJacocoAnt";
    public static final String AGENT_CONFIGURATION_NAME = "androidJacocoAgent";

    /** This version must be kept in sync with the version that the gradle plugin depends on. */
    @VisibleForTesting public static final String DEFAULT_JACOCO_VERSION = "0.7.4.201502262128";

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(JacocoPlugin.class);

    private Project project;

    @Nullable private String jacocoVersion;

    @Override
    public void apply(Project project) {
        this.project = project;
        addJacocoConfigurations();
    }

    @NonNull
    public String getAgentRuntimeDependency() {
        return "org.jacoco:org.jacoco.agent:" + getJacocoVersion() + ":runtime";
    }

    /**
     * Creates the configurations used by plugin.
     */
    private void addJacocoConfigurations() {
        Configuration config = this.project.getConfigurations().create(AGENT_CONFIGURATION_NAME);

        config.setVisible(false);
        config.setTransitive(true);
        config.setCanBeConsumed(false);
        config.setDescription("The Jacoco agent to use to get coverage data.");

        project.getDependencies().add(AGENT_CONFIGURATION_NAME, getAgentRuntimeDependency());

        config = this.project.getConfigurations().create(ANT_CONFIGURATION_NAME);

        config.setVisible(false);
        config.setTransitive(true);
        config.setCanBeConsumed(false);
        config.setDescription("The Jacoco ant tasks to use to get execute Gradle tasks.");

        project.getDependencies()
                .add(ANT_CONFIGURATION_NAME, "org.jacoco:org.jacoco.ant:" + getJacocoVersion());
    }

    @NonNull
    private String getJacocoVersion() {
        if (jacocoVersion != null) {
            return jacocoVersion;
        }
        // Version of Jacoco might not be the one AGP depends on, as it can be changed
        // by adding another classpath dependency. To get the actual runtime version, we
        // use Jacoco itself to extract info about its version.
        String pomFile = "META-INF/maven/org.jacoco/org.jacoco.core/pom.properties";
        try (InputStream in = JaCoCo.class.getClassLoader().getResourceAsStream(pomFile)) {
            if (in == null) {
                logger.warning(
                        "This is not a Jacoco maven jar. Using version %s.",
                        DEFAULT_JACOCO_VERSION);
                jacocoVersion = DEFAULT_JACOCO_VERSION;
                return jacocoVersion;
            }

            Properties properties = new Properties();
            properties.load(in);
            jacocoVersion = properties.getProperty("version", DEFAULT_JACOCO_VERSION);
        } catch (IOException e) {
            logger.warning("Loading properties failed. Using version %s.", DEFAULT_JACOCO_VERSION);
            jacocoVersion = DEFAULT_JACOCO_VERSION;
        }

        return jacocoVersion;
    }
}
