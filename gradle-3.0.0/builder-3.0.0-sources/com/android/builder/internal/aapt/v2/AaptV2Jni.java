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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.Version;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AbstractAapt;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.tools.aapt2.Aapt2Jni;
import com.android.tools.aapt2.Aapt2RenamingConventions;
import com.android.tools.aapt2.Aapt2Result;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Implementation of {@link com.android.builder.internal.aapt.Aapt} that uses out-of-process
 * execution of {@code aapt2}.
 */
public class AaptV2Jni extends AbstractAapt {

    @NonNull private final File intermediateDir;
    @NonNull private final ProcessOutputHandler processOutputHandler;
    @NonNull private final WaitableExecutor executor;
    @NonNull private final Aapt2Jni aapt2Jni;

    /** Creates a new entry point to {@code aapt2} using the jni bindings. */
    public AaptV2Jni(
            @NonNull File intermediateDir,
            @NonNull WaitableExecutor executor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @Nullable FileCache fileCache)
            throws IOException {
        this.intermediateDir = intermediateDir;
        this.executor = executor;
        this.processOutputHandler = processOutputHandler;

        this.aapt2Jni = new Aapt2Jni(new TempDirCache());
    }

    @NonNull
    @Override
    protected ListenableFuture<Void> makeValidatedPackage(@NonNull AaptPackageConfig config)
            throws AaptException {
        if (config.getResourceOutputApk() != null) {
            try {
                Files.deleteIfExists(config.getResourceOutputApk().toPath());
            } catch (IOException e) {
                return Futures.immediateFailedFuture(e);
            }
        }
        List<String> args = AaptV2CommandBuilder.makeLink(config, intermediateDir);
        Aapt2Result aapt2Result = aapt2Jni.link(args);
        writeMessages(processOutputHandler, aapt2Result.getMessages());

        if (aapt2Result.getReturnCode() == 0) {
            return Futures.immediateFuture(null);
        } else {
            return Futures.immediateFailedFuture(buildException("link", args, aapt2Result));
        }
    }

    @NonNull
    @Override
    public Future<File> compile(@NonNull CompileResourceRequest request) throws Exception {
        return executor.execute(
                () -> {
                    List<String> args = AaptV2CommandBuilder.makeCompile(request);
                    Aapt2Result aapt2Result = aapt2Jni.compile(args);
                    writeMessages(processOutputHandler, aapt2Result.getMessages());

                    if (aapt2Result.getReturnCode() == 0) {
                        return new File(
                                request.getOutput(),
                                Aapt2RenamingConventions.compilationRename(request.getInput()));
                    } else {
                        throw buildException("compile", args, aapt2Result);
                    }
                });
    }

    @Override
    public void close() {
        // since we don't batch, we are done.
    }

    @Override
    @NonNull
    public File compileOutputFor(@NonNull CompileResourceRequest request) {
        return new File(
                request.getOutput(),
                Aapt2RenamingConventions.compilationRename(request.getInput()));
    }

    private static AaptException buildException(
            String action, List<String> args, Aapt2Result aapt2Result) {
        StringBuilder builder = new StringBuilder();
        builder.append("AAPT2 ")
                .append(action)
                .append(" failed:\naapt2 ")
                .append(action)
                .append(" ")
                .append(Joiner.on(' ').join(args))
                .append("\n");
        if (aapt2Result.getMessages().isEmpty()) {
            builder.append("No issues were reported");
        } else {
            builder.append("Issues:\n - ")
                    .append(Joiner.on("\n - ").join(aapt2Result.getMessages()));
        }
        return new AaptException(builder.toString());
    }

    /*
     * Writes messages received from AAPT2.
     */
    private static void writeMessages(
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull List<Aapt2Result.Message> messages)
            throws AaptException {
        if (messages.isEmpty()) {
            return;
        }
        ProcessOutput output;
        try (Closeable ignored = output = processOutputHandler.createOutput();
                PrintWriter err = new PrintWriter(output.getErrorOutput());
                PrintWriter out = new PrintWriter(output.getStandardOutput())) {
            for (Aapt2Result.Message message : messages) {
                switch (message.getLevel()) {
                    case NOTE:
                        out.println(message.toString());
                        break;
                    case ERROR:
                    case WARN:
                        err.println(message.toString());
                        break;
                }
            }
        } catch (IOException e) {
            throw new AaptException(e, "Unexpected error handling AAPT output");
        }
        try {
            processOutputHandler.handleOutput(output);
        } catch (ProcessException e) {
            throw new AaptException(e, "Unexpected error handling AAPT output");
        }
    }

    private static class FileCacheAapt2JniCache implements Aapt2Jni.Cache {

        @NonNull private final FileCache fileCache;

        FileCacheAapt2JniCache(@NonNull FileCache fileCache) {
            this.fileCache = fileCache;
        }

        @NonNull
        @Override
        public Path getCachedDirectory(
                @NonNull HashCode hashCode, @NonNull Aapt2Jni.Creator creator) throws IOException {

            String pluginVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION;
            // Development plugins may be reloaded in the same daemon.
            // As a new classloader is used, the same library cannot be copied to the same location.
            // Copy the library to a new location each time.
            if (pluginVersion.endsWith("-dev")) {
                pluginVersion +=
                        "-"
                                + LocalDateTime.now(ZoneOffset.ofHours(0))
                                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            FileCache.Inputs inputs =
                    new FileCache.Inputs.Builder(FileCache.Command.EXTRACT_AAPT2_JNI)
                            .putString("hashcode", hashCode.toString())
                            .putString("pluginVersion", pluginVersion)
                            .build();
            FileCache.QueryResult result;
            try {
                result =
                        fileCache.createFileInCacheIfAbsent(
                                inputs,
                                file -> {
                                    Files.createDirectory(file.toPath());
                                    creator.create(file.toPath());
                                });
            } catch (ExecutionException e) {
                throw new IOException("Failed to create AAPT2 jni cache entry", e);
            }
            return Preconditions.checkNotNull(result.getCachedFile()).toPath();
        }
    }

    private static class TempDirCache implements Aapt2Jni.Cache {

        @NonNull
        @Override
        public Path getCachedDirectory(
                @NonNull HashCode hashCode, @NonNull Aapt2Jni.Creator creator) throws IOException {
            Path tempDir = Files.createTempDirectory("aapt2_");
            creator.create(tempDir);

            /*
             * Add a hook to delete the directory and all files when the JVM exits. We can't do that
             * before because of the DLL being locked on Windows.
             */
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        try {
                                            Files.walkFileTree(tempDir, new RecursiveDelete());
                                        } catch (IOException ignored) {
                                            // well, we tried
                                        }
                                    }));
            return tempDir;
        }

        private static class RecursiveDelete extends SimpleFileVisitor<Path> {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        }
    }
}
