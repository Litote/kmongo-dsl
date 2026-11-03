/*
 * Copyright (C) 2016/2026 Litote
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.litote.kmongo.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates the coroutine flavor extensions (`kmongo-dsl-coroutine`) from the sync flavor sources (`kmongo-dsl-sync`).
 *
 * The generated files are committed (so that they are visible in the IDE, the sources jar and the documentation):
 * - `generateCoroutineSources` (with [verify] = false) writes them,
 * - `checkCoroutineSources` (with [verify] = true, part of `check`) fails if they are not up-to-date.
 */
@CacheableTask
abstract class GenerateCoroutineSourcesTask : DefaultTask() {

    /** The `org/litote/kmongo` sync sources directory. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val syncSourceDir: DirectoryProperty

    /** The `org/litote/kmongo/coroutine` sources directory. */
    @get:Internal
    abstract val coroutineSourceDir: DirectoryProperty

    /** If true, only checks that the coroutine sources are up-to-date. */
    @get:Input
    abstract val verify: Property<Boolean>

    /** Marker file (the task has no other output in verify mode). */
    @get:OutputFile
    abstract val marker: RegularFileProperty

    init {
        // resolved at configuration time: Task.project is not available at execution time (configuration cache)
        marker.convention(project.layout.buildDirectory.file("tmp/$name/done"))
    }

    @TaskAction
    fun generate() {
        val outdated = mutableListOf<String>()
        FILES.forEach { (syncName, coroutineName) ->
            val source = syncSourceDir.file(syncName).get().asFile
            val target = coroutineSourceDir.file(coroutineName).get().asFile
            val generated = transform(source.readText(), syncName)
            if (verify.get()) {
                if (!target.exists() || target.readText() != generated) outdated += coroutineName
            } else if (!target.exists() || target.readText() != generated) {
                target.parentFile.mkdirs()
                target.writeText(generated)
                logger.lifecycle("generated ${target.name}")
            }
        }
        if (outdated.isNotEmpty()) {
            throw GradleException(
                "The coroutine sources $outdated are not up-to-date with the sync sources: " +
                        "run ./gradlew :kmongo-dsl-coroutine:generateCoroutineSources"
            )
        }
        val markerFile = marker.get().asFile
        markerFile.parentFile.mkdirs()
        markerFile.writeText("ok")
    }

    companion object {

        /** Sync file -> coroutine file. */
        val FILES: Map<String, String> = linkedMapOf(
            "MongoCollections.kt" to "MongoCollections.kt",
            "MongoCollectionsWithClientSession.kt" to "MongoCollectionsWithClientSession.kt",
            "MongoDatabases.kt" to "MongoDatabases.kt",
            "MongoDatabasesWithClientSession.kt" to "MongoDatabasesWithClientSession.kt",
            "FindIterables.kt" to "FindFlows.kt",
            "DistinctIterables.kt" to "DistinctFlows.kt",
            "SyncProperties.kt" to "CoroutineProperties.kt",
        )

        private val TYPES = linkedMapOf(
            "FindIterable" to "FindFlow",
            "AggregateIterable" to "AggregateFlow",
            "DistinctIterable" to "DistinctFlow",
            "ListIndexesIterable" to "ListIndexesFlow",
            "ChangeStreamIterable" to "ChangeStreamFlow",
        )

        private val FUNCTION_START = Regex("""^((?:@\w+(?:\([^)]*\))? )*)(public )?(inline )?fun """)
        private val SIGNATURE_END_BLOCK = Regex("""\)\s*(:\s*[^={]+)?\s*[={]\s*$""")
        private val SIGNATURE_END_EXPRESSION = Regex("""\)\s*(:\s*[^={]+)?\s*=\s*\S""")
        private val RETURN_TYPE = Regex("""\)\s*:\s*([^={]+?)\s*[={]""")

        /** Functions returning these types are not suspending (cold flows or driver objects). */
        private val NON_SUSPENDING_RETURN_TYPE =
            Regex("""(FindFlow|AggregateFlow|DistinctFlow|ListIndexesFlow|ChangeStreamFlow|Flow|MongoCollection|MongoDatabase)<""")

        fun transform(syncSource: String, syncName: String): String {
            var s = syncSource
            s = s.replaceFirst(
                "package org.litote.kmongo\n",
                "// Generated from kmongo-dsl-sync $syncName by the generateCoroutineSources task: do not edit.\n\n" +
                        "package org.litote.kmongo.coroutine\n\nimport org.litote.kmongo.*\n"
            )
            TYPES.forEach { (sync, coroutine) ->
                s = s.replace(
                    "import com.mongodb.kotlin.client.$sync\n",
                    "import com.mongodb.kotlin.client.coroutine.$coroutine\n"
                )
                s = Regex("""\b$sync\b""").replace(s, coroutine)
            }
            s = s.replace(
                "import com.mongodb.kotlin.client.MongoIterable\n",
                "import kotlinx.coroutines.flow.Flow\nimport kotlinx.coroutines.flow.map\nimport kotlinx.coroutines.flow.firstOrNull\n"
            )
            s = Regex("""\bMongoIterable<""").replace(s, "Flow<")
            // the coroutine DistinctFlow type parameter is not nullable (the sync DistinctIterable one is)
            s = s.replace(
                "inline fun <reified T : Any, reified TResult> MongoCollection<T>.distinct(",
                "inline fun <reified T : Any, reified TResult : Any> MongoCollection<T>.distinct("
            )
            s = s.replace("    field: KProperty1<T, TResult>,", "    field: KProperty1<T, TResult?>,")
            s = Regex("""import com\.mongodb\.kotlin\.client\.(\w+)\n""")
                .replace(s, "import com.mongodb.kotlin.client.coroutine.$1\n")
            return addSuspendModifiers(s)
        }

        private fun addSuspendModifiers(source: String): String {
            val lines = source.split("\n")
            return lines.mapIndexed { i, line ->
                if (FUNCTION_START.toPattern().matcher(line).lookingAt() && !" $line".contains(" suspend ")) {
                    var signature = line
                    var j = i
                    while (!SIGNATURE_END_BLOCK.containsMatchIn(signature)
                        && !SIGNATURE_END_EXPRESSION.containsMatchIn(signature)
                        && j + 1 < lines.size
                    ) {
                        j++
                        signature += "\n" + lines[j]
                    }
                    val returnType = RETURN_TYPE.find(signature)?.groupValues?.get(1)?.trim() ?: "Unit"
                    if (NON_SUSPENDING_RETURN_TYPE.toPattern().matcher(returnType).lookingAt()) {
                        line
                    } else if (line.contains("inline fun ")) {
                        line.replaceFirst("inline fun ", "suspend inline fun ")
                    } else {
                        line.replaceFirst("fun ", "suspend fun ")
                    }
                } else {
                    line
                }
            }.joinToString("\n")
        }
    }
}
