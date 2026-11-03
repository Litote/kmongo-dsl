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

package org.litote.kmongo.reactivestreams

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import org.litote.kmongo.coroutine.KMongo as CoroutineKMongo

/**
 * Kept for KMongo 4/5 source compatibility (`KMongo.createClient().coroutine`).
 */
@Deprecated(
    "kmongo-dsl: use org.litote.kmongo.coroutine.KMongo",
    ReplaceWith("KMongo", "org.litote.kmongo.coroutine.KMongo")
)
public object KMongo {

    public fun createClient(): MongoClient = CoroutineKMongo.createClient()

    public fun createClient(connectionString: String): MongoClient = CoroutineKMongo.createClient(connectionString)

    public fun createClient(connectionString: ConnectionString): MongoClient = CoroutineKMongo.createClient(connectionString)

    public fun createClient(settings: MongoClientSettings): MongoClient = CoroutineKMongo.createClient(settings)
}
