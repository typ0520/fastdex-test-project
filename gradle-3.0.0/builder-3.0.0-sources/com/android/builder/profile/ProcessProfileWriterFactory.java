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

package com.android.builder.profile;

import static com.google.common.base.Verify.verifyNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.Anonymizer;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Configures and creates instances of {@link ProcessProfileWriter}.
 *
 * <p>There can be only one instance of {@link ProcessProfileWriter} per process (well class loader
 * to be exact). This instance can be configured initially before any calls to {@link
 * ThreadRecorder#get()} is made. An exception will be thrown if an attempt is made to configure the
 * instance of {@link ProcessProfileWriter} past this initialization window.
 */
public final class ProcessProfileWriterFactory {

    public static void shutdownAndMaybeWrite(@Nullable Path outputFile)
            throws InterruptedException {
        synchronized (LOCK) {
            if (sINSTANCE.isInitialized()) {
                verifyNotNull(sINSTANCE.processProfileWriter);
                sINSTANCE.processProfileWriter.finishAndMaybeWrite(outputFile);
            }
            sINSTANCE.processProfileWriter = null;
        }
    }


    @NonNull
    private ScheduledExecutorService mScheduledExecutorService = Executors.newScheduledThreadPool(1);

    @VisibleForTesting
    ProcessProfileWriterFactory() {}
    @Nullable
    private ILogger mLogger = null;

    /** Set up the the ProcessProfileWriter. Idempotent for multi-project builds. */
    public static void initialize(
            @NonNull File rootProjectDirectoryPath,
            @NonNull String gradleVersion,
            @NonNull ILogger logger,
            boolean enableChromeTracingOutput) {

        synchronized (LOCK) {
            if (sINSTANCE.isInitialized()) {
                return;
            }
            sINSTANCE.setLogger(logger);
            sINSTANCE.setEnableChromeTracingOutput(enableChromeTracingOutput);
            ProcessProfileWriter recorder =
                    sINSTANCE.get(); // Initialize the ProcessProfileWriter instance
            setGlobalProperties(recorder, rootProjectDirectoryPath, gradleVersion, logger);
        }
    }

    private static void setGlobalProperties(
            @NonNull ProcessProfileWriter recorder,
            @NonNull File projectPath,
            @NonNull String gradleVersion,
            @NonNull ILogger logger) {
        recorder.getProperties()
                .setOsName(Strings.nullToEmpty(System.getProperty("os.name")))
                .setOsVersion(Strings.nullToEmpty(System.getProperty("os.version")))
                .setJavaVersion(Strings.nullToEmpty(System.getProperty("java.version")))
                .setJavaVmVersion(Strings.nullToEmpty(System.getProperty("java.vm.version")))
                .setMaxMemory(Runtime.getRuntime().maxMemory())
                .setGradleVersion(Strings.nullToEmpty(gradleVersion));

        String anonymizedProjectId;
        try {
            anonymizedProjectId = Anonymizer.anonymizeUtf8(logger, projectPath.getAbsolutePath());
        } catch (IOException e) {
            anonymizedProjectId = "*ANONYMIZATION_ERROR*";
        }
        recorder.getProperties().setProjectId(anonymizedProjectId);
    }

    public synchronized void setLogger(@NonNull ILogger iLogger) {
        assertRecorderNotCreated();
        this.mLogger = iLogger;
    }

    public static ProcessProfileWriterFactory getFactory() {
        return sINSTANCE;
    }

    boolean isInitialized() {
        return processProfileWriter != null;
    }

    @SuppressWarnings("VariableNotUsedInsideIf")
    private void assertRecorderNotCreated() {
        if (isInitialized()) {
            throw new RuntimeException("ProcessProfileWriter already created.");
        }
    }

    private static final Object LOCK = new Object();
    static ProcessProfileWriterFactory sINSTANCE = new ProcessProfileWriterFactory();

    @Nullable private ProcessProfileWriter processProfileWriter = null;

    @VisibleForTesting
    public static void initializeForTests() {
        sINSTANCE = new ProcessProfileWriterFactory();
        ProcessProfileWriter recorder =
                sINSTANCE.get(); // Initialize the ProcessProfileWriter instance
        recorder.resetForTests();
        setGlobalProperties(recorder,
                new File("fake/path/to/test_project/"),
                "2.10",
                new StdLogger(StdLogger.Level.VERBOSE));
    }

    private static void initializeAnalytics(@NonNull ILogger logger,
            @NonNull ScheduledExecutorService eventLoop) {
        AnalyticsSettings settings = AnalyticsSettings.getInstance(logger);
        UsageTracker.initialize(settings, eventLoop);
        UsageTracker tracker = UsageTracker.getInstance();
        tracker.setMaxJournalTime(10, TimeUnit.MINUTES);
        tracker.setMaxJournalSize(1000);
    }

    private boolean enableChromeTracingOutput;

    synchronized ProcessProfileWriter get() {
        if (processProfileWriter == null) {
            if (mLogger == null) {
                mLogger = new StdLogger(StdLogger.Level.INFO);
            }
            initializeAnalytics(mLogger, mScheduledExecutorService);
            processProfileWriter = new ProcessProfileWriter(enableChromeTracingOutput);
        }

        return processProfileWriter;
    }

    public void setEnableChromeTracingOutput(boolean enableChromeTracingOutput) {
        this.enableChromeTracingOutput = enableChromeTracingOutput;
    }
}
