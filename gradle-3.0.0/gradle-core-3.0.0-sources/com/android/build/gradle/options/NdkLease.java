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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import java.time.Instant;

public final class NdkLease {
    public static final long DEPRECATED_NDK_COMPILE_LEASE_DAYS = 60;
    public static final long DEPRECATED_NDK_COMPILE_LEASE_MILLIS =
            DEPRECATED_NDK_COMPILE_LEASE_DAYS * 24 * 60 * 60 * 1000;

    private NdkLease() {}

    public static long getFreshDeprecatedNdkCompileLease() {
        return Instant.now().toEpochMilli();
    }

    public static boolean isDeprecatedNdkCompileLeaseExpired(@NonNull ProjectOptions options) {
        Long leaseDate = options.get(LongOption.DEPRECATED_NDK_COMPILE_LEASE);
        if (leaseDate == null) {
            // There is no lease so it is expired by definition
            return true;
        }
        long freshLease = getFreshDeprecatedNdkCompileLease();
        if (freshLease - leaseDate > DEPRECATED_NDK_COMPILE_LEASE_MILLIS) {
            // There is a lease but it expired
            return true;
        }
        if (leaseDate > freshLease) {
            // The lease date is set too far in the future so it is expired by definition
            return true;
        }
        return false;
    }
}
