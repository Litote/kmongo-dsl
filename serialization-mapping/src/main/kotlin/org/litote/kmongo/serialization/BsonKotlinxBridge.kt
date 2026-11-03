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

package org.litote.kmongo.serialization

import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.AbstractBsonReader
import org.bson.AbstractBsonWriter
import org.bson.BsonWriter
import org.bson.codecs.kotlinx.BsonDecoder
import org.bson.codecs.kotlinx.BsonEncoder
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Access to the underlying [AbstractBsonReader] / [BsonWriter] of the official `bson-kotlinx`
 * encoders and decoders.
 *
 * `bson-kotlinx` does not expose them publicly, but a few KMongo serializers need them
 * to stay compatible with the KMongo 4/5 BSON format (map keys of type `Id`, mostly).
 */
internal object BsonKotlinxBridge {

    private val fields = ConcurrentHashMap<Pair<Class<*>, String>, Field>()

    private fun field(type: Class<*>, name: String): Field {
        fields[type to name]?.let { return it }
        var c: Class<*>? = type
        while (c != null) {
            val f = c.declaredFields.firstOrNull { it.name == name }
            if (f != null) {
                f.isAccessible = true
                fields[type to name] = f
                return f
            }
            c = c.superclass
        }
        error("Field '$name' not found in ${type.name}: this bson-kotlinx version is not supported by KMongo")
    }

    fun reader(decoder: Decoder): AbstractBsonReader? =
        (decoder as? BsonDecoder)?.let { field(it.javaClass, "reader").get(it) as AbstractBsonReader }

    fun writer(encoder: Encoder): BsonWriter? =
        (encoder as? BsonEncoder)?.let { field(it.javaClass, "writer").get(it) as BsonWriter }

    /**
     * True if the encoder is waiting for a document field name (ie the current value is a map key).
     */
    fun isWritingName(encoder: Encoder): Boolean =
        (writer(encoder) as? AbstractBsonWriter)
            ?.let { field(AbstractBsonWriter::class.java, "state").get(it) == AbstractBsonWriter.State.NAME }
            ?: false

    /**
     * True if the decoder is positioned on a document field name (ie the current value is a map key).
     */
    fun isReadingName(decoder: Decoder): Boolean =
        reader(decoder)?.state == AbstractBsonReader.State.NAME
}
