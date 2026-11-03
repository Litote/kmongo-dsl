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

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import org.litote.kmongo.util.KMongoClientSettings

/**
 * Creates [MongoClient] instances of the official MongoDB Kotlin coroutine driver,
 * configured with the KMongo object mapping.
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
}
