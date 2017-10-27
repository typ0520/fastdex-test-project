/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

/**
 * Version of TransformStream handling outputs of transforms.
 */
class IntermediateStream extends TransformStream {

    static Builder builder(@NonNull Project project, @NonNull String name) {
        return new Builder(project, name);
    }

    static final class Builder {

        @NonNull private final Project project;
        @NonNull private final String name;
        private Set<ContentType> contentTypes = Sets.newHashSet();
        private Set<QualifiedContent.ScopeType> scopes = Sets.newHashSet();
        private File rootLocation;
        private String taskName;

        public Builder(@NonNull Project project, @NonNull String name) {
            this.project = project;
            this.name = name;
        }

        public IntermediateStream build() {
            Preconditions.checkNotNull(rootLocation);
            Preconditions.checkNotNull(taskName);
            Preconditions.checkState(!contentTypes.isEmpty());
            Preconditions.checkState(!scopes.isEmpty());

            // create a file collection with the files and the dependencies.
            FileCollection fileCollection = project.files(rootLocation).builtBy(taskName);

            return new IntermediateStream(
                    name,
                    ImmutableSet.copyOf(contentTypes),
                    ImmutableSet.copyOf(scopes),
                    fileCollection);
        }

        Builder addContentTypes(@NonNull Set<ContentType> types) {
            this.contentTypes.addAll(types);
            return this;
        }

        Builder addContentTypes(@NonNull ContentType... types) {
            this.contentTypes.addAll(Arrays.asList(types));
            return this;
        }

        Builder addScopes(@NonNull Set<? super Scope> scopes) {
            for (Object scope : scopes) {
                this.scopes.add((QualifiedContent.ScopeType) scope);
            }
            return this;
        }

        Builder addScopes(@NonNull Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        Builder setRootLocation(@NonNull final File rootLocation) {
            this.rootLocation = rootLocation;
            return this;
        }

        Builder setTaskName(@NonNull String taskName) {
            this.taskName = taskName;
            return this;
        }
    }

    private IntermediateStream(
            @NonNull String name,
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes,
            @NonNull FileCollection fileCollection) {
        super(name, contentTypes, scopes, fileCollection);
    }

    private IntermediateFolderUtils folderUtils = null;

    /**
     * Returns the files that make up the streams. The callable allows for resolving this lazily.
     */
    @NonNull
    File getRootLocation() {
        return getFileCollection().getSingleFile();
    }

    /**
     * Returns a new view of this content as a {@link TransformOutputProvider}.
     */
    @NonNull
    TransformOutputProvider asOutput() {
        init();
        return new TransformOutputProviderImpl(folderUtils);
    }

    void save() throws IOException {
        folderUtils.save();
    }

    @NonNull
    @Override
    TransformInput asNonIncrementalInput() {
        init();
        return folderUtils.computeNonIncrementalInputFromFolder();
    }

    @NonNull
    @Override
    IncrementalTransformInput asIncrementalInput() {
        init();
        return folderUtils.computeIncrementalInputFromFolder();
    }

    @NonNull
    @Override
    TransformStream makeRestrictedCopy(
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes) {
        return new IntermediateStream(
                getName() + "-restricted-copy", types, scopes, getFileCollection());
    }

    @Override
    @NonNull
    FileCollection getOutputFileCollection(@NonNull Project project, @NonNull StreamFilter streamFilter) {
        // create a collection that only returns the requested content type/scope,
        // and contain the dependency information.

        // the collection inside this type of stream cannot be used as is. This is because it
        // contains the root location rather that the actual inputs of the stream. Therefore
        // we need to go through them and create a single collection that contains the actual
        // inputs.
        // However the content of the intermediate root folder isn't known at configuration
        // time so we need to pass a callable that will compute the files dynamically.
        Supplier<Collection<File>> supplier =
                () -> {
                    init();
                    return folderUtils.getFiles(streamFilter);
                };

        return project.files(TaskInputHelper.bypassFileCallable(supplier))
                .builtBy(getFileCollection().getBuildDependencies());
    }

    private void init() {
        if (folderUtils == null) {
            folderUtils =
                    new IntermediateFolderUtils(getRootLocation(), getContentTypes(), getScopes());
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scopes", getScopes())
                .add("contentTypes", getContentTypes())
                .add("fileCollection", getFileCollection())
                .toString();
    }
}
