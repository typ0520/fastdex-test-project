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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.LIBS_FOLDER;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/** Transform that returns the content of an extracted AAR folder. */
public class AarTransform extends ArtifactTransform {
    @NonNull private final ArtifactType targetType;

    @Inject
    public AarTransform(@NonNull ArtifactType targetType) {
        this.targetType = targetType;
    }

    @NonNull
    public static ArtifactType[] getTransformTargets() {
        return new ArtifactType[] {
            ArtifactType.CLASSES,
            ArtifactType.JAVA_RES,
            ArtifactType.JAR,
            ArtifactType.MANIFEST,
            ArtifactType.ANDROID_RES,
            ArtifactType.ASSETS,
            ArtifactType.JNI,
            ArtifactType.AIDL,
            ArtifactType.RENDERSCRIPT,
            ArtifactType.PROGUARD_RULES,
            ArtifactType.LINT,
            ArtifactType.ANNOTATIONS,
            ArtifactType.PUBLIC_RES,
            ArtifactType.SYMBOL_LIST,
            ArtifactType.DATA_BINDING_ARTIFACT,
        };
    }

    @Override
    public List<File> transform(File input) {
        // single file case return
        File file;

        switch (targetType) {
            case CLASSES:
            case JAVA_RES:
            case JAR:
                // even though resources are supposed to only be in the main jar of the AAR, this
                // is not necessarily enforced by all build systems generating AAR so it's safer to
                // read all jars from the manifest.
                return getJars(input);
            case LINT:
                file = FileUtils.join(input, FD_JARS, FN_LINT_JAR);
                break;
            case MANIFEST:
                file = new File(input, FN_ANDROID_MANIFEST_XML);
                break;
            case ANDROID_RES:
                file = new File(input, FD_RES);
                break;
            case ASSETS:
                file = new File(input, FD_ASSETS);
                break;
            case JNI:
                file = new File(input, FD_JNI);
                break;
            case AIDL:
                file = new File(input, FD_AIDL);
                break;
            case RENDERSCRIPT:
                file = new File(input, FD_RENDERSCRIPT);
                break;
            case PROGUARD_RULES:
                file = new File(input, FN_PROGUARD_TXT);
                break;
            case ANNOTATIONS:
                file = new File(input, FN_ANNOTATIONS_ZIP);
                break;
            case PUBLIC_RES:
                file = new File(input, FN_PUBLIC_TXT);
                break;
            case SYMBOL_LIST:
                file = new File(input, FN_RESOURCE_TEXT);
                break;
            case DATA_BINDING_ARTIFACT:
                file = new File(input, DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR);
                break;
            default:
                throw new RuntimeException("Unsupported type in AarTransform: " + targetType);
        }

        if (file.exists()) {
            return Collections.singletonList(file);
        }

        return Collections.emptyList();
    }

    private static List<File> getJars(@NonNull File explodedAar) {
        List<File> files = Lists.newArrayList();
        File jarFolder = new File(explodedAar, FD_JARS);

        File file = FileUtils.join(jarFolder, FN_CLASSES_JAR);
        if (file.isFile()) {
            files.add(file);
        }

        // local jars
        final File localJarFolder = new File(jarFolder, LIBS_FOLDER);
        File[] jars = localJarFolder.listFiles((dir, name) -> name.endsWith(SdkConstants.DOT_JAR));

        if (jars != null) {
            files.addAll((Arrays.asList(jars)));
        }

        //System.out.println("\tJars: " + files);
        return files;
    }
}
