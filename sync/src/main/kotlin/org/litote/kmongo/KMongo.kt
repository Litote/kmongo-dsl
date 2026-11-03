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

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.kotlin.client.MongoClient
import org.bson.codecs.configuration.CodecRegistry
import org.litote.kmongo.util.KMongoClientSettings
import com.mongodb.client.MongoClient as JMongoClient

/**
 * Main object used to create a [MongoClient](https://mongodb.github.io/mongo-java-driver/) instance
 * of the official MongoDB Kotlin sync driver, configured with the KMongo object mapping.
 */
public object KMongo {

    /**
     * Creates a [MongoClient] instance with default connection string (localhost).
     */
    public fun createClient(): MongoClient = createClient("mongodb://localhost")

    /**
     * Creates a [MongoClient] with the given connection string.
     */
    public fun createClient(connectionString: String): MongoClient = createClient(ConnectionString(connectionString))

    /**
     * Creates a [MongoClient] with the given connection string.
     */
    public fun createClient(connectionString: ConnectionString): MongoClient =
        MongoClient.create(KMongoClientSettings.settings(connectionString), KMongoClientSettings.driverInformation)

    /**
     * Creates a [MongoClient] with the given settings (the codec registry is updated with KMongo mapping).
     */
    public fun createClient(settings: MongoClientSettings): MongoClient =
        MongoClient.create(KMongoClientSettings.settings(settings), KMongoClientSettings.driverInformation)

    /**
     * Creates a Java sync driver client (`com.mongodb.client.MongoClient`) with the given settings
     * (the codec registry is updated with KMongo mapping).
     *
     * Use it for the Java driver features that the official Kotlin driver does not provide (GridFS...),
     * and [createClient] (with the Java client) to get the Kotlin client sharing the same connection pool:
     * ```
     * val javaClient = KMongo.createJavaClient(settings)
     * val client = KMongo.createClient(javaClient)
     * val bucket = GridFSBuckets.create(javaClient.getDatabase("db"))
     * ```
     */
    public fun createJavaClient(settings: MongoClientSettings): JMongoClient =
        MongoClients.create(KMongoClientSettings.settings(settings), KMongoClientSettings.driverInformation)

    /**
     * Creates a Java sync driver client (`com.mongodb.client.MongoClient`) with the given connection string
     * (see [createJavaClient]).
     */
    public fun createJavaClient(connectionString: String): JMongoClient =
        MongoClients.create(KMongoClientSettings.settings(ConnectionString(connectionString)), KMongoClientSettings.driverInformation)

    /**
     * Wraps a Java sync driver client in a Kotlin sync driver client (they share the same connection pool,
     * closing one closes the other).
     *
     * The codec registry of the Java client is not modified: create it with [createJavaClient] to use the KMongo mapping.
     */
    public fun createClient(javaClient: JMongoClient): MongoClient = MongoClient(javaClient)

    internal fun configureRegistry(codecRegistry: CodecRegistry = MongoClientSettings.getDefaultCodecRegistry()): CodecRegistry =
        KMongoClientSettings.configureRegistry(codecRegistry)
}
