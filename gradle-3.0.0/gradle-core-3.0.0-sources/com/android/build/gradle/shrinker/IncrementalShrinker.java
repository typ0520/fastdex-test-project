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

package com.android.build.gradle.shrinker;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.shrinker.parser.BytecodeVersion;
import com.android.build.gradle.shrinker.tracing.NoOpTracer;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;

/** Code for incremental shrinking. */
public class IncrementalShrinker<T> extends AbstractShrinker<T> {

    /**
     * Exception thrown when the incremental shrinker detects incompatible changes and requests a
     * full run instead.
     */
    public static class IncrementalRunImpossibleException extends RuntimeException {
        IncrementalRunImpossibleException(String message) {
            super(message);
        }

        IncrementalRunImpossibleException(Throwable cause) {
            super("Failed to load incremental state.", cause);
        }
    }

    public IncrementalShrinker(
            @NonNull WaitableExecutor executor,
            @NonNull ShrinkerGraph<T> graph,
            @NonNull ShrinkerLogger shrinkerLogger,
            @Nullable BytecodeVersion bytecodeVersion) {
        super(graph, executor, shrinkerLogger, bytecodeVersion);
    }

    /**
     * Perform incremental shrinking, in the supported cases (where only code in pre-existing
     * methods has been modified).
     *
     * <p>The general idea is this: for every method in modified classes, remove all outgoing "code
     * reference" edges, add them again based on the current code and then set the counters again
     * (traverse the graph) using the new set of edges.
     *
     * <p>The counters are re-calculated every time from scratch (starting from known entry points
     * from the config file) to avoid cycles being left in the output.
     *
     * @throws IncrementalRunImpossibleException If incremental shrinking is impossible and a full
     *     run should be done instead.
     */
    public void incrementalRun(
            @NonNull Iterable<TransformInput> inputs, @NonNull TransformOutputProvider output)
            throws IOException, IncrementalRunImpossibleException {
        Set<T> modifiedClasses = Sets.newConcurrentHashSet();
        Set<PostProcessingData.UnresolvedReference<T>> unresolvedReferences =
                Sets.newConcurrentHashSet();

        Stopwatch stopwatch = Stopwatch.createStarted();

        Map<T, State<T>> oldState = saveState();
        logTime("save state", stopwatch);

        clearCounters();
        logTime("clear counters", stopwatch);

        processInputs(inputs, modifiedClasses, unresolvedReferences);
        logTime("process inputs", stopwatch);

        finishGraph(unresolvedReferences);
        logTime("finish graph", stopwatch);

        setCounters(CounterSet.SHRINK, new NoOpTracer<>());
        logTime("set counters", stopwatch);

        Changes<T> changes = calculateChanges(inputs, output, oldState, modifiedClasses);
        logTime("choose classes", stopwatch);

        updateClassFiles(changes.classesToWrite, changes.classFilesToDelete, inputs, output);
        logTime("update class files", stopwatch);

        mGraph.saveState();
        logTime("save state", stopwatch);
    }

    /**
     * Decides which classes need to be updated on disk and which need to be deleted. It puts
     * appropriate entries in the lists passed as arguments.
     */
    private Changes<T> calculateChanges(
            @NonNull Iterable<TransformInput> inputs,
            @NonNull TransformOutputProvider output,
            @NonNull Map<T, State<T>> oldStates,
            @NonNull Set<T> modifiedClasses) {
        Set<T> classesToWrite = Sets.newConcurrentHashSet();
        Set<File> classFilesToDelete = Sets.newConcurrentHashSet();

        for (T klass : mGraph.getReachableClasses(CounterSet.SHRINK)) {
            if (!oldStates.containsKey(klass)) {
                classesToWrite.add(klass);
            } else {
                try {
                    State<T> oldState = oldStates.get(klass);

                    Set<String> newMembers =
                            mGraph.getReachableMembersLocalNames(klass, CounterSet.SHRINK);
                    Set<T> newInterfaces = getReachableImplementedInterfaces(klass);
                    Set<T> newTypesFromSignatures = getReachableTypesFromSignatures(klass);

                    // Update the class file if the user modified it or we "modified it" by adding or
                    // removing class members or implemented interfaces or types in generic signatures.
                    if (modifiedClasses.contains(klass)
                            || !newMembers.equals(oldState.members)
                            || !newTypesFromSignatures.equals(oldState.typesFromGenericSignatures)
                            || !newInterfaces.equals(oldState.interfaces)) {
                        classesToWrite.add(klass);
                    }
                } catch (ClassLookupException e) {
                    throw new AssertionError("Reachable class not found in graph.", e);
                }
            }

            oldStates.remove(klass);
        }

        // All keys that remained in oldStates should be deleted.
        for (T klass : oldStates.keySet()) {
            File sourceFile = mGraph.getSourceFile(klass);
            checkState(sourceFile != null, "One of the inputs has no source file.");

            Optional<File> outputFile = chooseOutputFile(klass, sourceFile, inputs, output);
            if (!outputFile.isPresent()) {
                throw new IllegalStateException(
                        "Can't determine path of " + mGraph.getClassName(klass));
            }
            classFilesToDelete.add(outputFile.get());
        }

        return new Changes<>(classesToWrite, classFilesToDelete);
    }

    /**
     * Returns the set of interfaces a given class implements, filtered to only include interfaces
     * that are otherwise used in the program.
     *
     * <p>In other words, the shrunk bytecode for {@code klass} should use these in the class
     * definition.
     */
    private Set<T> getReachableImplementedInterfaces(@NonNull T klass) throws ClassLookupException {
        return Stream.of(mGraph.getInterfaces(klass))
                .filter(iface -> mGraph.isReachable(iface, CounterSet.SHRINK))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the set of types referenced from the given class' generic signatures, filtered to
     * only include types that are otherwise used in the program.
     */
    private Set<T> getReachableTypesFromSignatures(@NonNull T klass) throws ClassLookupException {
        return mGraph.getTypesFromGenericSignatures(klass)
                .stream()
                .filter(
                        typeFromSignature ->
                                mGraph.isReachable(typeFromSignature, CounterSet.SHRINK))
                .collect(Collectors.toSet());
    }

    /**
     * Returns a {@link State} instance for every reachable class in the graph.
     *
     * @see State
     */
    @NonNull
    private Map<T, State<T>> saveState() {
        Map<T, State<T>> oldState = new HashMap<>();

        // TODO: do it in parallel?
        for (T klass : mGraph.getReachableClasses(CounterSet.SHRINK)) {
            try {
                Set<String> reachableMembers =
                        mGraph.getReachableMembersLocalNames(klass, CounterSet.SHRINK);
                Set<T> typesFromGenericSignatures = getReachableTypesFromSignatures(klass);
                Set<T> interfaces = getReachableImplementedInterfaces(klass);

                oldState.put(
                        klass,
                        new State<>(reachableMembers, interfaces, typesFromGenericSignatures));
            } catch (ClassLookupException e) {
                throw new AssertionError("Reachable class not found in graph.", e);
            }
        }

        return oldState;
    }

    private void clearCounters() {
        mGraph.clearCounters(mExecutor);
        waitForAllTasks();
    }

    private void finishGraph(
            @NonNull Iterable<PostProcessingData.UnresolvedReference<T>> unresolvedReferences) {
        resolveReferences(unresolvedReferences);
        waitForAllTasks();
    }

    private void processInputs(
            @NonNull Iterable<TransformInput> inputs,
            @NonNull final Collection<T> modifiedClasses,
            @NonNull
                    final Collection<PostProcessingData.UnresolvedReference<T>>
                            unresolvedReferences)
            throws IncrementalRunImpossibleException {
        for (final TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                switch (jarInput.getStatus()) {
                    case ADDED:
                    case REMOVED:
                    case CHANGED:
                        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
                        throw new IncrementalRunImpossibleException(
                                String.format(
                                        "Input jar %s has been %s.",
                                        jarInput.getFile(),
                                        jarInput.getStatus().name().toLowerCase()));
                    case NOTCHANGED:
                        break;
                }
            }

            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                for (final Map.Entry<File, Status> changedFile :
                        directoryInput.getChangedFiles().entrySet()) {
                    mExecutor.execute(
                            () -> {
                                switch (changedFile.getValue()) {
                                    case ADDED:
                                        throw new IncrementalRunImpossibleException(
                                                String.format(
                                                        "File %s added.", changedFile.getKey()));
                                    case REMOVED:
                                        throw new IncrementalRunImpossibleException(
                                                String.format(
                                                        "File %s removed.", changedFile.getKey()));
                                    case CHANGED:
                                        processChangedClassFile(
                                                changedFile.getKey(),
                                                unresolvedReferences,
                                                modifiedClasses);
                                        break;
                                    case NOTCHANGED:
                                        break;
                                }
                                return null;
                            });
                }
            }
        }
        waitForAllTasks();
    }

    /**
     * Handles a changed class file by removing old code references (graph edges) and adding
     * up-to-date edges, according to the current state of the class.
     *
     * <p>This only works on {@link DependencyType#REQUIRED_CODE_REFERENCE} edges, which are only
     * ever created from method containing the opcode to target memShareber. The first pass is
     * equivalent to removing all code from the method, the second to adding "current" opcodes to
     * it.
     *
     * @throws IncrementalRunImpossibleException If current members of the class are not the same as
     *     they used to be. This means that edges of other types need to be updated, and we don't
     *     handle this incrementally. It also means that -keep rules would need to be re-applied,
     *     which is something we also don't do incrementally.
     */
    private void processChangedClassFile(
            @NonNull File file,
            @NonNull Collection<PostProcessingData.UnresolvedReference<T>> unresolvedReferences,
            @NonNull Collection<T> modifiedClasses)
            throws IncrementalRunImpossibleException {
        try {
            ClassReader classReader = new ClassReader(Files.toByteArray(file));
            IncrementalRunVisitor<T> visitor =
                    new IncrementalRunVisitor<>(mGraph, modifiedClasses, unresolvedReferences);

            DependencyRemoverVisitor<T> remover = new DependencyRemoverVisitor<>(mGraph, visitor);

            classReader.accept(remover, 0);
        } catch (IncrementalRunImpossibleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process " + file.getAbsolutePath(), e);
        }
    }

    @Override
    protected void waitForAllTasks() {
        try {
            super.waitForAllTasks();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IncrementalRunImpossibleException) {
                throw (IncrementalRunImpossibleException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    /**
     * Holds data computed about a class in the previous run of the shrinker.
     *
     * <p>If any of this has changed, the classfile needs to be updated.
     */
    private static final class State<T> {
        /** Members (fields and methods) that were kept in the shrunk bytecode. */
        @NonNull final ImmutableSet<String> members;

        /** Interfaces that were used in the shrunk class definition. */
        @NonNull final ImmutableSet<T> interfaces;

        /** Types that were used in the shrunk class generic signatures. */
        @NonNull final ImmutableSet<T> typesFromGenericSignatures;

        public State(
                @NonNull Iterable<String> members,
                @NonNull Iterable<T> interfaces,
                @NonNull Iterable<T> typesFromGenericSignatures) {
            this.members = ImmutableSet.copyOf(members);
            this.interfaces = ImmutableSet.copyOf(interfaces);
            this.typesFromGenericSignatures = ImmutableSet.copyOf(typesFromGenericSignatures);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            State<?> state = (State<?>) o;
            return Objects.equal(members, state.members)
                    && Objects.equal(interfaces, state.interfaces)
                    && Objects.equal(typesFromGenericSignatures, state.typesFromGenericSignatures);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(members, interfaces, typesFromGenericSignatures);
        }
    }

    /** Describes changes that should be applied to the output directory. */
    private static final class Changes<T> {
        @NonNull final Set<T> classesToWrite;
        @NonNull final Set<File> classFilesToDelete;

        private Changes(@NonNull Set<T> classesToWrite, @NonNull Set<File> classFilesToDelete) {
            this.classesToWrite = classesToWrite;
            this.classFilesToDelete = classFilesToDelete;
        }
    }
}
