# Testing

`kmongo-dsl-testcontainers` provides a MongoDB server shared by all the tests of a JVM, started with
[Testcontainers](https://java.testcontainers.org/modules/databases/mongodb/) (Docker required)
as a single node **replica set**: transactions and change streams are available.

It replaces `kmongo-flapdoodle` (KMongo 4/5).

## Dependency

With the [BOM](quick-start.md#choose-the-flavor-and-the-object-mapping):

*(Gradle)*
```kotlin
testImplementation("org.litote.kmongo:kmongo-dsl-testcontainers")
```

*(Maven)*
```xml
<dependency>
  <groupId>org.litote.kmongo</groupId>
  <artifactId>kmongo-dsl-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
```

## A JUnit test

The container is started at the first `KMongoTestServer.connectionString` call, and removed by Testcontainers when the JVM exits.
`newDatabaseName()` returns a new database name at each call (`test_1`, `test_2`...): the test classes do not share
their data, even when they run in parallel.

### Sync

```kotlin
import org.litote.kmongo.*
import org.litote.kmongo.testcontainers.KMongoTestServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

data class Jedi(val name: String, val age: Int)

class JediRepositoryTest {

    private val client = KMongo.createClient(KMongoTestServer.connectionString)
    private val database = client.getDatabase(KMongoTestServer.newDatabaseName())
    private val col = database.getCollection<Jedi>()

    @AfterTest
    fun tearDown() {
        database.drop()
        client.close()
    }

    @Test
    fun `find the young jedis`() {
        col.insertMany(listOf(Jedi("Luke Skywalker", 19), Jedi("Yoda", 896)))

        val young = col.find(Jedi::age lt 100).toList()

        assertEquals(listOf("Luke Skywalker"), young.map { it.name })
    }
}
```

### Coroutine

```kotlin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.testcontainers.KMongoTestServer
import kotlin.test.Test
import kotlin.test.assertEquals

class CoroutineJediRepositoryTest {

    private val client = KMongo.createClient(KMongoTestServer.connectionString)
    private val col = client.getDatabase(KMongoTestServer.newDatabaseName()).getCollection<Jedi>()

    @Test
    fun `find the young jedis`() = runBlocking {
        col.insertMany(listOf(Jedi("Luke Skywalker", 19), Jedi("Yoda", 896)))

        assertEquals(listOf("Luke Skywalker"), col.find(Jedi::age lt 100).toList().map { it.name })
    }
}
```

## Configuration

| System property | Environment variable | Default | Usage |
|---|---|---|---|
| `kmongo.test.mongodb.image` | | `mongo:8.2` | the MongoDB Docker image |
| `kmongo.test.mongodb.uri` | `KMONGO_TEST_MONGODB_URI` | | an existing server: no container is started |

With an existing server (a CI service container for example), use a replica set if your tests need
transactions or change streams.

For example, to run the tests against MongoDB 7.0 with Gradle:

```kotlin
tasks.test {
    systemProperty("kmongo.test.mongodb.image", "mongo:7.0")
}
```
