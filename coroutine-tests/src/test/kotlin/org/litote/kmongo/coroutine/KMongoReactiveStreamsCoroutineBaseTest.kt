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

package org.litote.kmongo.coroutine

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.litote.kmongo.KMongoRootTest
import org.litote.kmongo.test.MongoTestServer
import org.litote.kmongo.util.KMongoUtil
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

/**
 * Shared coroutine client.
 */
object CoroutineTestClient {
    val mongoClient: MongoClient by lazy { KMongo.createClient(MongoTestServer.connectionString) }
    val database: MongoDatabase by lazy { mongoClient.getDatabase("test") }
}

/**
 * Base class for coroutine tests: provides a collection of [T] dropped after each test.
 * (no @Tag here: JUnit5 tags are cumulative, they are set on each subclass)
 */
abstract class KMongoReactiveStreamsCoroutineBaseTest<T : Any> : KMongoRootTest() {

    val mongoClient: MongoClient get() = CoroutineTestClient.mongoClient

    val database: MongoDatabase get() = CoroutineTestClient.database

    val col: MongoCollection<T> by lazy {
        database.getCollection(KMongoUtil.defaultCollectionName(getDefaultCollectionClass()), getDefaultCollectionClass().java)
    }

    inline fun <reified T : Any> getCollection(): MongoCollection<T> =
        database.getCollection(KMongoUtil.defaultCollectionName(T::class), T::class.java)

    @AfterEach
    fun dropDefaultCollection() = runBlocking {
        col.drop()
    }

    @Suppress("UNCHECKED_CAST")
    open fun getDefaultCollectionClass(): KClass<T> =
        ((this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>).kotlin
}
