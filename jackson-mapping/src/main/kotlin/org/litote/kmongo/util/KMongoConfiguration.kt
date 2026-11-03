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

package org.litote.kmongo.util

import com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import org.bson.UuidRepresentation
import org.litote.kmongo.jackson.JacksonCodecProvider
import org.litote.kmongo.jackson.ObjectMapperFactory
import org.litote.kmongo.util.KotlinModuleConfiguration.kotlinModuleInitializer

/**
 * Configure the jackson mapper engine.
 *
 * Call the methods of this object *before* any call to [KMongoConfiguration] methods.
 */
public object KMongoJacksonFeature {

    /**
     * Set the [UuidRepresentation] for the serialized version of the UUID class.
     */
    public fun setUUIDRepresentation(uuidRepresentation: UuidRepresentation?) {
        KMongoConfiguration.bsonMapper = ObjectMapperFactory.createBsonObjectMapper(uuidRepresentation)
        KMongoConfiguration.bsonMapperCopy = ObjectMapperFactory.createBsonObjectMapperCopy(uuidRepresentation)
    }
}

/**
 * Configure the [KotlinModule] used by jackson mapper engine.
 *
 * Call the methods of this object *before* any call to [KMongoConfiguration] methods.
 */
public object KotlinModuleConfiguration {

    /**
     * Function to customize [KotlinModule] used by KMongo.
     */
    public var kotlinModuleInitializer: KotlinModule.Builder.() -> Unit = { }
}

/**
 * Use this class to customize the default behaviour of KMongo jackson bindings.
 */
public object KMongoConfiguration {

    /**
     * Manage mongo extended json format.
     */
    @Volatile
    public var extendedJsonMapper: JsonMapper = ObjectMapperFactory.createExtendedJsonObjectMapper()

    /**
     * Manage bson format.
     *
     * Assign it before creating the client: the existing codecs keep the previous mapper.
     */
    @Volatile
    public var bsonMapper: JsonMapper = ObjectMapperFactory.createBsonObjectMapper()
        set(value) {
            field = value
            refreshBsonMappers()
        }

    /**
     * Basically a copy of [bsonMapper] without [org.litote.kmongo.jackson.KMongoBsonFactory].
     * Used by [org.litote.kmongo.jackson.JacksonCodec] to resolves specific serialization issues.
     *
     * Assign it before creating the client: the existing codecs keep the previous mapper.
     */
    @Volatile
    public var bsonMapperCopy: JsonMapper = ObjectMapperFactory.createBsonObjectMapperCopy()
        set(value) {
            field = value
            refreshBsonMappers()
        }

    @Volatile
    private var currentJacksonCodecProvider: JacksonCodecProvider? = null

    internal val jacksonCodecProvider: JacksonCodecProvider
        get() = currentJacksonCodecProvider ?:
        (JacksonCodecProvider(bsonMapper, bsonMapperCopy).apply { currentJacksonCodecProvider = this })

    @Volatile
    private var currentFilterIdBsonMapper: JsonMapper? = null

    internal val filterIdBsonMapper: JsonMapper
        get() {
            if (currentFilterIdBsonMapper == null) {
                currentFilterIdBsonMapper = ObjectMapperFactory.createFilterIdObjectMapper(bsonMapper)
            }
            return currentFilterIdBsonMapper!!
        }

    @Volatile
    private var currentBsonMapperWithoutNullSerialization: JsonMapper? = null

    @Volatile
    private var currentFilterIdBsonMapperWithoutNullSerialization: JsonMapper? = null

    @Volatile
    private var currentBsonMapperWithNullSerialization: JsonMapper? = null

    @Volatile
    private var currentFilterIdBsonMapperWithNullSerialization: JsonMapper? = null

    internal val bsonMapperWithoutNullSerialization: JsonMapper
        get() {
            if (currentBsonMapperWithoutNullSerialization == null) {
                currentBsonMapperWithoutNullSerialization = ObjectMapperFactory.withInclusion(bsonMapper, NON_NULL)
            }
            return currentBsonMapperWithoutNullSerialization!!
        }

    internal val filterIdBsonMapperWithoutNullSerialization: JsonMapper
        get() {
            if (currentFilterIdBsonMapperWithoutNullSerialization == null) {
                currentFilterIdBsonMapperWithoutNullSerialization =
                        ObjectMapperFactory.withInclusion(filterIdBsonMapper, NON_NULL)
            }
            return currentFilterIdBsonMapperWithoutNullSerialization!!
        }

    internal val bsonMapperWithNullSerialization: JsonMapper
        get() {
            if (currentBsonMapperWithNullSerialization == null) {
                currentBsonMapperWithNullSerialization = ObjectMapperFactory.withInclusion(bsonMapper, ALWAYS)
            }
            return currentBsonMapperWithNullSerialization!!
        }

    internal val filterIdBsonMapperWithNullSerialization: JsonMapper
        get() {
            if (currentFilterIdBsonMapperWithNullSerialization == null) {
                currentFilterIdBsonMapperWithNullSerialization =
                        ObjectMapperFactory.withInclusion(filterIdBsonMapper, ALWAYS)
            }
            return currentFilterIdBsonMapperWithNullSerialization!!
        }

    /**
     * Register a jackson [JacksonModule] for the two bson mappers, [bsonMapper] and [bsonMapperCopy].
     *
     * The Jackson mappers are immutable: this function replaces them. Call it before creating the client,
     * the existing codecs keep the previous mappers.
     *
     * For example, if you need to manage [DBRefs](https://docs.mongodb.com/manual/reference/database-references/) autoloading,
     * you can write this kind of module:
     *
     *      class KMongoBeanDeserializer(deserializer:BeanDeserializer) : ThrowableDeserializer(deserializer) {
     *
     *              override fun deserializeFromObject(jp: JsonParser, ctxt: DeserializationContext): Any? {
     *                   if(jp.currentName() == "\$ref") {
     *                       val ref = jp.nextStringValue()
     *                       jp.nextValue()
     *                       val id = jp.getValueAsString()
     *                       while(jp.currentToken() != JsonToken.END_OBJECT) jp.nextToken()
     *                       return database.getCollection(ref).withDocumentClass(_beanType.rawClass).findOneById(id)
     *                    } else {
     *                       return super.deserializeFromObject(jp, ctxt)
     *                    }
     *                   }
     *               }
     *
     *       class KMongoBeanDeserializerModifier : ValueDeserializerModifier() {
     *
     *              override fun modifyDeserializer(config: DeserializationConfig, beanDesc: BeanDescription.Supplier, deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
     *                  return if(deserializer is BeanDeserializer) {
     *                          KMongoBeanDeserializer( deserializer)
     *                         } else {
     *                          deserializer
     *                         }
     *                  }
     *              }
     *
     *       KMongoConfiguration.registerBsonModule(SimpleModule().setDeserializerModifier(KMongoBeanDeserializerModifier()))
     */
    public fun registerBsonModule(module: JacksonModule) {
        bsonMapper = bsonMapper.rebuild().addModule(module).build()
        bsonMapperCopy = bsonMapperCopy.rebuild().addModule(module).build()
    }

    /**
     * Reset the jackson configuration.
     *
     * Useful if you need to manage hot class reloading (see https://github.com/Litote/kmongo/issues/75 )
     *
     * Usage:
     *
     *  KMongoConfiguration.registerBsonModule(MyModule())
     *  client = KMongo.createClient(..)
     *
     *  // then reloading
     *  KMongoConfiguration.resetConfiguration()
     *  KMongoConfiguration.registerBsonModule(MyModule())
     *  client = KMongo.createClient(..)
     */
    public fun resetConfiguration() {
        extendedJsonMapper = ObjectMapperFactory.createExtendedJsonObjectMapper()
        bsonMapper = ObjectMapperFactory.createBsonObjectMapper()
        bsonMapperCopy = ObjectMapperFactory.createBsonObjectMapperCopy()
    }

    /** Applies the new values of [bsonMapper] and [bsonMapperCopy] to the codec provider and to the derived mappers. */
    private fun refreshBsonMappers() {
        currentJacksonCodecProvider?.apply {
            bsonObjectMapper = bsonMapper
            notBsonObjectMapper = bsonMapperCopy
        }
        currentFilterIdBsonMapper = null
        currentBsonMapperWithNullSerialization = null
        currentBsonMapperWithoutNullSerialization = null
        currentFilterIdBsonMapperWithNullSerialization = null
        currentFilterIdBsonMapperWithoutNullSerialization = null
    }

}
