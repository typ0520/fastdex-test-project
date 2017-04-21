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

package com.android.tools.fd.runtime;

import android.util.Log;

import static com.android.tools.fd.runtime.AppInfo.applicationId;
import static com.android.tools.fd.runtime.Logging.LOG_TAG;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class which handles locating existing code and resource files on the device,
 * as well as writing new versions of these.
 */
public class FileManager {
    /**
     * Whether we're using extracted resources rather than just pointing to a .zip
     * archive for the resource ap_ file.
     * <p>
     * According to Dianne, using an extracted directory tree of resources rather than
     * in an archive was implemented before 1.0 and never used or tested... so we should
     * tread carefully here.
     */
    static final boolean USE_EXTRACTED_RESOURCES = false;

    /** Name of file to write resource data into, if not extracting resources */
    private static final String RESOURCE_FILE_NAME = Paths.RESOURCE_FILE_NAME;

    /** Name of folder to write extracted resource data into, if extracting resources */
    private static final String RESOURCE_FOLDER_NAME = "resources";

    /** Name of the file which points to either the left or the right data directory */
    private static final String FILE_NAME_ACTIVE = "active";

    /** Name of the left directory */
    private static final String FOLDER_NAME_LEFT = "left";

    /** Name of the right directory */
    private static final String FOLDER_NAME_RIGHT = "right";

    /** Prefix for reload.dex files */
    private static final String RELOAD_DEX_PREFIX = "reload";

    /** Suffix for classes.dex files */
    public static final String CLASSES_DEX_SUFFIX = ".dex";

    /** Whether we've purged temp dex files in this session */
    private static boolean havePurgedTempDexFolder;

    /**
     * The folder where resources and code are located. Within this folder we have two
     * alternatives: "left" and "right". One is in the foreground (in use), one is in the
     * background (to write to). These are named {@link #FOLDER_NAME_LEFT} and
     * {@link #FOLDER_NAME_RIGHT} and the current one is pointed to by
     * {@link #FILE_NAME_ACTIVE}. */
    private static File getDataFolder() {
        // TODO: Call Context#getFilesDir(), but since we don't have a context yet figure
        // out what to do
        // Keep in sync with ResourceDeltaManager in the IDE (which needs this path
        // in order to run an adb wipe command when reinstalling a freshly built app
        // to avoid using stale data)
        return new File(Paths.getDataDirectory(applicationId));
    }


    private static File getResourceFile(File base) {
        //noinspection ConstantConditions
        return new File(base, USE_EXTRACTED_RESOURCES ? RESOURCE_FOLDER_NAME : RESOURCE_FILE_NAME);
    }

    /**
     * Returns the folder used for temporary .dex files (e.g. classes loaded on the fly
     * and only needing to exist during the current app process
     */

    private static File getTempDexFileFolder(File base) {
        return new File(base, "dex-temp");
    }

    public static File getNativeLibraryFolder() {
        return new File(Paths.getMainApkDataDirectory(applicationId), "lib");
    }

    /**
     * Returns the "foreground" folder: the location to read code and resources from.
     */

    public static File getReadFolder() {
        String name = leftIsActive() ? FOLDER_NAME_LEFT : FOLDER_NAME_RIGHT;
        return new File(getDataFolder(), name);
    }

    /**
     * Swaps the read/write folders such that the next time somebody asks for the
     * read or write folders, they'll get the opposite.
     */
    public static void swapFolders() {
        setLeftActive(!leftIsActive());
    }

    /**
     * Returns the "background" folder: the location to write code and resources to.
     */

    public static File getWriteFolder(boolean wipe) {
        String name = leftIsActive() ? FOLDER_NAME_RIGHT : FOLDER_NAME_LEFT;
        File folder = new File(getDataFolder(), name);
        if (wipe && folder.exists()) {
            delete(folder);
            boolean mkdirs = folder.mkdirs();
            if (!mkdirs) {
                Log.e(LOG_TAG, "Failed to create folder " + folder);
            }
        }
        return folder;
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            // Delete the contents
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    delete(child);
                }
            }
        }

        //noinspection ResultOfMethodCallIgnored
        boolean deleted = file.delete();
        if (!deleted) {
            Log.e(LOG_TAG, "Failed to delete file " + file);
        }
    }

    private static boolean leftIsActive() {
        File folder = getDataFolder();
        File pointer = new File(folder, FILE_NAME_ACTIVE);
        if (!pointer.exists()) {
            return true;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(pointer));
            try {
                String line = reader.readLine();
                return FOLDER_NAME_LEFT.equals(line);
            } finally {
                reader.close();
            }
        } catch (IOException ignore) {
            return true;
        }
    }

    private static void setLeftActive(boolean active) {
        File folder = getDataFolder();
        File pointer = new File(folder, FILE_NAME_ACTIVE);
        if (pointer.exists()) {
            boolean deleted = pointer.delete();
            if (!deleted) {
                Log.e(LOG_TAG, "Failed to delete file " + pointer);
            }
        } else if (!folder.exists()) {
            boolean create = folder.mkdirs();
            if (!create) {
                Log.e(LOG_TAG, "Failed to create directory " + folder);
            }
            return;
        }

        try {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pointer),
                    "UTF-8"));
            try {
                writer.write(active ? FOLDER_NAME_LEFT : FOLDER_NAME_RIGHT);
            } finally {
                writer.close();
            }
        } catch (IOException ignore) {
        }
    }

    /** Returns the current/active resource file, if it exists */

    public static File getExternalResourceFile() {
        File file = getResourceFile(getReadFolder());
        if (!file.exists()) {
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "Cannot find external resources, not patching them in");
            }
            return null;
        }

        return file;
    }

    /** Produces the next available dex file name */

    public static File getTempDexFile() {
        // Find the file name of the next dex file to write
        File dataFolder = getDataFolder();
        File dexFolder = getTempDexFileFolder(dataFolder);
        if (!dexFolder.exists()) {
            boolean created = dexFolder.mkdirs();
            if (!created) {
                Log.e(LOG_TAG, "Failed to create directory " + dexFolder);
                return null;
            }

            // There was nothing to purge, but leave the folder be from now on.
            havePurgedTempDexFolder = true;
        } else {
            // The *first* time we write a reload dex file in the new process, we'll
            // delete previously stashes reload dex files. (We keep them around
            // such that we can (repeatedly) warn an app on startup if its hotswap patches
            // are more recent than the app itself, such that developers aren't confused
            // when the app is not reflecting the most recent changes
            if (!havePurgedTempDexFolder) {
                purgeTempDexFiles(dataFolder);
            }
        }

        File[] files = dexFolder.listFiles();
        int max = -1;

        // Pick highest available number + 1 - we want these to be sortable
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(RELOAD_DEX_PREFIX) && name.endsWith(CLASSES_DEX_SUFFIX)) {
                    String middle = name.substring(RELOAD_DEX_PREFIX.length(),
                            name.length() - CLASSES_DEX_SUFFIX.length());
                    try {
                        int version = Integer.decode(middle);
                        if (version > max) {
                            max = version;
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }

        String fileName = String.format("%s0x%04x%s", RELOAD_DEX_PREFIX, max + 1,
                CLASSES_DEX_SUFFIX);
        File file = new File(dexFolder, fileName);

        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Log.v(LOG_TAG, "Writing new dex file: " + file);
        }

        return file;
    }

    public static boolean writeRawBytes(File destination, byte[] bytes) {
        try {
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
            try {
                output.write(bytes);
                output.flush();
                return true;
            } finally {
                output.close();
            }
        } catch (IOException ioe) {
            Log.wtf(LOG_TAG, "Failed to write file, clean project and rebuild " + destination, ioe);
            throw new RuntimeException(
                    String.format(
                            "InstantRun could not write file %1$s, clean project and rebuild ",
                            destination));
        }
    }

    public static boolean extractZip(File destination, byte[] zipBytes) {
        if (USE_EXTRACTED_RESOURCES) {
            InputStream inputStream = new ByteArrayInputStream(zipBytes);
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            try {
                byte[] buffer = new byte[2000];

                for (ZipEntry entry = zipInputStream.getNextEntry();
                        entry != null;
                        entry = zipInputStream.getNextEntry()) {
                    String name = entry.getName();
                    // Don't extract META-INF data
                    if (name.startsWith("META-INF")) {
                        continue;
                    }
                    if (!entry.isDirectory()) {
                        // Using / as separators in both .zip files and on Android, no need to convert
                        // to File.separator
                        File dest = new File(destination, name);
                        File parent = dest.getParentFile();
                        if (parent != null && !parent.exists()) {
                            boolean created = parent.mkdirs();
                            if (!created) {
                                Log.e(LOG_TAG, "Failed to create directory " + dest);
                                return false;
                            }
                        }

                        OutputStream src = new BufferedOutputStream(new FileOutputStream(dest));
                        try {
                            int bytesRead;
                            while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                                src.write(buffer, 0, bytesRead);
                            }
                        } finally {
                            src.close();
                        }
                    }
                }

                return true;
            } catch (IOException ioe) {
                Log.e(LOG_TAG, "Failed to extract zip contents into directory " + destination,
                        ioe);
                return false;
            } finally {
                try {
                    zipInputStream.close();
                } catch (IOException ignore) {
                }
            }
        } else {
            Log.wtf(LOG_TAG, "");
            return false;
        }
    }

    public static void startUpdate() {
        // Wipe the back-buffer, if already present
        getWriteFolder(true);
    }

    public static void finishUpdate(boolean wroteResources) {
        if (wroteResources) {
            swapFolders();
        }
    }

    public static void writeAaptResources(String relativePath, byte[] bytes) {
        // TODO: Take relativePath into account for the actual destination file
        File resourceFile = getResourceFile(getWriteFolder(false));
        File file = resourceFile;
        if (USE_EXTRACTED_RESOURCES) {
            file = new File(file, relativePath);
        }
        File folder = file.getParentFile();
        if (!folder.isDirectory()) {
            boolean created = folder.mkdirs();
            if (!created) {
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Cannot create local resource file directory " + folder);
                }
                return;
            }
        }

        if (relativePath.equals(RESOURCE_FILE_NAME)) {
            //noinspection ConstantConditions
            if (USE_EXTRACTED_RESOURCES) {
                extractZip(resourceFile, bytes);
            } else {
                writeRawBytes(file, bytes);
            }
        } else {
            writeRawBytes(file, bytes);
        }
    }


    public static String writeTempDexFile(byte[] bytes) {
        File file = getTempDexFile();
        if (file != null) {
            writeRawBytes(file, bytes);
            return file.getPath();
        } else {
            Log.e(LOG_TAG, "No file to write temp dex content to");
        }
        return null;
    }

    /**
     * Removes .dex files from the temp dex file folder
     */
    public static void purgeTempDexFiles(File dataFolder) {
        havePurgedTempDexFolder = true;

        File dexFolder = getTempDexFileFolder(dataFolder);
        if (!dexFolder.isDirectory()) {
            return;
        }
        File[] files = dexFolder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.getPath().endsWith(CLASSES_DEX_SUFFIX)) {
                boolean deleted = file.delete();
                if (!deleted) {
                    Log.e(LOG_TAG, "Could not delete temp dex file " + file);
                }
            }
        }
    }

    public static long getFileSize(String path) {
        if (USE_EXTRACTED_RESOURCES) {
            // Currently only handle this for resource files
            if (path.equals(RESOURCE_FILE_NAME)) {
                File file = getExternalResourceFile();
                if (file != null) {
                    return file.length();
                }
            }
        }

        return -1;
    }


    public static byte[] getCheckSum(String path) {
        if (USE_EXTRACTED_RESOURCES) {
            // Currently only handle this for resource files
            if (path.equals(RESOURCE_FILE_NAME)) {
                File file = getExternalResourceFile();
                if (file != null) {
                    return getCheckSum(file);
                }
            }
        }

        return null;
    }

    /**
     * Computes a checksum of a file.
     *
     * @param file the file to compute the fingerprint for
     * @return a fingerprint
     */

    public static byte[] getCheckSum(File file) {
        if (USE_EXTRACTED_RESOURCES) {
            try {
                // Create MD5 Hash
                MessageDigest digest = MessageDigest.getInstance("MD5");
                byte[] buffer = new byte[4096];
                BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
                try {
                    while (true) {
                        int read = input.read(buffer);
                        if (read == -1) {
                            break;
                        }
                        digest.update(buffer, 0, read);
                    }
                    return digest.digest();
                } finally {
                    input.close();
                }
            } catch (NoSuchAlgorithmException e) {
                if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                    Log.e(LOG_TAG, "Couldn't look up message digest", e);
                }
            } catch (IOException ioe) {
                if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                    Log.e(LOG_TAG, "Failed to read file " + file, ioe);
                }
            } catch (Throwable t) {
                if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                    Log.e(LOG_TAG, "Unexpected checksum exception", t);
                }
            }
        }
        return null;
    }
}
