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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.packaging.JarMerger;
import com.android.builder.packaging.ZipAbortException;
import com.android.builder.packaging.ZipEntryFilter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public abstract class LibraryBaseTransform extends Transform {

    /**
     * Convenient way to attach exclude list providers that can provide their list at the end of
     * the build.
     */
    public interface ExcludeListProvider {
        @Nullable List<String> getExcludeList();
    }

    @NonNull
    protected final File mainClassLocation;
    @Nullable
    protected final File localJarsLocation;
    @NonNull
    protected final String packagePath;
    protected final boolean packageBuildConfig;
    @Nullable
    protected final File typedefRecipe;

    @Nullable
    protected List<ExcludeListProvider> excludeListProviders;

    public LibraryBaseTransform(
            @NonNull File mainClassLocation,
            @Nullable File localJarsLocation,
            @Nullable File typedefRecipe,
            @NonNull String packageName,
            boolean packageBuildConfig) {
        this.mainClassLocation = mainClassLocation;
        this.localJarsLocation = localJarsLocation;
        this.typedefRecipe = typedefRecipe;
        this.packagePath = packageName.replace(".", "/");
        this.packageBuildConfig = packageBuildConfig;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        if (typedefRecipe != null) {
            return ImmutableList.of(SecondaryFile.nonIncremental(typedefRecipe));
        } else {
            return ImmutableList.of();
        }
    }

    public void addExcludeListProvider(@NonNull ExcludeListProvider provider) {
        if (excludeListProviders == null) {
            excludeListProviders = Lists.newArrayList();
        }
        excludeListProviders.add(provider);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_JARS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<? super Scope> getReferencedScopes() {
        return TransformManager.PROJECT_ONLY;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        if (localJarsLocation == null) {
            return ImmutableList.of();
        }
        return ImmutableList.of(localJarsLocation);
    }

    @NonNull
    protected List<Pattern> computeExcludeList() {
        List<String> excludes = Lists.newArrayListWithExpectedSize(5);

        // these must be regexp to match the zip entries
        excludes.add(".*/R.class$");
        excludes.add(".*/R\\$(.*).class$");
        excludes.add(packagePath + "/Manifest.class$");
        excludes.add(packagePath + "/Manifest\\$(.*).class$");
        if (!packageBuildConfig) {
            excludes.add(packagePath + "/BuildConfig.class$");
        }
        if (excludeListProviders != null) {
            for (ExcludeListProvider provider : excludeListProviders) {
                List<String> list = provider.getExcludeList();
                if (list != null) {
                    excludes.addAll(list);
                }
            }
        }

        // create Pattern Objects.
        return excludes.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    protected void processLocalJars(@NonNull List<QualifiedContent> qualifiedContentList)
            throws IOException {

        // first copy the jars (almost) as is, and remove them from the list.
        // then we'll make a single jars that contains all the folders (though it's unlikely to
        // happen)
        // Note that we do need to remove the resources from the jars since they have been merged
        // somewhere else.
        // TODO: maybe do the folders separately to handle incremental?

        Iterator<QualifiedContent> iterator = qualifiedContentList.iterator();

        while (iterator.hasNext()) {
            QualifiedContent content = iterator.next();
            if (content instanceof JarInput) {
                // we need to copy the jars but only take the class files as the resources have
                // been merged into the main jar.
                copyJarWithContentFilter(
                        content.getFile(),
                        new File(localJarsLocation, content.getFile().getName()),
                        ZipEntryFilter.CLASSES_ONLY);
                iterator.remove();
            }
        }

        // now handle the folders.
        if (!qualifiedContentList.isEmpty()) {
            try (JarMerger jarMerger =
                    new JarMerger(
                            new File(localJarsLocation, "otherclasses.jar").toPath(),
                            ZipEntryFilter.CLASSES_ONLY)) {
                for (QualifiedContent content : qualifiedContentList) {
                    jarMerger.addDirectory(content.getFile().toPath());
                }
            }
        }
    }

    protected static void copyJarWithContentFilter(
            @NonNull File from,
            @NonNull File to,
            @NonNull final List<Pattern> excludes) throws IOException {
        copyJarWithContentFilter(from, to, archivePath -> checkEntry(excludes, archivePath));
    }

    protected static void copyJarWithContentFilter(
            @NonNull File from,
            @NonNull File to,
            @Nullable ZipEntryFilter filter) throws IOException {
        byte[] buffer = new byte[4096];

        try (Closer closer = Closer.create()) {
            FileOutputStream fos = closer.register(new FileOutputStream(to));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            ZipOutputStream zos = closer.register(new ZipOutputStream(bos));

            FileInputStream fis = closer.register(new FileInputStream(from));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            ZipInputStream zis = closer.register(new ZipInputStream(bis));

            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (entry.isDirectory() || (filter != null && !filter.checkEntry(name))) {
                    continue;
                }

                JarEntry newEntry;

                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }

                newEntry.setLastModifiedTime(JarMerger.ZERO_TIME);
                newEntry.setLastAccessTime(JarMerger.ZERO_TIME);
                newEntry.setCreationTime(JarMerger.ZERO_TIME);

                // add the entry to the jar archive
                zos.putNextEntry(newEntry);

                // read the content of the entry from the input stream, and write it into the archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    zos.write(buffer, 0, count);
                }

                zos.closeEntry();
                zis.closeEntry();
            }
        } catch (ZipAbortException e) {
            throw new IOException(e);
        }
    }

    protected static boolean checkEntry(
            @NonNull List<Pattern> patterns,
            @NonNull String name) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }

    protected static void jarFolderToLocation(
            @NonNull File fromFolder,
            @NonNull File toFile,
            @Nullable ZipEntryFilter filter,
            @Nullable JarMerger.Transformer typedefRemover)
            throws IOException {
        try (JarMerger jarMerger = new JarMerger(toFile.toPath())) {
            jarMerger.addDirectory(fromFolder.toPath(), filter, typedefRemover);
        }
    }

    protected static void mergeInputsToLocation(
            @NonNull List<QualifiedContent> qualifiedContentList,
            @NonNull File toFile,
            boolean forIntermediateJar,
            @Nullable final ZipEntryFilter filter,
            @Nullable final JarMerger.Transformer typedefRemover)
            throws IOException {
        ZipEntryFilter filterAndOnlyClasses = ZipEntryFilter.CLASSES_ONLY.and(filter);

        try (JarMerger jarMerger = new JarMerger(toFile.toPath())) {
            for (QualifiedContent content : qualifiedContentList) {
                // merge only class files if RESOURCES are not in the scope, unless
                // merging into an intermediate jar: in that case we want to merge
                // meta-inf files even if the only content type is CLASSES
                boolean hasResources =
                        content.getContentTypes()
                                .contains(QualifiedContent.DefaultContentType.RESOURCES);
                ZipEntryFilter thisFilter =
                        hasResources || forIntermediateJar ? filter : filterAndOnlyClasses;
                if (content instanceof JarInput) {
                    jarMerger.addJar(content.getFile().toPath(), thisFilter);
                } else {
                    jarMerger.addDirectory(content.getFile().toPath(), thisFilter, typedefRemover);
                }
            }
        }
    }
}
