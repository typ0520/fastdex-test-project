/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.internal.testing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.TestData;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Basic Callable to run tests on a given {@link DeviceConnector} using {@link
 * RemoteAndroidTestRunner}.
 *
 * <p>The boolean return value is true if success.
 */
public class SimpleTestCallable implements Callable<Boolean> {

    public static final String FILE_COVERAGE_EC = "coverage.ec";

    @NonNull private final RemoteAndroidTestRunner runner;
    @NonNull private final String projectName;
    @NonNull private final DeviceConnector device;
    @NonNull private final String flavorName;
    @NonNull private final TestData testData;
    @NonNull private final File resultsDir;
    @NonNull private final File coverageDir;
    @NonNull private final List<File> testedApks;
    @NonNull private final Collection<String> installOptions;
    @NonNull private final ILogger logger;
    @NonNull private final Set<File> helperApks;

    private final int timeoutInMs;

    public SimpleTestCallable(
            @NonNull DeviceConnector device,
            @NonNull String projectName,
            @NonNull RemoteAndroidTestRunner runner,
            @NonNull String flavorName,
            @NonNull List<File> testedApks,
            @NonNull TestData testData,
            @NonNull Set<File> helperApks,
            @NonNull File resultsDir,
            @NonNull File coverageDir,
            int timeoutInMs,
            @NonNull Collection<String> installOptions,
            @NonNull ILogger logger) {
        this.projectName = projectName;
        this.device = device;
        this.runner = runner;
        this.flavorName = flavorName;
        this.helperApks = helperApks;
        this.resultsDir = resultsDir;
        this.coverageDir = coverageDir;
        this.testedApks = testedApks;
        this.testData = testData;
        this.timeoutInMs = timeoutInMs;
        this.installOptions = installOptions;
        this.logger = logger;
    }

    @Override
    public Boolean call() throws Exception {
        String deviceName = device.getName();
        boolean isInstalled = false;

        CustomTestRunListener runListener =
                new CustomTestRunListener(deviceName, projectName, flavorName, logger);
        runListener.setReportDir(resultsDir);

        long time = System.currentTimeMillis();
        boolean success = false;

        String coverageFile =
                "/data/data/" + testData.getTestedApplicationId() + "/" + FILE_COVERAGE_EC;

        try {
            device.connect(timeoutInMs, logger);

            if (!testedApks.isEmpty()) {
                logger.verbose(
                        "DeviceConnector '%s': installing %s",
                        deviceName, Joiner.on(", ").join(testedApks));
                if (testedApks.size() > 1 && device.getApiLevel() < 21) {
                    throw new InstallException(
                            "Internal error, file a bug, multi-apk applications require a device with API level 21+");
                }
                if (testedApks.size() > 1) {
                    device.installPackages(testedApks, installOptions, timeoutInMs, logger);
                } else {
                    device.installPackage(testedApks.get(0), installOptions, timeoutInMs, logger);
                }
            }

            for (File helperApk : helperApks) {
                logger.verbose(
                        "DeviceConnector '%s': installing helper APK %s", deviceName, helperApk);
                device.installPackage(helperApk, installOptions, timeoutInMs, logger);
            }

            logger.verbose(
                    "DeviceConnector '%s': installing %s", deviceName, testData.getTestApk());
            device.installPackage(testData.getTestApk(), installOptions, timeoutInMs, logger);
            isInstalled = true;

            for (Map.Entry<String, String> argument :
                    testData.getInstrumentationRunnerArguments().entrySet()) {
                runner.addInstrumentationArg(argument.getKey(), argument.getValue());
            }

            if (testData.isTestCoverageEnabled()) {
                runner.addInstrumentationArg("coverage", "true");
                runner.addInstrumentationArg("coverageFile", coverageFile);
            }

            if (testData.getAnimationsDisabled()) {
                runner.setRunOptions("--no_window_animation");
            }

            runner.setRunName(deviceName);
            runner.setMaxtimeToOutputResponse(timeoutInMs);

            runner.run(runListener);

            TestRunResult testRunResult = runListener.getRunResult();

            success = true;

            // for now throw an exception if no tests.
            // TODO return a status instead of allow merging of multi-variants/multi-device reports.
            if (testRunResult.getNumTests() == 0) {
                CustomTestRunListener fakeRunListener =
                        new CustomTestRunListener(deviceName, projectName, flavorName, logger);
                fakeRunListener.setReportDir(resultsDir);

                // create a fake test output
                Map<String, String> emptyMetrics = Collections.emptyMap();
                TestIdentifier fakeTest =
                        new TestIdentifier(device.getClass().getName(), "No tests found.");
                fakeRunListener.testStarted(fakeTest);
                fakeRunListener.testFailed(
                        fakeTest,
                        "No tests found. This usually means that your test classes are"
                                + " not in the form that your test runner expects (e.g. don't inherit from"
                                + " TestCase or lack @Test annotations).");
                fakeRunListener.testEnded(fakeTest, emptyMetrics);

                // end the run to generate the XML file.
                fakeRunListener.testRunEnded(System.currentTimeMillis() - time, emptyMetrics);
                return false;
            }

            return !testRunResult.hasFailedTests() && !testRunResult.isRunFailure();
        } catch (Exception e) {
            Map<String, String> emptyMetrics = Collections.emptyMap();

            // create a fake test output
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter pw = new PrintWriter(baos, true);
            e.printStackTrace(pw);
            TestIdentifier fakeTest = new TestIdentifier(device.getClass().getName(), "runTests");
            runListener.testStarted(fakeTest);
            runListener.testFailed(fakeTest, baos.toString());
            runListener.testEnded(fakeTest, emptyMetrics);

            // end the run to generate the XML file.
            runListener.testRunEnded(System.currentTimeMillis() - time, emptyMetrics);

            // and throw
            throw e;
        } finally {
            if (isInstalled) {
                // Get the coverage if needed.
                if (success && testData.isTestCoverageEnabled()) {
                    pullCoverageData(deviceName, coverageFile);
                }

                uninstall(testData.getTestApk(), testData.getApplicationId(), deviceName);

                for (File testedApk : testedApks) {
                    uninstall(testedApk, testData.getTestedApplicationId(), deviceName);
                }
            }

            device.disconnect(timeoutInMs, logger);
        }
    }

    private void pullCoverageData(String deviceName, String coverageFile)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        String temporaryCoverageCopy =
                "/data/local/tmp/" + testData.getTestedApplicationId() + "." + FILE_COVERAGE_EC;

        MultiLineReceiver outputReceiver =
                new MultiLineReceiver() {
                    @Override
                    public void processNewLines(String[] lines) {
                        for (String line : lines) {
                            logger.verbose(line);
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                };

        logger.verbose(
                "DeviceConnector '%s': fetching coverage data from %s", deviceName, coverageFile);
        device.executeShellCommand(
                "run-as "
                        + testData.getTestedApplicationId()
                        + " cat "
                        + coverageFile
                        + " | cat > "
                        + temporaryCoverageCopy,
                outputReceiver,
                30,
                TimeUnit.SECONDS);
        device.pullFile(
                temporaryCoverageCopy,
                new File(coverageDir, deviceName + "-" + FILE_COVERAGE_EC).getPath());
        device.executeShellCommand(
                "rm " + temporaryCoverageCopy, outputReceiver, 30, TimeUnit.SECONDS);
    }

    private void uninstall(
            @NonNull File apkFile, @Nullable String packageName, @NonNull String deviceName)
            throws DeviceException {
        if (packageName != null) {
            logger.verbose("DeviceConnector '%s': uninstalling %s", deviceName, packageName);
            device.uninstallPackage(packageName, timeoutInMs, logger);
        } else {
            logger.verbose(
                    "DeviceConnector '%s': unable to uninstall %s: unable to get package name",
                    deviceName, apkFile);
        }
    }
}
