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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.scope.SplitList;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import joptsimple.internal.Strings;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Variant scoped task.
 *
 * <p>Task that will populate the {@link SplitList} variant scoped object keeping the list of pure
 * splits that should be built for this variant.
 *
 * <p>The task will also persist the list of splits in a gson file for consumption on subsequent
 * builds when there is no input changes to avoid rerunning it.
 */
@CacheableTask
public class SplitsDiscovery extends BaseTask {

    @Nullable FileCollection mergedResourcesFolders;
    Set<String> densityFilters;
    boolean densityAuto;
    Set<String> languageFilters;
    boolean languageAuto;
    Set<String> abiFilters;
    boolean resConfigAuto;
    Collection<String> resourceConfigs;

    @InputFiles
    @Optional
    @Nullable
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getMergedResourcesFolders() {
        return mergedResourcesFolders;
    }

    @Input
    @Optional
    Set<String> getDensityFilters() {
        return densityFilters;
    }

    @Input
    @Optional
    boolean isDensityAuto() {
        return densityAuto;
    }

    @Input
    @Optional
    Set<String> getLanguageFilters() {
        return languageFilters;
    }

    @Input
    @Optional
    boolean isLanguageAuto() {
        return languageAuto;
    }
    @Input
    @Optional
    Set<String> getAbiFilters() {
        return abiFilters;
    }

    @Input
    public boolean isResConfigAuto() {
        return resConfigAuto;
    }

    @Input
    Collection<String> getResourceConfigs() {
        return resourceConfigs;
    }

    File persistedList;

    @OutputFile
    File getPersistedList() {
        return persistedList;
    }

    @TaskAction
    void taskAction() throws IOException {

        Set<File> mergedResourcesFolderFiles =
                mergedResourcesFolders != null ? mergedResourcesFolders.getFiles() : null;
        Collection<String> resConfigs = resourceConfigs;
        if (resConfigAuto) {
            resConfigs = discoverListOfResourceConfigsNotDensities();
        }
        SplitList.save(
                getPersistedList(),
                getFilters(mergedResourcesFolderFiles, DiscoverableFilterType.DENSITY),
                getFilters(mergedResourcesFolderFiles, DiscoverableFilterType.LANGUAGE),
                // no need to pass the source folders, we don't support Auto for ABI splits so far.
                getFilters(ImmutableList.of(), DiscoverableFilterType.ABI),
                resConfigs);
    }

    /**
     * Gets the list of filter values for a filter type either from the user specified build.gradle
     * settings or through a discovery mechanism using folders names.
     *
     * @param resourceFolders the list of source folders to discover from.
     * @param filterType the filter type
     * @return a possibly empty list of filter value for this filter type.
     */
    @NonNull
    private Set<String> getFilters(
            @Nullable Iterable<File> resourceFolders, @NonNull DiscoverableFilterType filterType) {

        Set<String> filtersList = new HashSet<>();
        if (filterType.isAuto(this)) {
            Preconditions.checkNotNull(
                    resourceFolders,
                    "Merged resources must be supplied to perform automatic discovery of splits.");
            filtersList.addAll(getAllFilters(resourceFolders, filterType.folderPrefix));
        } else {
            filtersList.addAll(filterType.getConfiguredFilters(this));
        }
        return filtersList;
    }

    @NonNull
    public List<String> discoverListOfResourceConfigsNotDensities() {
        List<String> resFoldersOnDisk = new ArrayList<>();
        Preconditions.checkNotNull(
                mergedResourcesFolders,
                "Merged resources must be supplied to perform automatic discovery of resource configs.");
        resFoldersOnDisk.addAll(
                getAllFilters(
                        mergedResourcesFolders, DiscoverableFilterType.LANGUAGE.folderPrefix));
        return resFoldersOnDisk;
    }

    /**
     * Discover all sub-folders of all the resource folders which names are starting with one of the
     * provided prefixes.
     *
     * @param resourceFolders the list of resource folders
     * @param prefixes the list of prefixes to look for folders.
     * @return a possibly empty list of folders.
     */
    @NonNull
    private static List<String> getAllFilters(
            @NonNull Iterable<File> resourceFolders, @NonNull String... prefixes) {
        List<String> providedResFolders = new ArrayList<>();
        for (File resFolder : resourceFolders) {
            File[] subResFolders = resFolder.listFiles();
            if (subResFolders != null) {
                for (File subResFolder : subResFolders) {
                    for (String prefix : prefixes) {
                        if (subResFolder.getName().startsWith(prefix)) {
                            providedResFolders
                                    .add(getFilter(subResFolder, prefix));
                        }
                    }
                }
            }
        }
        return providedResFolders;
    }

    private static String getFilter(File file, String prefix) {
        // File can either be a resource directory (e.g. drawable-hdpi) if we're using AAPT1 or it
        // can be a .flat file (e.g. drawable-hdpi_ic_launcher.png.flat) if we're using AAPT2.
        if (file.isDirectory()) {
            return file.getName().substring(prefix.length());
        } else if (file.isFile() && file.getName().endsWith(".flat")) {
            // The format of AAPT2 compiled files is:
            // {@code <type>(-<filter1>)*_<original-name-of-file>(.arsc).flat}
            int firstUnderscore = file.getName().indexOf('_');
            if (firstUnderscore > -1) {
                return file.getName().substring(prefix.length(), firstUnderscore);
            }
        }
        return Strings.EMPTY;
    }

    /**
     * Defines the discoverability attributes of filters.
     */
    private enum DiscoverableFilterType {

        DENSITY("drawable-") {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull SplitsDiscovery task) {
                return task.getDensityFilters() != null
                        ? task.getDensityFilters()
                        : ImmutableList.of();
            }

            @Override
            boolean isAuto(@NonNull SplitsDiscovery task) {
                return task.isDensityAuto();
            }

        }, LANGUAGE("values-") {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull SplitsDiscovery task) {
                return task.getLanguageFilters() != null
                        ? task.getLanguageFilters()
                        : ImmutableList.of();
            }

            @Override
            boolean isAuto(@NonNull SplitsDiscovery task) {
                return task.isLanguageAuto();
            }
        }, ABI("") {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull SplitsDiscovery task) {
                return task.getAbiFilters() != null
                        ? task.getAbiFilters()
                        : ImmutableList.of();
            }

            @Override
            boolean isAuto(@NonNull SplitsDiscovery task) {
                // so far, we never auto-discover abi filters.
                return false;
            }
        };

        /** The folder prefix that filter specific resources must start with. */
        private final String folderPrefix;

        DiscoverableFilterType(String folderPrefix) {
            this.folderPrefix = folderPrefix;
        }

        /**
         * Returns the applicable filters configured in the build.gradle for this filter type.
         * @return a list of filters.
         */
        @NonNull
        abstract Collection<String> getConfiguredFilters(@NonNull SplitsDiscovery task);

        /**
         * Returns true if the user wants the build system to auto discover the splits for this
         * split type.
         * @return true to use auto-discovery, false to use the build.gradle configuration.
         */
        abstract boolean isAuto(@NonNull SplitsDiscovery task);
    }

    public static final class ConfigAction implements TaskConfigAction<SplitsDiscovery> {

        private final VariantScope variantScope;
        private final File persistedList;

        public ConfigAction(VariantScope variantScope, File persistedList) {
            this.variantScope = variantScope;
            this.persistedList = persistedList;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("splitsDiscoveryTask");
        }

        @NonNull
        @Override
        public Class<SplitsDiscovery> getType() {
            return SplitsDiscovery.class;
        }

        @Override
        public void execute(@NonNull SplitsDiscovery task) {
            task.setVariantName(variantScope.getFullVariantName());
            Splits splits = variantScope.getGlobalScope().getExtension().getSplits();
            task.persistedList = persistedList;
            if (splits.getDensity().isEnable()) {
                task.densityFilters = splits.getDensityFilters();
                task.densityAuto = splits.getDensity().isAuto();
            }
            if (splits.getLanguage().isEnable()) {
                task.languageFilters = splits.getLanguageFilters();
                task.languageAuto = splits.getLanguage().isAuto();
            }
            if (splits.getAbi().isEnable()) {
                task.abiFilters = splits.getAbiFilters();
            }

            task.resourceConfigs =
                    variantScope
                            .getVariantConfiguration()
                            .getMergedFlavor()
                            .getResourceConfigurations();

            task.resConfigAuto =
                    task.resourceConfigs.size() == 1
                            && Iterables.getOnlyElement(task.resourceConfigs).equals("auto");

            // Only consume the merged resources if auto is being used.
            if (task.densityAuto || task.languageAuto || task.resConfigAuto) {
                task.mergedResourcesFolders =
                        variantScope.getOutput(VariantScope.TaskOutputType.MERGED_RES);
            }
        }
    }
}
