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

import static com.android.utils.FileUtils.getAllFiles;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.shrinker.parser.BytecodeVersion;
import com.android.build.gradle.shrinker.tracing.NoOpTracer;
import com.android.build.gradle.shrinker.tracing.RealTracer;
import com.android.build.gradle.shrinker.tracing.Trace;
import com.android.build.gradle.shrinker.tracing.Tracer;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Stopwatch;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeTraverser;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Code shrinker. It analyzes the input classes and the SDK jar and outputs minified classes. Uses
 * the given implementation of {@link ShrinkerGraph} to keep state and persist it for later
 * incremental runs.
 */
public class FullRunShrinker<T> extends AbstractShrinker<T> {

    /** Result of the shrinker run. */
    @Immutable
    public class Result {
        @NonNull public final ShrinkerGraph<T> graph;
        @NonNull public final Map<T, Trace<T>> traces;

        public Result(@NonNull ShrinkerGraph<T> graph, @NonNull Map<T, Trace<T>> traces) {
            this.graph = graph;
            this.traces = traces;
        }
    }

    /** Suffix for "fake methods", inserted to forward dependencies between unrelated classes. */
    static final String SHRINKER_FAKE_MARKER = "$shrinker_fake";

    private final Set<File> mPlatformJars;

    public FullRunShrinker(
            @NonNull WaitableExecutor executor,
            @NonNull ShrinkerGraph<T> graph,
            @NonNull Set<File> platformJars,
            @NonNull ShrinkerLogger shrinkerLogger,
            @Nullable BytecodeVersion bytecodeVersion) {
        super(graph, executor, shrinkerLogger, bytecodeVersion);
        mPlatformJars = platformJars;
    }

    /**
     * Performs the full shrinking run. This clears previous incremental state, creates a new {@link
     * ShrinkerGraph} and fills it with data read from the platform JARs as well as input classes.
     * Then we find "entry points" that match {@code -keep} rules from the config file, and walk the
     * graph, setting the counters and finding reachable classes and members. In the last step we
     * rewrite all reachable class files to only contain kept class members and put them in the
     * matching output directories.
     */
    public Result run(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedClasses,
            @NonNull TransformOutputProvider output,
            @NonNull ImmutableMap<CounterSet, KeepRules> keepRules,
            @Nullable KeepRules whyAreYouKeepingRules,
            boolean saveState)
            throws IOException {
        output.deleteAll();
        Stopwatch stopwatch = Stopwatch.createStarted();

        buildGraph(inputs, referencedClasses);
        logTime("Build graph", stopwatch);

        Tracer<T> tracer = setCounters(keepRules, whyAreYouKeepingRules);
        logTime("Set counters", stopwatch);

        writeOutput(inputs, output);
        logTime("Write output", stopwatch);

        if (saveState) {
            mGraph.saveState();
            logTime("Saving state", stopwatch);
        }

        return new Result(mGraph, tracer.getRecordedTraces());
    }

    /**
     * Populates the graph with all nodes (classes, members) and edges (dependencies, references),
     * so that it's ready to be traversed in search of reachable nodes.
     */
    private void buildGraph(
            @NonNull Iterable<TransformInput> programInputs,
            @NonNull Iterable<TransformInput> libraryInputs)
            throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        final PostProcessingData<T> postProcessingData = new PostProcessingData<>();

        readPlatformJars();

        for (TransformInput input : libraryInputs) {
            for (File directory : getAllDirectories(input)) {
                for (final File classFile : getClassFiles(directory)) {
                    mExecutor.execute(
                            () -> {
                                processLibraryClass(Files.toByteArray(classFile));
                                return null;
                            });
                }
            }

            for (final File jarFile : getAllJars(input)) {
                processJarFile(jarFile, this::processLibraryClass);
            }
        }

        for (TransformInput input : programInputs) {
            for (File directory : getAllDirectories(input)) {
                for (final File classFile : getClassFiles(directory)) {
                    mExecutor.execute(
                            () -> {
                                processProgramClassFile(
                                        Files.toByteArray(classFile),
                                        classFile,
                                        postProcessingData);
                                return null;
                            });
                }
            }

            for (final File jarFile : getAllJars(input)) {
                processJarFile(
                        jarFile,
                        bytes -> processProgramClassFile(bytes, jarFile, postProcessingData));
            }
        }
        waitForAllTasks();
        logTime("Read input", stopwatch);

        handleOverrides(postProcessingData.getVirtualMethods());
        handleMultipleInheritance(postProcessingData.getMultipleInheritance());
        handleInterfaceInheritance(postProcessingData.getInterfaceInheritance());
        resolveReferences(postProcessingData.getUnresolvedReferences());
        waitForAllTasks();
        logTime("Finish graph", stopwatch);

        mGraph.checkDependencies(mShrinkerLogger);
    }

    private void handleInterfaceInheritance(@NonNull Set<T> interfaceInheritance) {
        for (final T klass : interfaceInheritance) {
            mExecutor.execute(
                    () -> {
                        handleInterfaceInheritance(klass);
                        return null;
                    });
        }
    }

    private void handleInterfaceInheritance(T klass) {
        TreeTraverser<T> interfaceTraverser =
                TypeHierarchyTraverser.interfaces(mGraph, mShrinkerLogger);

        if ((mGraph.getModifiers(klass) & Opcodes.ACC_INTERFACE) != 0) {

            // The "children" name is unfortunate: in the type hierarchy tree traverser,
            // these are the interfaces that klass (which is an interface itself)
            // extends (directly).
            Iterable<T> superinterfaces = interfaceTraverser.children(klass);

            for (T superinterface : superinterfaces) {
                if (mGraph.isProgramClass(superinterface)) {
                    // Add the arrow going "down", from the superinterface to this one.
                    mGraph.addDependency(superinterface, klass, DependencyType.SUPERINTERFACE_KEPT);
                } else {
                    // The superinterface is part of the SDK, so it's always kept. As
                    // long as there's any class that implements this interface, it
                    // needs to be kept.
                    mGraph.addRoots(
                            ImmutableMap.of(klass, DependencyType.SUPERINTERFACE_KEPT),
                            CounterSet.SHRINK);
                }
            }
        }

        Iterable<T> implementedInterfaces =
                // Skip the class itself.
                interfaceTraverser.preOrderTraversal(klass).skip(1);

        for (T iface : implementedInterfaces) {
            if (mGraph.isProgramClass(iface)) {
                mGraph.addDependency(klass, iface, DependencyType.INTERFACE_IMPLEMENTED);
            }
        }
    }

    @NonNull
    private static FluentIterable<File> getClassFiles(@NonNull File dir) {
        return getAllFiles(dir).filter(f -> Files.getFileExtension(f.getName()).equals("class"));
    }

    /**
     * Updates the graph to handle a case when a class inherits an interface method implementation
     * from a super class which does not implement the given interface.
     *
     * <p>We handle it by inserting fake nodes into the graph, equivalent to just calling super() to
     * invoke the inherited implementation. This way an "invokeinterface" opcode can cause the fake
     * method to be kept, which in turn causes the real method to be kept, even though on the
     * surface it has nothing to do with the interface.
     */
    private void handleMultipleInheritance(@NonNull Set<T> multipleInheritance) {
        for (final T klass : multipleInheritance) {
            mExecutor.execute(
                    new Callable<Void>() {
                        final Set<T> methods = mGraph.getMethods(klass);

                        @Override
                        public Void call() throws Exception {
                            T superclass;
                            try {
                                superclass = mGraph.getSuperclass(klass);

                                if (superclass == null || !isProgramClass(superclass)) {
                                    // All the superclass methods are kept anyway.
                                    return null;
                                }

                                Iterable<T> interfaces =
                                        TypeHierarchyTraverser.interfaces(mGraph, mShrinkerLogger)
                                                .preOrderTraversal(klass)
                                                .skip(1); // Skip the class itself.

                                for (T iface : interfaces) {
                                    for (T method : mGraph.getMethods(iface)) {
                                        handleMethod(method);
                                    }
                                }
                                return null;
                            } catch (ClassLookupException e) {
                                mShrinkerLogger.invalidClassReference(
                                        mGraph.getClassName(klass), e.getClassName());
                                return null;
                            }
                        }

                        private void handleMethod(T method) {
                            if (this.methods.contains(method)) {
                                // We implement this interface method directly in the class, which is the
                                // common case. Nothing left to do.
                                return;
                            }

                            // Otherwise, look in the superclasses for the implementation.

                            FluentIterable<T> superclasses =
                                    TypeHierarchyTraverser.superclasses(mGraph, mShrinkerLogger)
                                            .preOrderTraversal(klass)
                                            .skip(1); // Skip the class itself.

                            for (T current : superclasses) {
                                if (!isProgramClass(current)) {
                                    // We will not remove the method anyway.
                                    return;
                                }

                                T matchingMethod = mGraph.findMatchingMethod(current, method);
                                if (matchingMethod != null) {
                                    String name =
                                            mGraph.getMemberName(method) + SHRINKER_FAKE_MARKER;
                                    String desc = mGraph.getMemberDescriptor(method);
                                    T fakeMethod =
                                            mGraph.addMember(
                                                    klass, name, desc, mGraph.getModifiers(method));

                                    // Simulate a super call.
                                    mGraph.addDependency(
                                            fakeMethod,
                                            matchingMethod,
                                            DependencyType.REQUIRED_CLASS_STRUCTURE);

                                    if (!isProgramClass(mGraph.getOwnerClass(method))) {
                                        mGraph.addDependency(
                                                klass,
                                                fakeMethod,
                                                DependencyType.REQUIRED_CLASS_STRUCTURE);
                                    } else {
                                        mGraph.addDependency(
                                                klass, fakeMethod, DependencyType.CLASS_IS_KEPT);
                                        mGraph.addDependency(
                                                method, fakeMethod, DependencyType.IF_CLASS_KEPT);
                                    }

                                    return;
                                }
                            }
                        }
                    });
        }
    }

    /**
     * Updates the graph to add edges which model how overridden methods should be handled.
     *
     * <p>A method overriding another one (from a class or interface), is kept if it's invoked
     * directly (naturally) or if the class is kept for whatever reason and the overridden method is
     * also invoked - we don't know if the call site for the overridden method actually operates on
     * objects of the subclass.
     */
    private void handleOverrides(@NonNull Set<T> virtualMethods) {
        for (final T method : virtualMethods) {
            mExecutor.execute(
                    () -> {
                        if (isJavaLangObjectMethod(
                                mGraph.getMemberName(method), mGraph.getMemberDescriptor(method))) {
                            // If we override an SDK method, it just has to be there at runtime
                            // (if the class itself is kept).
                            mGraph.addDependency(
                                    mGraph.getOwnerClass(method),
                                    method,
                                    DependencyType.REQUIRED_CLASS_STRUCTURE);
                            return null;
                        }

                        FluentIterable<T> superTypes =
                                TypeHierarchyTraverser.superclassesAndInterfaces(
                                                mGraph, mShrinkerLogger)
                                        .preOrderTraversal(mGraph.getOwnerClass(method))
                                        .skip(1); // Skip the class itself.

                        for (T klass : superTypes) {
                            if (mGraph.getClassName(klass).equals("java/lang/Object")) {
                                continue;
                            }

                            T superMethod = mGraph.findMatchingMethod(klass, method);
                            if (superMethod != null && !superMethod.equals(method)) {
                                if (!isProgramClass(mGraph.getOwnerClass(superMethod))) {
                                    // If we override an SDK method, it just has to be there at runtime
                                    // (if the class itself is kept).
                                    mGraph.addDependency(
                                            mGraph.getOwnerClass(method),
                                            method,
                                            DependencyType.REQUIRED_CLASS_STRUCTURE);
                                    return null;
                                } else {
                                    // If we override a program method, there's a chance this method is
                                    // never called and we will get rid of it. Set up the dependencies
                                    // appropriately.
                                    mGraph.addDependency(
                                            mGraph.getOwnerClass(method),
                                            method,
                                            DependencyType.CLASS_IS_KEPT);
                                    mGraph.addDependency(
                                            superMethod, method, DependencyType.IF_CLASS_KEPT);
                                }
                            }
                        }
                        return null;
                    });
        }
    }

    private static boolean isJavaLangObjectMethod(
            @NonNull String name, @NonNull String descriptor) {
        return (name.equals("hashCode") && descriptor.equals("()I"))
                || (name.equals("equals") && descriptor.equals("(Ljava/lang/Object;)Z"))
                || (name.equals("toString") && descriptor.equals("()Ljava/lang/String;"));
    }

    /**
     * Updates the graph with nodes from a library (read-only) class. There's no point creating
     * edges, since library classes cannot references program classes and we don't shrink library
     * code.
     */
    private void processLibraryClass(@NonNull byte[] source) {
        ClassReader classReader = new ClassReader(source);
        classReader.accept(
                new ClassStructureVisitor<>(mGraph, null, null),
                ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    }

    /** Updates the graph with nodes and edges based on the given class file. */
    private void processProgramClassFile(
            byte[] bytes,
            @NonNull File classFile,
            @NonNull final PostProcessingData<T> postProcessingData) {
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        ClassVisitor depsFinder =
                new DependencyFinderVisitor<T>(mGraph, classNode) {
                    @Override
                    protected void handleDependency(T source, T target, DependencyType type) {
                        mGraph.addDependency(source, target, type);
                    }

                    @Override
                    protected void handleMultipleInheritance(T klass) {
                        postProcessingData.getMultipleInheritance().add(klass);
                    }

                    @Override
                    protected void handleVirtualMethod(T method) {
                        postProcessingData.getVirtualMethods().add(method);
                    }

                    @Override
                    protected void handleInterfaceInheritance(T klass) {
                        postProcessingData.getInterfaceInheritance().add(klass);
                    }

                    @Override
                    protected void handleUnresolvedReference(
                            PostProcessingData.UnresolvedReference<T> reference) {
                        postProcessingData.getUnresolvedReferences().add(reference);
                    }
                };
        ClassVisitor structureVisitor = new ClassStructureVisitor<>(mGraph, classFile, depsFinder);
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(structureVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private interface ByteCodeConsumer {
        void process(byte[] bytes);
    }

    private void readPlatformJars() throws IOException {
        for (File platformJar : mPlatformJars) {
            processJarFile(platformJar, this::processLibraryClass);
        }
    }

    private void processJarFile(File platformJar, final ByteCodeConsumer consumer)
            throws IOException {
        try (JarFile jarFile = new JarFile(platformJar)) {
            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    final byte[] bytes = ByteStreams.toByteArray(inputStream);
                    mExecutor.execute(
                            () -> {
                                consumer.process(bytes);
                                return null;
                            });
                }
            }
        }
    }

    /** Sets the roots (i.e. entry points) of the graph and marks all nodes reachable from them. */
    private Tracer<T> setCounters(
            @NonNull ImmutableMap<CounterSet, KeepRules> allKeepRules,
            @Nullable KeepRules whyAreYouKeepingRules) {
        CounterSet counterSet = CounterSet.SHRINK;
        KeepRules keepRules = allKeepRules.get(counterSet);
        Set<T> whyAreYouKeeping = Sets.newConcurrentHashSet();

        for (final T klass : mGraph.getAllProgramClasses()) {
            mExecutor.execute(
                    () -> {
                        mGraph.addRoots(keepRules.getSymbolsToKeep(klass, mGraph), counterSet);
                        if (whyAreYouKeepingRules != null) {
                            whyAreYouKeeping.addAll(
                                    whyAreYouKeepingRules.getSymbolsToKeep(klass, mGraph).keySet());
                        }
                        return null;
                    });
        }
        waitForAllTasks();

        Tracer<T> tracer;
        //noinspection VariableNotUsedInsideIf: if this is null, it means we're not tracing.
        if (whyAreYouKeepingRules == null) {
            tracer = new NoOpTracer<>();
        } else {
            tracer = new RealTracer<>(whyAreYouKeeping);
        }

        setCounters(counterSet, tracer);

        return tracer;
    }

    private void writeOutput(
            @NonNull Collection<TransformInput> inputs, @NonNull TransformOutputProvider output)
            throws IOException {
        updateClassFiles(
                mGraph.getReachableClasses(CounterSet.SHRINK),
                Collections.emptyList(),
                inputs,
                output);
    }
}
