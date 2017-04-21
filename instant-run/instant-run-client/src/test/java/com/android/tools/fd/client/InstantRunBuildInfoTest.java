/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.*;


import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class InstantRunBuildInfoTest {

    @Test
    public void testBuildId() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");
        assertEquals("1451508349243", info.getTimeStamp());
    }

    @Test
    public void testApiLevel() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");
        assertEquals(23, info.getFeatureLevel());
    }

    @Test
    public void testHasNoChanges() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info-no-artifacts.xml");
        assertTrue(
                "If there are no artifacts, then it doesn't matter what the verifier said.",
                info.hasNoChanges());

        info = getBuildInfo("instantrun", "build-info-res.xml");
        assertFalse("If there is an artifact, then there are changes", info.hasNoChanges());

        info = getBuildInfo("instantrun", "no-changes.xml");
        assertTrue(info.hasNoChanges());
    }

    @Test
    public void testFormat() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");
        assertEquals(1, info.getFormat());
    }

    @Test
    public void testSplitApks() throws IOException {
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info1.xml");

        List<InstantRunArtifact> artifacts = info.getArtifacts();
        assertEquals(11, artifacts.size());
        assertTrue(info.hasMainApk());
        assertTrue(
                artifacts.stream().filter(p -> p.timestamp.equals("1451508349243")).count() == 11);
    }

    @Test
    public void testSplitApks2() throws IOException {
        // Ensure that when we get a main APK (but not all the splits) as part of
        // a build info, we pull in only the necessary slices
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info2.xml");

        List<InstantRunArtifact> artifacts = info.getArtifacts();
        assertEquals(2, artifacts.size());
        assertTrue(info.hasMainApk());
        assertTrue(info.hasOneOf(InstantRunArtifactType.SPLIT));
        // should find two new build artifacts - one split and one main
        assertTrue(
                artifacts.stream().filter(p -> p.type.equals(InstantRunArtifactType.SPLIT)).count()
                        == 1);
        assertTrue(
                artifacts
                                .stream()
                                .filter(p -> p.type.equals(InstantRunArtifactType.SPLIT_MAIN))
                                .count()
                        == 1);
        assertTrue(
                artifacts.stream().filter(p -> p.timestamp.equals("1452207930094")).count() == 2);
        // don't look at old build
        assertTrue(
                artifacts.stream().filter(p -> p.timestamp.equals("1452205343311")).count() == 0);
        assertEquals("COLD", info.getBuildMode());
    }

    @Test
    public void fullBuildBasedOnNumberOfArtifacts() throws IOException {
        // output of a full build is never a patch
        InstantRunBuildInfo info = getBuildInfo("instantrun", "build-info-split1.xml");
        assertFalse(info.isPatchBuild());

        // output of a coldswap build should be considered a full build if the number of artifacts == 12
        info = getBuildInfo("instantrun", "build-info-split2.xml");
        assertFalse(info.isPatchBuild());

        // otherwise it is a patch build
        info = getBuildInfo("instantrun", "build-info-split3.xml");
        assertTrue(info.isPatchBuild());
    }


    private static InstantRunBuildInfo getBuildInfo(String... buildInfoPath)
            throws IOException {
        String path = Joiner.on('/').join(buildInfoPath);
        String xml = Resources.toString(Resources.getResource(path), Charsets.UTF_8);
        InstantRunBuildInfo buildInfo = InstantRunBuildInfo.get(xml);
        assertNotNull("Unable to create build info from resource @" + path, buildInfo);
        return buildInfo;
    }

}
