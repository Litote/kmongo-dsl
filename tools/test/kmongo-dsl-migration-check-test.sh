#!/usr/bin/env bash
#
# Tests of tools/kmongo-dsl-migration-check.sh (run by the CI).

set -euo pipefail

script="$(cd "$(dirname "$0")/.." && pwd)/kmongo-dsl-migration-check.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

assert_contains() { if ! grep -qF -- "$2" <<<"$1"; then echo "FAILED $3: '$2' not found"; failures=$((failures + 1)); fi; }
assert_not_contains() { if grep -qF -- "$2" <<<"$1"; then echo "FAILED $3: '$2' found"; failures=$((failures + 1)); fi; }

# --- checks
mkdir -p "$work/check/src"
cat > "$work/check/src/Dao.kt" <<'EOF'
import com.mongodb.kotlin.client.MongoCollection

fun one(col: MongoCollection<Friend>) = col.find(Friend::name eq "a").first()
fun sorted(col: MongoCollection<Friend>) = col.find(and(Friend::name eq "a", Friend::age gt 1))
    .sort(descending(Friend::age))
    .first()
fun list(friends: List<Friend>) = friends.first()
fun fromList(col: MongoCollection<Friend>) = col.find().toList().first()
fun safe(col: MongoCollection<Friend>) = col.find().firstOrNull()
fun keys(col: MongoCollection<Friend>) = col.dropIndex("{name:1}")
fun sessionKeys(col: MongoCollection<Friend>) = col.dropIndex(session, "{name:1}")
fun name(col: MongoCollection<Friend>) = col.dropIndex("name_1")
EOF
cat > "$work/check/JacksonConfig.kt" <<'EOF'
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.module.SimpleModule
EOF
cat > "$work/check/pom.xml" <<'EOF'
<dependency><groupId>org.litote.kmongo</groupId><artifactId>kmongo-dsl-sync</artifactId></dependency>
<dependency><groupId>org.litote.kmongo</groupId><artifactId>kmongo-coroutine</artifactId></dependency>
<dependency><groupId>org.litote.kmongo</groupId><artifactId>kmongo-id</artifactId></dependency>
<annotationProcessorPath><groupId>org.litote.jackson</groupId><artifactId>jackson-generator</artifactId></annotationProcessorPath>
<dependency><groupId>org.litote.jackson</groupId><artifactId>jackson-data</artifactId></dependency>
<dependency><groupId>org.litote.jackson</groupId><artifactId>jackson-module-loader</artifactId></dependency>
<dependency><groupId>org.litote.kmongo</groupId><artifactId>kmongo-dsl-jackson-data</artifactId></dependency>
EOF
cat > "$work/check/build.gradle.kts" <<'EOF'
implementation("org.litote.kmongo:kmongo-dsl-coroutine:5.14.0")
implementation("org.litote.kmongo:kmongo:5.12.0")
EOF
output=$("$script" "$work/check" || true)
assert_contains "$output" "Dao.kt:3: fun one" "first() on find()"
assert_contains "$output" "Dao.kt:4: fun sorted" "first() on a multiline find().sort()"
assert_not_contains "$output" "Dao.kt:7:" "List.first()"
assert_not_contains "$output" "Dao.kt:8:" "toList().first()"
assert_not_contains "$output" "Dao.kt:9:" "firstOrNull()"
assert_contains "$output" "Dao.kt:10:fun keys" "dropIndex with JSON keys"
assert_contains "$output" "Dao.kt:11:fun sessionKeys" "dropIndex with a session and JSON keys"
assert_not_contains "$output" "Dao.kt:12:" "dropIndex with a name"
assert_contains "$output" "JacksonConfig.kt:2:" "Jackson 2 databind import"
assert_not_contains "$output" "JacksonConfig.kt:1:" "Jackson annotation import"
assert_contains "$output" "<artifactId>kmongo-coroutine</artifactId>" "KMongo 4/5 Maven artifact"
assert_contains "$output" "org.litote.kmongo:kmongo:5.12.0" "KMongo 4/5 Gradle artifact"
assert_not_contains "$output" "kmongo-dsl-sync" "kmongo-dsl Maven artifact"
assert_not_contains "$output" "kmongo-dsl-coroutine" "kmongo-dsl Gradle artifact"
assert_not_contains "$output" "<artifactId>kmongo-dsl-jackson-data</artifactId>" "kmongo-dsl-jackson-data artifact"
assert_contains "$output" "<artifactId>kmongo-id</artifactId>" "kmongo-id artifact"
assert_contains "$output" "<artifactId>jackson-generator</artifactId>" "jackson-generator"
assert_contains "$output" "<artifactId>jackson-data</artifactId>" "jackson-data"
assert_contains "$output" "<artifactId>jackson-module-loader</artifactId>" "jackson-module-loader"

# --- --add-imports (Kotlin 2 compiler messages, Maven and Gradle formats)
mkdir -p "$work/imports"
dao="$work/imports/FriendDao.kt"
cat > "$dao" <<'EOF'
package sample

import com.mongodb.kotlin.client.MongoCollection
import com.mongodb.kotlin.client.MongoDatabase
import java.time.Instant

class FriendDao(database: MongoDatabase) {
    private val col: MongoCollection<Friend> = database.getCollection<Friend>()
    fun byName(name: String): Friend? = col.findOne(Friend::name eq name)
    fun names(): List<String> = col.find().filter { it.name != "" }.map { it.name }
    fun all(): List<Friend> = buildList { for (friend in col.find()) add(friend) }
}
EOF
coroutine="$work/imports/CoroutineDao.kt"
cat > "$coroutine" <<'EOF'
package sample

import com.mongodb.kotlin.client.coroutine.MongoCollection

class CoroutineDao(private val col: MongoCollection<Friend>) {
    suspend fun byName(name: String): Friend? = col.findOne(Friend::name eq name)
}
EOF
cat > "$work/imports/build.log" <<EOF
[INFO] --- kotlin:2.4.21:compile (compile) @ sample ---
[ERROR] file://$dao:8:57 None of the following candidates is applicable:
[ERROR] 
[ERROR] fun <reified T : Any> getCollection(collectionName: String): MongoCollection<T>
[ERROR] fun <T : Any> getCollection(collectionName: String, resultClass: Class<T>): MongoCollection<T>:
[ERROR]   No value passed for parameter 'resultClass'.
[ERROR] file://$dao:9:45 Unresolved reference 'findOne' on receiver of type 'MongoCollection<Friend>'.
[ERROR] file://$dao:9:66 Unresolved reference 'eq' on receiver of type 'KProperty1<Friend, String>'.
[ERROR] file://$dao:10:51 Argument type mismatch: actual type is '() -> Boolean', but 'Bson?' was expected.
[ERROR] file://$dao:11:51 For-loop range must have an 'iterator()' method.
[INFO] BUILD FAILURE
e: file://$coroutine:6:53 Unresolved reference 'findOne'.
e: file://$coroutine:6:74 Unresolved reference 'eq'.
e: file://$coroutine:6:80 Unresolved reference 'unknownFunction'.
EOF
output=$("$script" --add-imports "$work/imports/build.log")
assert_contains "$output" "7 import(s) added" "imports count"
expected_imports="import com.mongodb.kotlin.client.MongoCollection
import com.mongodb.kotlin.client.MongoDatabase
import org.litote.kmongo.eq
import org.litote.kmongo.filter
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import org.litote.kmongo.iterator
import java.time.Instant"
actual_imports=$(grep '^import ' "$dao")
[ "$actual_imports" = "$expected_imports" ] || { echo "FAILED sync imports:"; echo "$actual_imports"; failures=$((failures + 1)); }
expected_imports="import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.litote.kmongo.coroutine.findOne
import org.litote.kmongo.eq"
actual_imports=$(grep '^import ' "$coroutine")
[ "$actual_imports" = "$expected_imports" ] || { echo "FAILED coroutine imports:"; echo "$actual_imports"; failures=$((failures + 1)); }
# idempotent
output=$("$script" --add-imports "$work/imports/build.log")
assert_contains "$output" "0 import(s) added" "second run"

if [ $failures -eq 0 ]; then echo "kmongo-dsl-migration-check tests: OK"; else echo "kmongo-dsl-migration-check tests: $failures failure(s)"; exit 1; fi
