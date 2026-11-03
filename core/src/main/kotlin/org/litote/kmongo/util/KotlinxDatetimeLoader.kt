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

public object KotlinxDatetimeLoader {

    /**
     * Call Class.forName multiple times can cause severe perf issue
     * if dependency kotlinx doesn't exist in project implement KMongo.
     * So the class is looked up only once (thread-safe).
     */
    private val isExistInClassLoader: Boolean by lazy {
        try {
            Class.forName("kotlinx.datetime.Instant")
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: LinkageError) {
            false
        }
    }

    /**
     * Returns [loadSerializersAndDeserializers] if kotlinx-datetime is available, [empty] otherwise.
     */
    public fun <T> loadKotlinxDateTime(loadSerializersAndDeserializers: () -> T, empty: () -> T): T =
        if (isExistInClassLoader) loadSerializersAndDeserializers() else empty()
}
