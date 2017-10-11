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

package com.android.tools.fd.client;



import com.android.ddmlib.*;
import com.android.tools.fd.runtime.ApplicationPatch;
import com.android.tools.fd.runtime.Paths;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.fd.client.InstantRunArtifactType.DEX;
import static com.android.tools.fd.client.InstantRunArtifactType.SPLIT;
import static com.android.tools.fd.common.ProtocolConstants.*;
import static com.android.tools.fd.runtime.Paths.getDeviceIdFolder;

public class InstantRunClient {

    /** Local port on the desktop machine via which we tunnel to the Android device */
    // Note: just a random number, hopefully it is a free/available port on the host
    private static final int DEFAULT_LOCAL_PORT = 46622;


    private final String mPackageName;


    private final ILogger mLogger;

    private final long mToken;

    private final ServiceCommunicator mAppService;

    public InstantRunClient(
             String packageName,
             ILogger logger,
            long token) {
        this(packageName, logger, token, DEFAULT_LOCAL_PORT);
    }

    @VisibleForTesting
    public InstantRunClient(
             String packageName,
             ILogger logger,
            long token,
            int port) {
        mAppService = new ServiceCommunicator(packageName, logger, port);
        mPackageName = packageName;
        mLogger = logger;
        mToken = token;
    }

    private static File createTempFile(String prefix, String suffix) throws IOException {
        //noinspection SSBasedInspection Tests use this in tools/base
        File file = File.createTempFile(prefix, suffix);
        file.deleteOnExit();
        return file;
    }

    /**
     * Attempts to connect to a given device and sees if an instant run enabled app is running
     * there.
     */

    public AppState getAppState( IDevice device) throws IOException {
        return mAppService.talkToService(device,
                new Communicator<AppState>() {
                    @Override
                    public AppState communicate( DataInputStream input,
                             DataOutputStream output) throws IOException {
                        output.writeInt(MESSAGE_PING);
                        boolean foreground = input.readBoolean(); // Wait for "pong"
                        mLogger.info(
                            "Ping sent and replied successfully, "
                            + "application seems to be running. Foreground=" + foreground);
                        return foreground ? AppState.FOREGROUND : AppState.BACKGROUND;
                    }
                });
    }


    @SuppressWarnings("unused")
    public void showToast( IDevice device,  final String message)
            throws IOException {
        mAppService.talkToService(device, new Communicator<Boolean>() {
            @Override
            public Boolean communicate( DataInputStream input,
                     DataOutputStream output) throws IOException {
                output.writeInt(MESSAGE_SHOW_TOAST);
                output.writeUTF(message);
                return false;
            }
        });
    }

    /**
     * Restart the activity on this device, if it's running and is in the foreground.
     */
    public void restartActivity( IDevice device) throws IOException {
        AppState appState = getAppState(device);
        if (appState == AppState.FOREGROUND || appState == AppState.BACKGROUND) {
            mAppService.talkToService(device, new Communicator<Void>() {
                @Override
                public Void communicate( DataInputStream input,
                         DataOutputStream output) throws IOException {
                    output.writeInt(MESSAGE_RESTART_ACTIVITY);
                    writeToken(output);
                    return null;
                }
            });
        }
    }

    public UpdateMode pushPatches( IDevice device,
             final InstantRunBuildInfo buildInfo,
             UpdateMode updateMode,
            final boolean isRestartActivity,
            final boolean isShowToastEnabled) throws InstantRunPushFailedException, IOException {
        if (!buildInfo.canHotswap()) {
            updateMode = updateMode.combine(UpdateMode.COLD_SWAP);
        }

        List<FileTransfer> files = Lists.newArrayList();

        boolean appInForeground;
        boolean appRunning;
        try {
            AppState appState = getAppState(device);
            appInForeground = appState == AppState.FOREGROUND;
            appRunning = appState == AppState.FOREGROUND || appState == AppState.BACKGROUND;
        } catch (IOException e) {
            appInForeground = appRunning = false;
        }

        List<InstantRunArtifact> artifacts = buildInfo.getArtifacts();
        mLogger.info("Artifacts from build-info.xml: " + Joiner.on("-").join(artifacts));
        for (InstantRunArtifact artifact : artifacts) {
            InstantRunArtifactType type = artifact.type;
            File file = artifact.file;
            switch (type) {
                case MAIN:
                    // Should never be used with this method: APKs should be pushed by DeployApkTask
                    assert false : artifact;
                    break;
                case SPLIT_MAIN:
                    // Should only be used here when we're doing a *compatible*
                    // resource swap and also got an APK for split. Ignore here.
                    continue;
                case SPLIT:
                    // Should never be used with this method: APK splits should
                    // be pushed by SplitApkDeployTask
                    assert false : artifact;
                    break;
                case RESOURCES:
                    updateMode = updateMode.combine(UpdateMode.WARM_SWAP);
                    files.add(FileTransfer.createResourceFile(file));
                    break;
                case DEX:
                    throw new UnsupportedOperationException(DEX.toString());
                case RELOAD_DEX:
                    if (appInForeground) {
                        files.add(FileTransfer.createHotswapPatch(file));
                    } else {
                        // Gradle created a reload dex, but the app is no longer running.
                        // If it created a cold swap artifact, we can use it; otherwise we're out of luck.
                        if (!buildInfo.hasOneOf(DEX, SPLIT)) {
                            throw new InstantRunPushFailedException(
                                "Can't apply hot swap patch: app is no longer running");
                        }
                    }
                    break;
                default:
                    assert false : artifact;
            }
        }

        boolean needRestart;

        if (appRunning) {
            List<ApplicationPatch> changes = new ArrayList<>(files.size());
            for (FileTransfer file : files) {
                try {
                    changes.add(file.getPatch());
                }
                catch (IOException e) {
                    throw new InstantRunPushFailedException("Could not read file " + file);
                }
            }
            updateMode = pushPatches(device, buildInfo.getTimeStamp(), changes,
                                     updateMode, isRestartActivity, isShowToastEnabled);

            needRestart = false;
            if (!appInForeground || !buildInfo.canHotswap()) {
                stopApp(device, false /* sendChangeBroadcast */);
                needRestart = true;
            }
        }
        else {
            return UpdateMode.COLD_SWAP;
        }

        logFilesPushed(files, needRestart);

        if (needRestart) {
            // TODO: this should not need to be explicit, but leaving in to ensure no behaviour change.
            return UpdateMode.COLD_SWAP;
        }
        return updateMode;
    }

    public UpdateMode pushPatches( IDevice device,
             final String buildId,
             final List<ApplicationPatch> changes,
             UpdateMode updateMode,
            final boolean isRestartActivity,
            final boolean isShowToastEnabled) throws IOException {
        if (changes.isEmpty() || updateMode == UpdateMode.NO_CHANGES) {
            // Sync the build id to the device; Gradle might rev the build id
            // even when there are no changes, and we need to make sure that the
            // device id reflects this new build id, or the next build will
            // discover different id's and will conclude that it needs to do a
            // full rebuild
            transferLocalIdToDeviceId(device, buildId);

            return UpdateMode.NO_CHANGES;
        }

        if (updateMode == UpdateMode.HOT_SWAP && isRestartActivity) {
            updateMode = updateMode.combine(UpdateMode.WARM_SWAP);
        }

        final UpdateMode updateMode1 = updateMode;
        mAppService.talkToService(device, new Communicator<Boolean>() {
            @Override
            public Boolean communicate( DataInputStream input,
                     DataOutputStream output) throws IOException {
                output.writeInt(MESSAGE_PATCHES);
                writeToken(output);
                ApplicationPatchUtil.write(output, changes, updateMode1);

                // Let the app know whether it should show toasts
                output.writeBoolean(isShowToastEnabled);

                // Finally read a boolean back from the other side; this has the net effect of
                // waiting until applying/verifying code on the other side is done. (It doesn't
                // count the actual restart time, but for activity restarts it's typically instant,
                // and for cold starts we have no easy way to handle it (the process will die and a
                // new process come up; to measure that we'll need to work a lot harder.)
                input.readBoolean();

                return false;
            }

            @Override
            int getTimeout() {
                return 8000; // allow up to 8 seconds for resource push
            }
        });

        transferLocalIdToDeviceId(device, buildId);

        return updateMode;
    }

    /**
     * Called after a build &amp; successful push to device: updates the build id on the device to
     * whatever the build id was assigned by Gradle.
     *
     * @param device the device to push to
     */
    public void transferLocalIdToDeviceId( IDevice device,  String buildId) {
        transferBuildIdToDevice(device, buildId, mPackageName, mLogger);
    }

    // Note: This method can be called even if IR is turned off, as even when IR is off, we want to
    // trash any existing build ids saved on the device.
    public static void transferBuildIdToDevice( IDevice device,
             String buildId,
             String pkgName,
             ILogger logger) {
        if (logger == null) {
            logger = new NullLogger();
        }
        final long unused = 0L;
        InstantRunClient client = new InstantRunClient(pkgName, logger, unused);
        client.transferBuildIdToDevice(device, buildId);
    }

    private void transferBuildIdToDevice( IDevice device,  String buildId) {
        try {
            String remoteIdFile = getDeviceIdFolder(mPackageName);
            //noinspection SSBasedInspection This should work
            File local = File.createTempFile("build-id", "txt");
            local.deleteOnExit();
            Files.write(buildId, local, Charsets.UTF_8);
            device.pushFile(local.getPath(), remoteIdFile);
        } catch (IOException ioe) {
            mLogger.warning("Couldn't write build id file: %s", ioe);
        } catch (AdbCommandRejectedException | TimeoutException | SyncException e) {
            mLogger.warning("%s", Throwables.getStackTraceAsString(e));
        }
    }

    @SuppressWarnings("unused")

    public static String getDeviceBuildTimestamp( IDevice device,
             String packageName,  ILogger logger) {
        try {
            String remoteIdFile = getDeviceIdFolder(packageName);
            File localIdFile = createTempFile("build-id", "txt");
            try {
                device.pullFile(remoteIdFile, localIdFile.getPath());
                return Files.toString(localIdFile, Charsets.UTF_8).trim();
            } catch (SyncException ignore) {
                return null;
            } finally {
                //noinspection ResultOfMethodCallIgnored
                localIdFile.delete();
            }
        } catch (IOException ignore) {
        } catch (AdbCommandRejectedException | TimeoutException e) {
            logger.warning("%s", Throwables.getStackTraceAsString(e));
        }

        return null;
    }

    private void writeToken( DataOutputStream output) throws IOException {
        output.writeLong(mToken);
    }

    /**
     * Transfer the file as a hotswap overlay file. This means
     * that its remote path should be a temporary file.
     */
    public static final int TRANSFER_MODE_HOTSWAP = 3;

    /**
     * Transfer the file as a resource file. This means that it
     * should be written to the inactive resource file section
     * in the app data directory.
     */
    public static final int TRANSFER_MODE_RESOURCES = 4;

    /**
     * File to be transferred to the device. For use with
     * {@link #pushPatches(IDevice, InstantRunBuildInfo, UpdateMode, boolean, boolean)}
     */
    public static class FileTransfer {
        public final int mode;
        public final File source;
        public final String name;

        public FileTransfer(int mode,  File source,  String name) {
            this.mode = mode;
            this.source = source;
            this.name = name;
        }


        public static FileTransfer createResourceFile( File source) {
            return new FileTransfer(TRANSFER_MODE_RESOURCES, source, Paths.RESOURCE_FILE_NAME);
        }


        public static FileTransfer createHotswapPatch( File source) {
            return new FileTransfer(TRANSFER_MODE_HOTSWAP, source, Paths.RELOAD_DEX_FILE_NAME);
        }


        public ApplicationPatch getPatch() throws IOException {
            byte[] bytes = Files.toByteArray(source);
            String path;
            // These path names are specially handled on the client side
            // (e.g. it interprets "classes.dex" as meaning create a new
            // unique class file in the class folder.
            switch (mode) {
                case TRANSFER_MODE_HOTSWAP:
                case TRANSFER_MODE_RESOURCES:
                    path = name;
                    break;
                default:
                    throw new IllegalArgumentException(Integer.toString(mode));
            }

            return new ApplicationPatch(path, bytes);
        }

        @Override
        public String toString() {
            return source + " as " + name + " with mode " + mode;
        }
    }

    /**
     * Stops the given app (via adb).
     *
     * @param device the device
     * @param sendChangeBroadcast whether to also send a package change broadcast
     * @throws InstantRunPushFailedException if there's a problem
     */
    public void stopApp( IDevice device, boolean sendChangeBroadcast)
            throws InstantRunPushFailedException {
        try {
            runCommand(device, "am force-stop " + mPackageName);
        } catch (Throwable t) {
            throw new InstantRunPushFailedException("Exception while stopping app: " + t);
        }
        if (sendChangeBroadcast) {
            try {
                // We think this might necessary to force the system not hold on
                // to any data from the previous version of the process, such as
                // the scenario described in
                // https://code.google.com/p/android/issues/detail?id=200895#c9
                runCommand(device, "am broadcast -a android.intent.action.PACKAGE_CHANGED -p "
                           + mPackageName);
            } catch (Throwable ignore) {
                // We can live with this one not succeeding; may require root etc
                // See https://code.google.com/p/android/issues/detail?id=201249
            }
        }
    }

    private boolean runCommand( IDevice device,  String cmd)
            throws TimeoutException, AdbCommandRejectedException,
            ShellCommandUnresponsiveException, IOException {
        String output = getCommandOutput(device, cmd).trim();
        if (!output.isEmpty()) {
            mLogger.warning("Unexpected shell output for " + cmd + ": " + output);
            return false;
        }
        return true;
    }


    private static String getCommandOutput( IDevice device,  String cmd)
            throws TimeoutException, AdbCommandRejectedException,
            ShellCommandUnresponsiveException, IOException {
        CollectingOutputReceiver receiver;
        receiver = new CollectingOutputReceiver();
        device.executeShellCommand(cmd, receiver);
        return receiver.getOutput();
    }

    private void logFilesPushed( List<FileTransfer> files, boolean needRestart) {
        StringBuilder sb = new StringBuilder("Pushing files: ");
        if (needRestart) {
            sb.append("(needs restart) ");
        }

        sb.append('[');
        String separator = "";
        for (FileTransfer file : files) {
            sb.append(separator);
            sb.append(file.source.getName());
            sb.append(" as ");
            sb.append(file.name);

            separator = ", ";
        }
        sb.append(']');

        mLogger.info(sb.toString());
    }
}
