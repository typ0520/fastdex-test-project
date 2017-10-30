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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.apkzlib.zip.StoredEntry;
import java.io.IOException;

final class NoCacheJarClassFileEntry implements ClassFileEntry {

    @NonNull private final StoredEntry entry;

    public NoCacheJarClassFileEntry(@NonNull StoredEntry storedEntry) {
        this.entry = storedEntry;
    }

    @Override
    public String name() {
        return "Zip:" + entry.getCentralDirectoryHeader().getName();
    }

    @Override
    public long getSize() {
        return entry.getCentralDirectoryHeader().getUncompressedSize();
    }

    @Override
    public String getRelativePath() {
        return entry.getCentralDirectoryHeader().getName();
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return entry.read();
    }

    @Override
    public int readAllBytes(byte[] bytes) throws IOException {
        return entry.read(bytes);
    }
}
