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

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.deser.DeserializationProblemHandler
import org.litote.kmongo.Id
import org.litote.kmongo.MongoOperator.oid
import org.litote.kmongo.id.WrappedObjectId

/**
 *
 */
internal object StringDeserializationProblemHandler : DeserializationProblemHandler() {

    override fun handleUnexpectedToken(
        ctxt: DeserializationContext,
        targetType: JavaType,
        t: JsonToken,
        p: JsonParser,
        failureMsg: String?
    ): Any {
        if (t == JsonToken.START_OBJECT && p.nextName() == "$oid") {
            when (targetType.rawClass) {
                //handle ObjectId -> String mapping
                String::class.java -> return readObjectId(p)
                //handle ObjectId -> Id mapping
                Id::class.java -> return WrappedObjectId<Any>(readObjectId(p))
            }
        }
        return super.handleUnexpectedToken(ctxt, targetType, t, p, failureMsg)
    }

    /** Reads the value of the `$oid` field, and moves the parser to the end of the object. */
    private fun readObjectId(p: JsonParser) =
        p.nextStringValue().also {
            while (p.currentToken() != JsonToken.END_OBJECT) {
                p.nextToken()
            }
        }
}
