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

package org.litote.kmongo

import com.mongodb.kotlin.client.MongoClient
import com.mongodb.kotlin.client.MongoCollection
import com.mongodb.kotlin.client.MongoDatabase
import org.junit.jupiter.api.AfterEach
import org.litote.kmongo.test.MongoTestServer
import org.litote.kmongo.util.KMongoUtil
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

/**
 * Shared sync client.
 */
object SyncTestClient {
    val mongoClient: MongoClient by lazy { KMongo.createClient(MongoTestServer.connectionString) }
    val database: MongoDatabase by lazy { mongoClient.getDatabase("test") }
}

/**
 * Base class for sync tests: provides a collection of [T] dropped after each test.
 */
abstract class KMongoBaseTest<T : Any> : KMongoRootTest() {

    val mongoClient: MongoClient get() = SyncTestClient.mongoClient

    val database: MongoDatabase get() = SyncTestClient.database

    val col: MongoCollection<T> by lazy {
        getCollection(KMongoUtil.defaultCollectionName(getDefaultCollectionClass()), getDefaultCollectionClass())
    }

    inline fun <reified T : Any> getCollection(): MongoCollection<T> =
        database.getCollection(KMongoUtil.defaultCollectionName(T::class), T::class.java)

    fun <T : Any> getCollection(name: String, clazz: KClass<T>): MongoCollection<T> =
        database.getCollection(name, clazz.java)

    inline fun <reified T : Any> dropCollection() = dropCollection(KMongoUtil.defaultCollectionName(T::class))

    fun dropCollection(collectionName: String) {
        database.getCollection<org.bson.Document>(collectionName).drop()
    }

    @AfterEach
    fun dropDefaultCollection() {
        col.drop()
    }

    @Suppress("UNCHECKED_CAST")
    fun getDefaultCollectionClass(): KClass<T> =
        (this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments[0].let {
            (it as? Class<T>)?.kotlin ?: ((it as ParameterizedType).rawType as Class<T>).kotlin
        }
}
