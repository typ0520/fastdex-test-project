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

package com.android.build.gradle.tasks.factory;

import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.wireless.android.sdk.stats.AnnotationProcessorInfo;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

/** Specialization of the JavaCompile task to record execution time. */
@CacheableTask
public class AndroidJavaCompile extends JavaCompile {

    String compileSdkVersion;

    InstantRunBuildContext mInstantRunBuildContext;

    File annotationProcessorOutputFolder;

    FileCollection processorListFile;

    String variantName;

    FileCollection dataBindingDependencyArtifacts;

    @InputFiles
    public FileCollection getProcessorListFile() {
        return processorListFile;
    }

    @OutputDirectory
    public File getAnnotationProcessorOutputFolder() {
        return annotationProcessorOutputFolder;
    }

    @InputFiles
    @Optional
    public FileCollection getDataBindingDependencyArtifacts() {
        return dataBindingDependencyArtifacts;
    }

    @Override
    protected void compile(IncrementalTaskInputs inputs) {
        getLogger().info(
                "Compiling with source level {} and target level {}.",
                getSourceCompatibility(),
                getTargetCompatibility());
        if (isPostN()) {
            if (!JavaVersion.current().isJava8Compatible()) {
                throw new RuntimeException("compileSdkVersion '" + compileSdkVersion + "' requires "
                        + "JDK 1.8 or later to compile.");
            }
        }

        processAnalytics();

        // Create directory for output of annotation processor.
        FileUtils.mkdirs(annotationProcessorOutputFolder);

        mInstantRunBuildContext.startRecording(InstantRunBuildContext.TaskType.JAVAC);
        super.compile(inputs);
        mInstantRunBuildContext.stopRecording(InstantRunBuildContext.TaskType.JAVAC);
    }

    /** Read the processorListFile to add annotation processors used to analytics. */
    @VisibleForTesting
    void processAnalytics() {
        Gson gson = new GsonBuilder().create();
        List<String> classNames;
        try (FileReader reader = new FileReader(processorListFile.getSingleFile())) {
            classNames = gson.fromJson(reader, new TypeToken<List<String>>() {}.getType());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String projectPath = getProject().getPath();
        GradleBuildVariant.Builder variant =
                ProcessProfileWriter.getOrCreateVariant(projectPath, variantName);
        for (String processorName : classNames) {
            AnnotationProcessorInfo.Builder builder = AnnotationProcessorInfo.newBuilder();
            builder.setSpec(processorName);
            variant.addAnnotationProcessors(builder);
        }
    }

    private boolean isPostN() {
        final AndroidVersion hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion);
        return hash != null && hash.getApiLevel() >= 24;
    }
}
