/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.CombinedInput;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.io.File;
import java.util.Map;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;

/**
 * A task that processes the manifest
 */
public abstract class ManifestProcessorTask extends IncrementalTask {

    private File manifestOutputDirectory;

    private File aaptFriendlyManifestOutputDirectory;

    private File instantRunManifestOutputDirectory;

    private File reportFile;

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @Nullable
    @Internal
    public abstract File getAaptFriendlyManifestOutputFile();

    /** The processed Manifest. */
    @OutputDirectory
    public File getManifestOutputDirectory() {
        return manifestOutputDirectory;
    }

    public void setManifestOutputDirectory(File manifestOutputFolder) {
        this.manifestOutputDirectory = manifestOutputFolder;
    }

    @OutputDirectory
    @Optional
    public File getInstantRunManifestOutputDirectory() {
        return instantRunManifestOutputDirectory;
    }

    public void setInstantRunManifestOutputDirectory(File instantRunManifestOutputDirectory) {
        this.instantRunManifestOutputDirectory = instantRunManifestOutputDirectory;
    }

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @OutputDirectory
    @Optional
    public File getAaptFriendlyManifestOutputDirectory() {
        return aaptFriendlyManifestOutputDirectory;
    }

    public void setAaptFriendlyManifestOutputDirectory(File aaptFriendlyManifestOutputDirectory) {
        this.aaptFriendlyManifestOutputDirectory = aaptFriendlyManifestOutputDirectory;
    }

    @OutputFile
    @Optional
    public File getReportFile() {
        return reportFile;
    }

    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }


    /**
     * Serialize a map key+value pairs into a comma separated list. Map elements are sorted to
     * ensure stability between instances.
     *
     * @param mapToSerialize the map to serialize.
     */
    protected static String serializeMap(Map<String, Object> mapToSerialize) {
        final Joiner keyValueJoiner = Joiner.on(":");
        // transform the map on a list of key:value items, sort it and concatenate it.
        return Joiner.on(",").join(
                Ordering.natural().sortedCopy(Iterables.transform(
                        mapToSerialize.entrySet(),
                        (input) -> keyValueJoiner.join(input.getKey(), input.getValue()))));
    }

    /**
     * Backward compatibility support. This method used to be available on AGP prior to 3.0 but has
     * now been replaced with {@link #getManifestOutputDirectory()}.
     *
     * @return
     * @deprecated As or release 3.0, replaced with {@link #getManifestOutputDirectory()}
     */
    @Deprecated
    @Internal
    public File getManifestOutputFile() {
        throw new RuntimeException(
                "Manifest Tasks does not support the manifestOutputFile property any more, please"
                        + " use the manifestOutputDirectory instead.\nFor more information, please check "
                        + "https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html");
    }

    // Workaround for https://issuetracker.google.com/67418335
    @Override
    @Input
    @NonNull
    public String getCombinedInput() {
        return new CombinedInput(super.getCombinedInput())
                .add("instantRunManifestOutputDirectory", getInstantRunManifestOutputDirectory())
                .add(
                        "aaptFriendlyManifestOutputDirectory",
                        getAaptFriendlyManifestOutputDirectory())
                .add("reportFile", getReportFile())
                .toString();
    }
}
