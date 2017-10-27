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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.actions.AttrExtractor
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.sdklib.IAndroidTarget
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File

/**
 * Task to extract R.txt from android.jar
 */
@CacheableTask
open class PlatformAttrExtractorTask : DefaultTask() {

    private lateinit var inputFile: File
    private lateinit var outputFile: File

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    fun getInput() = inputFile

    @OutputFile
    fun getOutput() = outputFile

    @TaskAction
    fun action() {
        val executor = services.get(WorkerExecutor::class.java)

        executor.submit(AttrExtractor::class.java, {
            it.params(inputFile, outputFile)
            it.isolationMode = IsolationMode.NONE
        })
    }

    class ConfigAction(val scope: GlobalScope, val output: File):
            TaskConfigAction<PlatformAttrExtractorTask> {

        override fun getName() = "platformAttrExtractor"

        override fun getType() = PlatformAttrExtractorTask::class.java

        override fun execute(task: PlatformAttrExtractorTask) {
            task.inputFile = File(
                    scope.androidBuilder
                            .target
                            .getPath(IAndroidTarget.ANDROID_JAR))

            task.outputFile = output
        }
    }
}