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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;

/**
 * Singleton object per variant that holds the list of splits declared by the DSL or discovered.
 */
public class SplitList {

    public static final String RESOURCE_CONFIGS = "ResConfigs";

    /**
     * Split list cache, valid only during this build.
     */
    private ImmutableList<Record> records;

    public static SplitList EMPTY = new SplitList(ImmutableList.of());

    private SplitList(List<Record> records) {
        this.records = ImmutableList.copyOf(records);
    }

    public static SplitList load(FileCollection persistedList) throws IOException {
        String persistedData = FileUtils.readFileToString(persistedList.getSingleFile());
        Gson gson = new Gson();
        Type collectionType = new TypeToken<ArrayList<Record>>() {}.getType();
        return new SplitList(gson.fromJson(persistedData, collectionType));
    }

    public Set<String> getFilters(OutputFile.FilterType splitType) throws IOException {
        return getFilters(splitType.name());
    }

    public synchronized Set<String> getFilters(String filterType) throws IOException {
        Optional<Record> record =
                records.stream().filter(r -> r.splitType.equals(filterType)).findFirst();
        return record.isPresent()
                ? record.get().values
                : ImmutableSet.of();
    }

    public interface SplitAction {
        void apply(OutputFile.FilterType filterType, Set<String> value);
    }

    public void forEach(SplitAction action) throws IOException {
        records.forEach(
                record -> {
                    if (record.isConfigSplit() && !record.values.isEmpty()) {
                        action.apply(
                                OutputFile.FilterType.valueOf(record.splitType), record.values);
                    }
                });
    }

    public Set<String> getResourcesSplit() throws IOException {
        ImmutableSet.Builder<String> allFilters = ImmutableSet.builder();
        allFilters.addAll(getFilters(OutputFile.FilterType.DENSITY));
        allFilters.addAll(getFilters(OutputFile.FilterType.LANGUAGE));
        return allFilters.build();
    }

    @NonNull
    public static Set<String> getSplits(
            @NonNull SplitList splitList, @NonNull MultiOutputPolicy multiOutputPolicy)
            throws IOException {
        return multiOutputPolicy == MultiOutputPolicy.SPLITS
                ? splitList.getResourcesSplit()
                : ImmutableSet.of();
    }

    public static synchronized void save(
            @NonNull File outputFile,
            @NonNull Set<String> densityFilters,
            @NonNull Set<String> languageFilters,
            @NonNull Set<String> abiFilters,
            @NonNull Collection<String> resourceConfigs)
            throws IOException {

        ImmutableList<Record> records =
                ImmutableList.of(
                        new Record(OutputFile.FilterType.DENSITY.name(), densityFilters),
                        new Record(OutputFile.FilterType.LANGUAGE.name(), languageFilters),
                        new Record(OutputFile.FilterType.ABI.name(), abiFilters),
                        new Record(RESOURCE_CONFIGS, ImmutableSet.copyOf(resourceConfigs)));

        Gson gson = new Gson();
        String listOfFilters = gson.toJson(records);
        FileUtils.write(outputFile, listOfFilters);
    }

    /**
     * Internal records to save split names and types.
     */
    private static final class Record {
        private final String splitType;
        private final Set<String> values;

        private Record(String splitType, Set<String> values) {
            this.splitType = splitType;
            this.values = values;
        }

        private boolean isConfigSplit() {
            return !splitType.equals(RESOURCE_CONFIGS);
        }
    }
}
