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

package org.litote.kmongo.testcontainers

import org.testcontainers.mongodb.MongoDBContainer
import java.util.concurrent.atomic.AtomicInteger

/**
 * MongoDB server shared by all the tests of a JVM - the KMongo DSL replacement of `kmongo-flapdoodle`.
 *
 * - if the `kmongo.test.mongodb.uri` system property (or the `KMONGO_TEST_MONGODB_URI` environment variable) is set,
 *   this server is used
 * - else a MongoDB container (a single node **replica set**: transactions and change streams are available)
 *   is started with [Testcontainers](https://java.testcontainers.org/) at the first use, and stopped at the JVM shutdown.
 *   The image can be set with the `kmongo.test.mongodb.image` system property (default [DEFAULT_IMAGE]).
 *
 * Sample:
 * ```
 * val client = KMongo.createClient(KMongoTestServer.connectionString)
 * val database = client.getDatabase(KMongoTestServer.newDatabaseName())
 * ```
 */
public object KMongoTestServer {

    /** The default MongoDB image. */
    public const val DEFAULT_IMAGE: String = "mongo:8.2"

    /** The system property of the MongoDB image. */
    public const val IMAGE_PROPERTY: String = "kmongo.test.mongodb.image"

    /** The system property of the external MongoDB server uri. */
    public const val URI_PROPERTY: String = "kmongo.test.mongodb.uri"

    /** The environment variable of the external MongoDB server uri. */
    public const val URI_ENV: String = "KMONGO_TEST_MONGODB_URI"

    private val databaseCounter = AtomicInteger()

    private val externalUri: String?
        get() = System.getProperty(URI_PROPERTY) ?: System.getenv(URI_ENV)

    private val container: MongoDBContainer? by lazy {
        if (externalUri != null) {
            null
        } else {
            MongoDBContainer(System.getProperty(IMAGE_PROPERTY) ?: DEFAULT_IMAGE)
                .withReplicaSet()
                .apply { start() }
        }
    }

    /**
     * The connection string of the test server (the container is started at the first call).
     */
    public val connectionString: String by lazy {
        externalUri ?: container!!.replicaSetUrl
    }

    /**
     * Returns a new database name (`test_1`, `test_2`...), to isolate the tests sharing the same server.
     */
    public fun newDatabaseName(prefix: String = "test"): String = "${prefix}_${databaseCounter.incrementAndGet()}"
}
