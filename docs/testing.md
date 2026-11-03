# Testing

`kmongo-dsl-testcontainers` provides a MongoDB server shared by all the tests of a JVM, started with
[Testcontainers](https://java.testcontainers.org/modules/databases/mongodb/) (Docker required)
as a single node **replica set**: transactions and change streams are available.

```kotlin
testImplementation("org.litote.kmongo:kmongo-dsl-testcontainers")
```

```kotlin
val client = KMongo.createClient(KMongoTestServer.connectionString)
val database = client.getDatabase(KMongoTestServer.newDatabaseName())
```

- the image is set with the `kmongo.test.mongodb.image` system property (default `mongo:8.2`)
- set the `kmongo.test.mongodb.uri` system property (or the `KMONGO_TEST_MONGODB_URI` environment variable)
  to use an existing server instead of a container
