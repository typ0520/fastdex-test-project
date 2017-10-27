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
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Container for all the feature split related package ids. */
public class FeatureSplitPackageIds {

    @VisibleForTesting static final String OUTPUT_FILE_NAME = "package_ids.json";
    @VisibleForTesting public static final int BASE_ID = 0x80;

    private final Set<SplitPackageId> featureSplits;

    public FeatureSplitPackageIds() {
        featureSplits = new HashSet<>();
    }

    private FeatureSplitPackageIds(Set<SplitPackageId> featureSplits) {
        this.featureSplits = featureSplits;
    }

    public synchronized void addFeatureSplit(@NonNull String featureSplit) {
        featureSplits.add(new SplitPackageId(featureSplit, BASE_ID + featureSplits.size()));
    }

    @Nullable
    public Integer getIdFor(@NonNull String featureSplit) {
        Optional<SplitPackageId> splitPacakgeId =
                featureSplits
                        .stream()
                        .filter(
                                splitPackageId ->
                                        splitPackageId.splitIdentifier.equals(featureSplit))
                        .findFirst();
        return splitPacakgeId.isPresent() ? splitPacakgeId.get().id : null;
    }

    public void save(@NonNull File outputDirectory) throws IOException {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        File outputFile = getOutputFile(outputDirectory);
        Files.write(gson.toJson(featureSplits), outputFile, Charsets.UTF_8);
    }

    @NonNull
    public static FeatureSplitPackageIds load(@NonNull Set<File> files) throws IOException {
        File outputFile = getOutputFile(files);
        if (outputFile == null) {
            throw new FileNotFoundException("Cannot find package ids json file");
        }
        return load(outputFile);
    }

    @NonNull
    public static FeatureSplitPackageIds load(@NonNull File input) throws IOException {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        Type typeToken = new TypeToken<HashSet<SplitPackageId>>() {}.getType();
        try (FileReader fileReader = new FileReader(input)) {
            Set<SplitPackageId> featureIds = gson.fromJson(fileReader, typeToken);
            return new FeatureSplitPackageIds(featureIds);
        }
    }

    @NonNull
    public static File getOutputFile(@NonNull File outputDirectory) {
        return new File(outputDirectory, OUTPUT_FILE_NAME);
    }

    @Nullable
    public static File getOutputFile(@NonNull Set<File> files) {
        for (File file : files) {
            if (file.getName().equals(OUTPUT_FILE_NAME)) {
                return file;
            }
        }
        return null;
    }


    private static class SplitPackageId {
        final String splitIdentifier;
        final int id;

        SplitPackageId(String splitIdentifier, int id) {
            this.splitIdentifier = splitIdentifier;
            this.id = id;
        }
    }
}
