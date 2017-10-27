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

package com.android.build.gradle.internal.tasks.featuresplit

import com.android.build.gradle.internal.dependency.VariantAttr
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.DefaultAndroidTask
import com.android.builder.model.MavenCoordinates
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.util.stream.Collectors
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier

/** Task to write the list of transitive dependencies.  */
@CacheableTask
open class FeatureSplitTransitiveDepsWriterTask : DefaultAndroidTask() {

    // list of runtime classpath.
    private lateinit var runtimeJars: ArtifactCollection

    @get:OutputFile
    private lateinit var outputFile: File

    // use CompileClasspath to get as little notifications as possible.
    // technically we only care when the list changes, not the content but there's no way
    // to get this right now.
    @CompileClasspath
    fun getInputJars() : FileCollection = runtimeJars.artifactFiles

    @TaskAction
    @Throws(IOException::class)
    fun write() {

        val content: Set<String> = runtimeJars.artifacts
                .stream()
                .map { artifact -> compIdToString(artifact) }
                .collect(Collectors.toSet())

        FileUtils.mkdirs(outputFile.parentFile)
        Files.asCharSink(outputFile, Charsets.UTF_8)
                .write(Joiner.on(System.lineSeparator()).join(content))
    }

    class ConfigAction(
            private val variantScope: VariantScope,
            private val outputFile: File) : TaskConfigAction<FeatureSplitTransitiveDepsWriterTask> {

        override fun getName() = variantScope.getTaskName("generate", "FeatureTransitiveDeps")
        override fun getType() = FeatureSplitTransitiveDepsWriterTask::class.java

        override fun execute(task: FeatureSplitTransitiveDepsWriterTask) {
            task.variantName = variantScope.fullVariantName
            task.outputFile = outputFile
            task.runtimeJars = variantScope.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.CLASSES)
        }
    }
}

fun compIdToString(artifact: ResolvedArtifactResult) : String {

    val id = artifact.id.componentIdentifier
    when (id) {
        is ProjectComponentIdentifier -> {
            val variant = getVariant(artifact)
            if (variant == null) {
                return id.projectPath
            } else {
                return id.projectPath + "::" + variant
            }
        }
        is ModuleComponentIdentifier -> return id.group + ":" + id.module
        else -> {
            return id.toString()
        }
    }
}

fun getVariant(artifact: ResolvedArtifactResult) = artifact.variant.attributes.getAttribute(VariantAttr.ATTRIBUTE)?.name

