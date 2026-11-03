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

package org.litote.kmongo.jackson

import tools.jackson.core.JsonGenerator
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.io.IOContext
import de.undercouch.bson4jackson.BsonConstants
import de.undercouch.bson4jackson.BsonFactory
import de.undercouch.bson4jackson.BsonGenerator
import de.undercouch.bson4jackson.BsonParser
import de.undercouch.bson4jackson.io.ByteOrderUtil
import de.undercouch.bson4jackson.types.Timestamp
import org.bson.BsonTimestamp
import org.bson.types.Binary
import org.bson.types.ObjectId
import org.litote.kmongo.jackson.BsonModule.KMongoObjectId
import java.io.InputStream
import java.io.OutputStream

internal class KMongoBsonFactory : BsonFactory() {

    init {
        enable(BsonParser.Feature.HONOR_DOCUMENT_LENGTH)
        enable(BsonGenerator.Feature.WRITE_BIGDECIMALS_AS_DECIMAL128)
    }

    internal class KMongoBsonGenerator(
        writeCtxt: ObjectWriteContext,
        ctxt: IOContext,
        jsonFeatures: Int,
        bsonFeatures: Int,
        out: OutputStream
    ) : BsonGenerator(writeCtxt, ctxt, jsonFeatures, bsonFeatures, out) {

        override fun canWriteObjectId(): Boolean {
            return true;
        }

        override fun writeObjectId(objectId: Any): JsonGenerator {
            if (objectId !is ObjectId) {
                //generation with @JsonIdentityInfo - see https://github.com/Litote/kmongo/issues/135
                if (objectId is String) {
                    writeName("_id")
                    writeString(objectId)
                } else {
                    error("$objectId has to be ${ObjectId::class}")
                }
            } else {
                _writeArrayFieldNameIfNeeded()
                _verifyValueWrite("write object id")
                _buffer.putByte(_typeMarker, BsonConstants.TYPE_OBJECTID)
                objectId.toByteArray().forEach { _buffer.putByte(it) }
                flushBuffer()
            }
            return this
        }

        override fun writeObjectRef(id: Any?): JsonGenerator =
            if (id is String) {
                writeString(id)
            } else {
                super.writeObjectRef(id)
            }

        fun writeBinary(binary: Binary) {
            _writeArrayFieldNameIfNeeded()
            _verifyValueWrite("write binary")
            val bytes = binary.data
            _buffer.putByte(_typeMarker, BsonConstants.TYPE_BINARY)
            _buffer.putInt(bytes.size)
            _buffer.putByte(binary.type)
            _buffer.putBytes(*binary.data)
            flushBuffer()
        }

        fun writeBsonTimestamp(timestamp: BsonTimestamp) {
            _writeArrayFieldNameIfNeeded()
            _verifyValueWrite("write timestamp")
            _buffer.putByte(_typeMarker, BsonConstants.TYPE_TIMESTAMP)
            _buffer.putInt(timestamp.getInc())
            _buffer.putInt(timestamp.getTime())
            flushBuffer()
        }

        fun writeMinKey() {
            _writeArrayFieldNameIfNeeded()
            _verifyValueWrite("write min key")
            _buffer.putByte(_typeMarker, BsonConstants.TYPE_MINKEY)
            flushBuffer()
        }

        fun writeMaxKey() {
            _writeArrayFieldNameIfNeeded()
            _verifyValueWrite("write max key")
            _buffer.putByte(_typeMarker, BsonConstants.TYPE_MAXKEY)
            flushBuffer()
        }
    }

    private class KMongoBsonParser(
        readCtxt: ObjectReadContext,
        ctxt: IOContext,
        jsonFeatures: Int,
        bsonFeatures: Int,
        inputStream: InputStream
    ) : BsonParser(readCtxt, ctxt, jsonFeatures, bsonFeatures, inputStream) {

        override fun getEmbeddedObject(): Any {
            val embedded = super.getEmbeddedObject()
            if (embedded is KMongoObjectId) {
                return convertToNativeObjectId(embedded)
            }
            if (embedded is Timestamp) {
                return convertToBSONTimestamp(embedded)
            }
            return embedded
        }

        private fun convertToBSONTimestamp(ts: Timestamp): Any {
            return BsonTimestamp(ts.time, ts.inc)
        }

        private fun convertToNativeObjectId(id: KMongoObjectId): ObjectId {
            return createFromLegacyFormat(id.time, id.machine, id.inc)
        }

        override fun readObjectId(): de.undercouch.bson4jackson.types.ObjectId {
            val time = ByteOrderUtil.flip(_in.readInt())
            val machine = ByteOrderUtil.flip(_in.readInt())
            val inc = ByteOrderUtil.flip(_in.readInt())
            return KMongoObjectId(time, machine, inc)
        }
    }


    override fun _createUTF8Generator(
        writeCtxt: ObjectWriteContext,
        ctxt: IOContext,
        out: OutputStream
    ): BsonGenerator {
        val g = KMongoBsonGenerator(writeCtxt, ctxt, _streamWriteFeatures, _bsonGeneratorFeatures, out)
        if (_bsonCharacterEscapes != null) {
            g.setCharacterEscapes(_bsonCharacterEscapes)
        }
        return g
    }

    override fun _createParser(readCtxt: ObjectReadContext, ctxt: IOContext, inputStream: InputStream): BsonParser =
        KMongoBsonParser(readCtxt, ctxt, _streamReadFeatures, _bsonParserFeatures, inputStream)

    override fun copy(): BsonFactory {
        return KMongoBsonFactory()
    }

    companion object {

        private const val OBJECT_ID_LENGTH = 12

        fun createFromLegacyFormat(time: Int, machine: Int, inc: Int): ObjectId {
            return ObjectId(legacyToBytes(time, machine, inc))
        }

        private fun legacyToBytes(
            timestamp: Int,
            machineAndProcessIdentifier: Int,
            counter: Int
        ): ByteArray {
            val bytes = ByteArray(OBJECT_ID_LENGTH)
            bytes[0] = int3(timestamp)
            bytes[1] = int2(timestamp)
            bytes[2] = int1(timestamp)
            bytes[3] = int0(timestamp)
            bytes[4] = int3(machineAndProcessIdentifier)
            bytes[5] = int2(machineAndProcessIdentifier)
            bytes[6] = int1(machineAndProcessIdentifier)
            bytes[7] = int0(machineAndProcessIdentifier)
            bytes[8] = int3(counter)
            bytes[9] = int2(counter)
            bytes[10] = int1(counter)
            bytes[11] = int0(counter)
            return bytes
        }

        private fun int3(x: Int): Byte {
            return (x shr 24).toByte()
        }

        private fun int2(x: Int): Byte {
            return (x shr 16).toByte()
        }

        private fun int1(x: Int): Byte {
            return (x shr 8).toByte()
        }

        private fun int0(x: Int): Byte {
            return x.toByte()
        }

    }
}


