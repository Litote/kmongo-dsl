# Quick Start

## Choose the flavor and the object mapping

A KMongo DSL setup is:

- a **flavor**: the official MongoDB Kotlin **sync** driver (`kmongo-dsl-sync`) or **coroutine** driver (`kmongo-dsl-coroutine`)
- an **object mapping**: [Jackson](https://github.com/FasterXML/jackson) (`kmongo-dsl-jackson-mapping`)
  or [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (`kmongo-dsl-serialization-mapping`),
  see the [dedicated chapter](object-mapping.md#how-to-choose-the-mapping-engine)

The `kmongo-dsl-<flavor>-<mapping>` artifacts bring both:

| Flavor    | Jackson mapping             | kotlinx.serialization mapping     |
|-----------|-----------------------------|-----------------------------------|
| sync      | `kmongo-dsl-sync-jackson`      | `kmongo-dsl-sync-serialization`      |
| coroutine | `kmongo-dsl-coroutine-jackson` | `kmongo-dsl-coroutine-serialization` |

If you don't know, start with the sync flavor and the Jackson mapping:

- with Maven

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.litote.kmongo</groupId>
      <artifactId>kmongo-dsl-bom</artifactId>
      <version>5.14.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.litote.kmongo</groupId>
    <artifactId>kmongo-dsl-sync-jackson</artifactId>
  </dependency>
</dependencies>
```

- or Gradle

*(Kotlin)*
```kotlin
implementation(platform("org.litote.kmongo:kmongo-dsl-bom:5.14.0"))
implementation("org.litote.kmongo:kmongo-dsl-sync-jackson")
```
*(Groovy)*
```groovy
implementation platform('org.litote.kmongo:kmongo-dsl-bom:5.14.0')
implementation 'org.litote.kmongo:kmongo-dsl-sync-jackson'
```

The BOM (`kmongo-dsl-bom`) aligns the versions of the KMongo DSL artifacts and of the MongoDB driver.
The KMongo DSL version follows the version of the MongoDB Kotlin driver it is built on (5.14.0: driver 5.14.0).

Latest version: [![Maven Central](https://img.shields.io/maven-central/v/org.litote.kmongo/kmongo-dsl-core)](https://central.sonatype.com/artifact/org.litote.kmongo/kmongo-dsl-core)

> Requirements: JDK 17+, Kotlin 2.4+, MongoDB server 5.0+.

And [start coding](#lets-start-coding).

### Coroutine flavor

Replace `kmongo-dsl-sync-jackson` by `kmongo-dsl-coroutine-jackson` (Jackson mapping)
or `kmongo-dsl-coroutine-serialization` (kotlinx.serialization mapping).

*(Kotlin)*
```kotlin
implementation(platform("org.litote.kmongo:kmongo-dsl-bom:5.14.0"))
implementation("org.litote.kmongo:kmongo-dsl-coroutine-serialization")
```

### Reactive streams, Reactor, RxJava

The official Kotlin drivers are sync and coroutine only: use the coroutine flavor
(Reactor and RxJava provide coroutine bridges: `kotlinx-coroutines-reactor`, `kotlinx-coroutines-rx2`...).

## Let's Start Coding

### With the sync driver

```kotlin
import org.litote.kmongo.* // NEEDED! import KMongo DSL extensions

data class Jedi(val name: String, val age: Int)

val client = KMongo.createClient() // official com.mongodb.kotlin.client.MongoClient
val database = client.getDatabase("test") // official driver usage
val col = database.getCollection<Jedi>() // KMongo DSL extension method
// here the name of the collection by convention is "jedi"
// you can use getCollection<Jedi>("otherjedi") if the collection name is different

col.insertOne(Jedi("Luke Skywalker", 19))

val yoda: Jedi? = col.findOne(Jedi::name eq "Yoda")

(...)
```

### With coroutines

```kotlin
import org.litote.kmongo.* // NEEDED! import the type-safe query DSL
import org.litote.kmongo.coroutine.* // NEEDED! import KMongo DSL coroutine extensions

data class Jedi(val name: String, val age: Int)

val client = KMongo.createClient() // official com.mongodb.kotlin.client.coroutine.MongoClient
val database = client.getDatabase("test") // official driver usage
val col = database.getCollection<Jedi>() // KMongo DSL extension method

runBlocking {
    col.insertOne(Jedi("Luke Skywalker", 19))

    val yoda: Jedi? = col.findOne(Jedi::name eq "Yoda")

    (...)
}
```

If both the sync and the coroutine flavors are in the classpath, import `KMongo` explicitly
(`import org.litote.kmongo.coroutine.KMongo`).

### With kotlinx.serialization

Enable the kotlinx.serialization compiler plugin, with the version of your Kotlin plugin:

- with Gradle

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
}
```

- or Maven

```xml
<plugin>
  <groupId>org.jetbrains.kotlin</groupId>
  <artifactId>kotlin-maven-plugin</artifactId>
  <version>${kotlin.version}</version>
  <configuration>
    <compilerPlugins>
      <plugin>kotlinx-serialization</plugin>
    </compilerPlugins>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-maven-serialization</artifactId>
      <version>${kotlin.version}</version>
    </dependency>
  </dependencies>
</plugin>
```

Then annotate your classes:

```kotlin
import org.litote.kmongo.* // NEEDED! import KMongo DSL extensions

@Serializable // you need to annotate each class you want to persist
data class Jedi(val name: String, val age: Int, val firstAppearance: StarWarsFilm)

@Serializable
data class StarWarsFilm(
    val name: String,
    // annotate with @Contextual the types that have already serializers - look at kotlinx.serialization documentation
    @Contextual val date: LocalDate
)

val client = KMongo.createClient() // official com.mongodb.kotlin.client.MongoClient
val database = client.getDatabase("test") // official driver usage
val col = database.getCollection<Jedi>() // KMongo DSL extension method
// here the name of the collection by convention is "jedi"
// you can use getCollection<Jedi>("otherjedi") if the collection name is different

col.insertOne(Jedi("Luke Skywalker", 19, StarWarsFilm("A New Hope", LocalDate.of(1977, Month.MAY, 25))))

val yoda: Jedi? = col.findOne(Jedi::name eq "Yoda")

(...)
```

### Explicit imports

The samples use star imports. If you use explicit imports (ktlint...), import each extension:
the sync extensions are in the `org.litote.kmongo` package, the coroutine extensions in the `org.litote.kmongo.coroutine` package.

## KDoc

The KMongo DSL API documentation in KDoc format is available:

- [KMongo DSL core (type-safe queries, JSON)](api/kmongo-dsl-core/org.litote.kmongo/index.html)
- [KMongo DSL sync](api/kmongo-dsl-sync/org.litote.kmongo/index.html)
- [KMongo DSL coroutine](api/kmongo-dsl-coroutine/org.litote.kmongo.coroutine/index.html)
- [all the modules](api/index.html)
