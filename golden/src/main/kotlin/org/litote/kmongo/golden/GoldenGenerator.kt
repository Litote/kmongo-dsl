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

package org.litote.kmongo.golden

import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.codecs.Codec
import org.bson.codecs.EncoderContext
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.litote.kmongo.service.ClassMappingType
import org.litote.kmongo.util.KMongoUtil
import org.litote.kmongo.util.ObjectMappingConfiguration
import java.io.File

/**
 * Encodes the golden samples with KMongo 5.12 (the mapping is selected by the classpath).
 *
 * args: output directory, mapping name (jackson|serialization)
 */
fun main(args: Array<String>) {
    val output = File(args[0]).apply { mkdirs() }
    val serialization = args[1] == "serialization"
    val registry = ClassMappingType.codecRegistry(KMongoUtil.defaultCodecRegistry)
    for (serializeNull in listOf(true, false)) {
        ObjectMappingConfiguration.serializeNull = serializeNull
        goldenSamples(serialization).forEach { (name, value) ->
            val document = BsonDocument()
            @Suppress("UNCHECKED_CAST")
            (registry.get(value.javaClass) as Codec<Any>)
                .encode(BsonDocumentWriter(document), value, EncoderContext.builder().build())
            val file = File(output, goldenFileName(name, serializeNull))
            file.writeText(document.toJson(JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).indent(true).build()) + "\n")
            println("generated $file")
        }
    }
}

