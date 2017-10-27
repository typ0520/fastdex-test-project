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

package com.android.build.gradle.internal.actions

import com.google.common.collect.Lists
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileWriter
import java.io.Serializable
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Extract attr IDs from a jar file and puts it in a R.txt
 */
class AttrExtractor @Inject constructor(val inputFile: File, val outputFile: File) : Runnable, Serializable {

    override fun run() {
        val attributes = ZipFile(inputFile).use { zip ->
            val entry = zip.getEntry("android/R\$attr.class")

            val result : MutableList<AttributeValue>?

            if (entry != null) {
                val stream = zip.getInputStream(entry)!! // this method does not return null.

                val customClassVisitor = CustomClassVisitor()
                ClassReader(stream).accept(customClassVisitor, 0)

                result = customClassVisitor.attributes
            } else {
                result = null
            }

            // "return" this to assign it to the attributes outside of 'use'
            result
        }

        FileWriter(outputFile).use { writer ->
            if (attributes != null) {
                for ((name, value) in attributes) {
                    writer.write("int attr $name 0x${String.format("%08x", value)}\n")
                }
            }
        }
    }
}

data class AttributeValue(val name: String, val value: Int)

class CustomClassVisitor : ClassVisitor(Opcodes.ASM5) {

    val attributes: MutableList<AttributeValue> = Lists.newArrayList()

    override fun visitField(access: Int, name: String?, desc: String?, signature: String?, value: Any?): FieldVisitor? {
        if (value is Int) {
            attributes.add(AttributeValue(name!!, value))
        }
        return null
    }
}