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

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES;
import static com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES;
import static com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS;
import static com.android.utils.StringHelper.capitalize;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.InternalScope;
import com.android.build.gradle.internal.TaskFactory;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Manages the transforms for a variant.
 *
 * <p>The actual execution is handled by Gradle through the tasks.
 * Instead it's a means to more easily configure a series of transforms that consume each other's
 * inputs when several of these transform are optional.
 */
public class TransformManager extends FilterableStreamCollection {

    private static final boolean DEBUG = true;

    private static final String FD_TRANSFORMS = "transforms";

    public static final Set<Scope> EMPTY_SCOPES = ImmutableSet.of();

    public static final Set<ContentType> CONTENT_CLASS = ImmutableSet.of(CLASSES);
    public static final Set<ContentType> CONTENT_JARS = ImmutableSet.of(CLASSES, RESOURCES);
    public static final Set<ContentType> CONTENT_RESOURCES = ImmutableSet.of(RESOURCES);
    public static final Set<ContentType> CONTENT_NATIVE_LIBS =
            ImmutableSet.of(NATIVE_LIBS);
    public static final Set<ContentType> CONTENT_DEX = ImmutableSet.of(ExtendedContentType.DEX);
    public static final Set<ContentType> DATA_BINDING_ARTIFACT =
            ImmutableSet.of(ExtendedContentType.DATA_BINDING);
    public static final Set<ScopeType> PROJECT_ONLY = ImmutableSet.of(Scope.PROJECT);
    public static final Set<Scope> SCOPE_FULL_PROJECT =
            Sets.immutableEnumSet(
                    Scope.PROJECT,
                    Scope.SUB_PROJECTS,
                    Scope.EXTERNAL_LIBRARIES);
    public static final Set<ScopeType> SCOPE_FULL_WITH_IR_FOR_DEXING =
            new ImmutableSet.Builder<ScopeType>()
                    .addAll(SCOPE_FULL_PROJECT)
                    .add(InternalScope.MAIN_SPLIT)
                    .build();
    public static final Set<ScopeType> SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS =
            ImmutableSet.of(Scope.PROJECT, InternalScope.LOCAL_DEPS);

    @NonNull
    private final Project project;
    @NonNull
    private final AndroidTaskRegistry taskRegistry;
    @NonNull
    private final ErrorReporter errorReporter;
    @NonNull
    private final Logger logger;
    @NonNull
    private final Recorder recorder;

    /**
     * These are the streams that are available for new Transforms to consume.
     *
     * <p>Once a new transform is added, the streams that it consumes are removed from this list,
     * and the streams it produces are put instead.
     *
     * <p>When all the transforms have been added, the remaining streams should be consumed by
     * standard Tasks somehow.
     *
     * @see #getStreams(StreamFilter)
     */
    @NonNull private final List<TransformStream> streams = Lists.newArrayList();
    @NonNull
    private final List<Transform> transforms = Lists.newArrayList();

    public TransformManager(
            @NonNull Project project,
            @NonNull AndroidTaskRegistry taskRegistry,
            @NonNull ErrorReporter errorReporter,
            @NonNull Recorder recorder) {
        this.project = project;
        this.taskRegistry = taskRegistry;
        this.errorReporter = errorReporter;
        this.recorder = recorder;
        this.logger = Logging.getLogger(TransformManager.class);
    }

    @NonNull
    public AndroidTaskRegistry getTaskRegistry() {
        return taskRegistry;
    }

    @Override
    Project getProject() {
        return project;
    }

    public void addStream(@NonNull TransformStream stream) {
        streams.add(stream);
    }

    /**
     * Adds a Transform.
     *
     * <p>This makes the current transform consumes whatever Streams are currently available and
     * creates new ones for the transform output.
     *
     * <p>This also creates a {@link TransformTask} to run the transform and wire it up with the
     * dependencies of the consumed streams.
     *
     * @param taskFactory the task factory
     * @param scope the current scope
     * @param transform the transform to add
     * @param <T> the type of the transform
     * @return {@code Optional<AndroidTask<Transform>>} containing the AndroidTask if it was able to
     *     create it
     */
    @NonNull
    public <T extends Transform> Optional<AndroidTask<TransformTask>> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull TransformVariantScope scope,
            @NonNull T transform) {
        return addTransform(taskFactory, scope, transform, null /*callback*/);
    }

    /**
     * Adds a Transform.
     *
     * <p>This makes the current transform consumes whatever Streams are currently available and
     * creates new ones for the transform output.
     *
     * <p>his also creates a {@link TransformTask} to run the transform and wire it up with the
     * dependencies of the consumed streams.
     *
     * @param taskFactory the task factory
     * @param scope the current scope
     * @param transform the transform to add
     * @param callback a callback that is run when the task is actually configured
     * @param <T> the type of the transform
     * @return {@code Optional<AndroidTask<Transform>>} containing the AndroidTask for the given
     *     transform task if it was able to create it
     */
    @NonNull
    public <T extends Transform> Optional<AndroidTask<TransformTask>> addTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull TransformVariantScope scope,
            @NonNull T transform,
            @Nullable TransformTask.ConfigActionCallback<T> callback) {

        if (!validateTransform(transform)) {
            // validate either throws an exception, or records the problem during sync
            // so it's safe to just return null here.
            return Optional.empty();
        }

        List<TransformStream> inputStreams = Lists.newArrayList();
        String taskName = scope.getTaskName(getTaskNamePrefix(transform));

        // get referenced-only streams
        List<TransformStream> referencedStreams = grabReferencedStreams(transform);

        // find input streams, and compute output streams for the transform.
        IntermediateStream outputStream = findTransformStreams(
                transform,
                scope,
                inputStreams,
                taskName,
                scope.getGlobalScope().getBuildDir());

        if (inputStreams.isEmpty() && referencedStreams.isEmpty()) {
            // didn't find any match. Means there is a broken order somewhere in the streams.
            errorReporter.handleSyncError(
                    null,
                    SyncIssue.TYPE_GENERIC,
                    String.format(
                            "Unable to add Transform '%s' on variant '%s': requested streams not available: %s+%s / %s",
                            transform.getName(), scope.getFullVariantName(),
                            transform.getScopes(), transform.getReferencedScopes(),
                            transform.getInputTypes()));
            return Optional.empty();
        }

        //noinspection PointlessBooleanExpression
        if (DEBUG && logger.isEnabled(LogLevel.DEBUG)) {
            logger.debug("ADDED TRANSFORM(" + scope.getFullVariantName() + "):");
            logger.debug("\tName: " + transform.getName());
            logger.debug("\tTask: " + taskName);
            for (TransformStream sd : inputStreams) {
                logger.debug("\tInputStream: " + sd);
            }
            for (TransformStream sd : referencedStreams) {
                logger.debug("\tRef'edStream: " + sd);
            }
            if (outputStream != null) {
                logger.debug("\tOutputStream: " + outputStream);
            }
        }

        transforms.add(transform);

        // create the task...
        AndroidTask<TransformTask> task =
                taskRegistry.create(
                        taskFactory,
                        new TransformTask.ConfigAction<>(
                                scope.getFullVariantName(),
                                taskName,
                                transform,
                                inputStreams,
                                referencedStreams,
                                outputStream,
                                recorder,
                                callback));

        return Optional.ofNullable(task);
    }

    @Override
    @NonNull
    public List<TransformStream> getStreams() {
        return streams;
    }

    @VisibleForTesting
    @NonNull
    static String getTaskNamePrefix(@NonNull Transform transform) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("transform");

        sb.append(
                transform.getInputTypes()
                        .stream()
                        .map(inputType ->
                                CaseFormat.UPPER_UNDERSCORE.to(
                                        CaseFormat.UPPER_CAMEL, inputType.name()))
                        .sorted() // Keep the order stable.
                        .collect(Collectors.joining("And")))
                .append("With")
                .append(capitalize(transform.getName()))
                .append("For");

        return sb.toString();
    }

    /**
     * Finds the stream the transform consumes, and return them.
     *
     * <p>This also removes them from the instance list. They will be replaced with the output
     * stream(s) from the transform.
     *
     * <p>This returns an optional output stream.
     *
     * @param transform the transform.
     * @param scope the scope the transform is applied to.
     * @param inputStreams the out list of input streams for the transform.
     * @param taskName the name of the task that will run the transform
     * @param buildDir the build dir of the project.
     * @return the output stream if any.
     */
    @Nullable
    private IntermediateStream findTransformStreams(
            @NonNull Transform transform,
            @NonNull TransformVariantScope scope,
            @NonNull List<TransformStream> inputStreams,
            @NonNull String taskName,
            @NonNull File buildDir) {

        Set<? super Scope> requestedScopes = transform.getScopes();
        if (requestedScopes.isEmpty()) {
            // this is a no-op transform.
            return null;
        }

        Set<ContentType> requestedTypes = transform.getInputTypes();

        // list to hold the list of unused streams in the manager after everything is done.
        // they'll be put back in the streams collection, along with the new outputs.
        List<TransformStream> oldStreams = Lists.newArrayListWithExpectedSize(streams.size());

        for (TransformStream stream : streams) {
            // streams may contain more than we need. In this case, we'll make a copy of the stream
            // with the remaining types/scopes. It'll be up to the TransformTask to make
            // sure that the content of the stream is usable (for instance when a stream
            // may contain two scopes, these scopes could be combined or not, impacting consumption)
            Set<ContentType> availableTypes = stream.getContentTypes();
            Set<? super Scope> availableScopes = stream.getScopes();

            Set<ContentType> commonTypes = Sets.intersection(requestedTypes,
                    availableTypes);
            Set<? super Scope> commonScopes = Sets.intersection(requestedScopes, availableScopes);
            if (!commonTypes.isEmpty() && !commonScopes.isEmpty()) {

                // check if we need to make another stream from this one with less scopes/types.
                if (!commonScopes.equals(availableScopes) || !commonTypes.equals(availableTypes)) {
                    // first the stream that gets consumed. It consumes only the common types/scopes
                    inputStreams.add(stream.makeRestrictedCopy(commonTypes, commonScopes));

                    // Now we could have two more streams. One with the requestedScope but the remainingTypes, and the other one with the remaining scopes and all the types.
                    // compute remaining scopes/types.
                    Sets.SetView<ContentType> remainingTypes =
                            Sets.difference(availableTypes, commonTypes);
                    Sets.SetView<? super Scope> remainingScopes = Sets.difference(availableScopes, commonScopes);

                    if (!remainingTypes.isEmpty()) {
                        oldStreams.add(
                                stream.makeRestrictedCopy(
                                        remainingTypes.immutableCopy(), availableScopes));
                    }
                    if (!remainingScopes.isEmpty()) {
                        oldStreams.add(
                                stream.makeRestrictedCopy(
                                        availableTypes, remainingScopes.immutableCopy()));
                    }
                } else {
                    // stream is an exact match (or at least subset) for the request,
                    // so we add it as it.
                    inputStreams.add(stream);
                }
            } else {
                // stream is not used, keep it around.
                oldStreams.add(stream);
            }
        }

        // create the output stream.
        // create single combined output stream for all types and scopes
        Set<ContentType> outputTypes = transform.getOutputTypes();

        File outRootFolder = FileUtils.join(buildDir, StringHelper.toStrings(
                AndroidProject.FD_INTERMEDIATES,
                FD_TRANSFORMS,
                transform.getName(),
                scope.getDirectorySegments()));

        // update the list of available streams.
        streams.clear();
        streams.addAll(oldStreams);

        // create the output
        IntermediateStream outputStream =
                IntermediateStream.builder(
                                project, transform.getName() + "-" + scope.getFullVariantName())
                        .addContentTypes(outputTypes)
                        .addScopes(requestedScopes)
                        .setRootLocation(outRootFolder)
                        .setTaskName(taskName)
                        .build();
        // and add it to the list of available streams for next transforms.
        streams.add(outputStream);

        return outputStream;
    }

    @NonNull
    private List<TransformStream> grabReferencedStreams(@NonNull Transform transform) {
        Set<? super Scope> requestedScopes = transform.getReferencedScopes();
        if (requestedScopes.isEmpty()) {
            return ImmutableList.of();
        }

        List<TransformStream> streamMatches = Lists.newArrayListWithExpectedSize(streams.size());

        Set<ContentType> requestedTypes = transform.getInputTypes();
        for (TransformStream stream : streams) {
            // streams may contain more than we need. In this case, we'll provide the whole
            // stream as-is since it's not actually consumed.
            // It'll be up to the TransformTask to make sure that the content of the stream is
            // usable (for instance when a stream
            // may contain two scopes, these scopes could be combined or not, impacting consumption)
            Set<ContentType> availableTypes = stream.getContentTypes();
            Set<? super Scope> availableScopes = stream.getScopes();

            Set<ContentType> commonTypes = Sets.intersection(requestedTypes,
                    availableTypes);
            Set<? super Scope> commonScopes = Sets.intersection(requestedScopes, availableScopes);

            if (!commonTypes.isEmpty() && !commonScopes.isEmpty()) {
                streamMatches.add(stream);
            }
        }

        return streamMatches;
    }

    private boolean validateTransform(@NonNull Transform transform) {
        // check the content type are of the right Type.
        if (!checkContentTypes(transform.getInputTypes(), transform)
                || !checkContentTypes(transform.getOutputTypes(), transform)) {
            return false;
        }

        // check some scopes are not consumed.
        Set<? super Scope> scopes = transform.getScopes();
        if (scopes.contains(Scope.PROVIDED_ONLY)) {
            errorReporter.handleSyncError(null, SyncIssue.TYPE_GENERIC,
                    String.format("PROVIDED_ONLY scope cannot be consumed by Transform '%1$s'",
                            transform.getName()));
            return false;
        }
        if (scopes.contains(Scope.TESTED_CODE)) {
            errorReporter.handleSyncError(null, SyncIssue.TYPE_GENERIC,
                    String.format("TESTED_CODE scope cannot be consumed by Transform '%1$s'",
                            transform.getName()));
            return false;

        }

        if (!transform
                .getClass()
                .getCanonicalName()
                .startsWith("com.android.build.gradle.internal.transforms")) {
            checkScopeDeprecation(transform.getScopes(), transform.getName());
            checkScopeDeprecation(transform.getReferencedScopes(), transform.getName());
        }

        return true;
    }

    @SuppressWarnings("deprecation")
    private void checkScopeDeprecation(
            @NonNull Set<? super Scope> scopes, @NonNull String transformName) {
        if (scopes.contains(Scope.PROJECT_LOCAL_DEPS)) {
            final String message =
                    String.format(
                            "Transform '%1$s' uses scope %2$s which is deprecated and replaced with %3$s",
                            transformName,
                            Scope.PROJECT_LOCAL_DEPS.name(),
                            Scope.EXTERNAL_LIBRARIES.name());
            if (!scopes.contains(Scope.EXTERNAL_LIBRARIES)) {
                errorReporter.handleSyncError(null, SyncIssue.TYPE_GENERIC, message);
            }
        }

        if (scopes.contains(Scope.SUB_PROJECTS_LOCAL_DEPS)) {
            final String message =
                    String.format(
                            "Transform '%1$s' uses scope %2$s which is deprecated and replaced with %3$s",
                            transformName,
                            Scope.SUB_PROJECTS_LOCAL_DEPS.name(),
                            Scope.EXTERNAL_LIBRARIES.name());
            if (!scopes.contains(Scope.EXTERNAL_LIBRARIES)) {
                errorReporter.handleSyncError(null, SyncIssue.TYPE_GENERIC, message);
            }
        }
    }

    private boolean checkContentTypes(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Transform transform) {
        for (ContentType contentType : contentTypes) {
            if (!(contentType instanceof QualifiedContent.DefaultContentType
                    || contentType instanceof ExtendedContentType)) {
                errorReporter.handleSyncError(null, SyncIssue.TYPE_GENERIC,
                        String.format("Custom content types (%1$s) are not supported in transforms (%2$s)",
                                contentType.getClass().getName(), transform.getName()));
                return false;
            }
        }
        return true;
    }
}
