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
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

final class D8DexArchiveBuilder extends DexArchiveBuilder {

    private final int minSdkVersion;
    @NonNull private final CompilationMode compilationMode;

    public D8DexArchiveBuilder(int minSdkVersion, boolean isDebuggable) {
        this.minSdkVersion = minSdkVersion;
        this.compilationMode = isDebuggable ? CompilationMode.DEBUG : CompilationMode.RELEASE;
    }

    @Override
    public void convert(
            @NonNull Stream<ClassFileEntry> input, @NonNull Path output, boolean isIncremental)
            throws DexArchiveBuilderException {
        try {
            Iterator<byte[]> data = input.map(D8DexArchiveBuilder::readAllBytes).iterator();
            if (!data.hasNext()) {
                // nothing to do here, just return
                return;
            }

            OutputMode outputMode = isIncremental ? OutputMode.FilePerClass : OutputMode.Indexed;
            D8Command.Builder builder =
                    D8Command.builder()
                            .setMode(compilationMode)
                            .setMinApiLevel(minSdkVersion)
                            .setOutputMode(outputMode);

            while (data.hasNext()) {
                builder.addClassProgramData(data.next());
            }

            builder.setOutputPath(output);
            D8.run(builder.build(), MoreExecutors.newDirectExecutorService());
        } catch (Throwable e) {
            throw new DexArchiveBuilderException(e);
        }
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull ClassFileEntry entry) {
        try {
            return entry.readAllBytes();
        } catch (IOException ex) {
            throw new DexArchiveBuilderException(ex);
        }
    }
}
