# Object Mapping

Query results are automatically mapped to objects.

Look at the [Quick Start related paragraph](quick-start.md#choose-the-flavor-and-the-object-mapping)
in order to know how to select the object mapping engine.

## How to choose the mapping engine

### The Jackson choice

With the [Jackson 3 library](https://github.com/FasterXML/jackson), you get full support of one of the most powerful data-binding java engine, in order to map your data objects.

If you need custom serialization or deserialization, you will not be blocked, you can implement it.

And you can use all [Jackson annotations](https://github.com/FasterXML/jackson-annotations/wiki/Jackson-Annotations).

Also, if you already use Jackson for json serialization (for rest services for example), the library is already in your list of dependencies.

However, if you don't already use Jackson, you add a new quite complex library to your dependencies.

#### Register a custom Jackson module

Use [registerBsonModule](api/kmongo-dsl-jackson-mapping/org.litote.kmongo.util/-k-mongo-configuration/register-bson-module.html) function.

#### Generated Jackson serializers

The `kmongo-dsl-annotation-generator` KAPT processor generates Jackson serializers and deserializers
for the classes annotated with `@JacksonData` (no reflection at runtime): they are registered automatically.
See [the generated code](typed-queries.md#jackson-serializers-jacksondata).

### The kotlinx.serialization choice

KMongo DSL also supports [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) mapping,
on top of the official [bson-kotlinx](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/fundamentals/data-formats/serialization/)
encoder and decoder (with the KMongo BSON format: `___type` class discriminator, dates, ids, `BigDecimal`...).

The main advantage of this kind of mapping is that **almost no (slow) reflection** is involved.

#### Additional Modules and Serializers

Use `registerModule` or `registerSerializer` functions in order to register new
(for example polymorphic) kotlinx Module or Serializer.

#### Avoid reflection completely

By default, there is still a little bit of reflection involved when persisting a document, in order to retrieve dynamically
the id of the document instance. You can declare your own `IdController`,
using the `changeIdController` function to avoid reflection completely.

### The "native" POJO mapping

The deprecated KMongo "native" mapping (POJO codec) is not provided by KMongo DSL: use the kotlinx.serialization mapping,
the Jackson mapping, or the official driver data class codec directly (without KMongo DSL).

### Conclusion

- For complex projects, or if you have already Jackson skills, the Jackson object mapping is a really nice choice.

- Kotlin experts will prefer kotlinx.serialization, as it usually provides the best performance.

See also [Performance section](performance.md).

## Configure `KotlinModule`

The Jackson `ObjectMapper` used by KMongo DSL uses a [jackson's KotlinModule](https://github.com/FasterXML/jackson-module-kotlin).
The module is created with default `KotlinFeature`s.
To change configuration of the module, use `KotlinModuleConfiguration` object:

```kotlin
KotlinModuleConfiguration.kotlinModuleInitializer = {
    // Configure `KotlinModule` using its `Builder` (which is referenced as `this`):
    enable(KotlinFeature.SingletonSupport)
    enable(KotlinFeature.StrictNullChecks)
    ...
}
KMongoConfiguration.resetConfiguration()
```

## Set your _id

To manage Mongo `_id`, a class must have one `_id` property
OR a property annotated with the `@BsonId` annotation.

> For **kotlinx.serialization**, `@BsonId` is not supported - you have to use `@SerialName("_id")` (or `@MongoId`)
> as `@BsonId` replacement.
>
> For example, `Data(@Contextual val _id: Id<Data>)` and `Data(@Contextual @SerialName("_id") val myId: Id<Data>)` are equivalent.

If there is no such field in your class, an `ObjectId` _id is generated on server side.

### Id type

KMongo DSL provides an optional dedicated [Id](api/kmongo-dsl-id/org.litote.kmongo/-id/index.html) type.
With this Id class, the document identifier is typed.

So for example:

```kotlin
class LightSaber(val _id: Id<LightSaber> = newId())
class Jedi(
    @BsonId val key: Id<Jedi> = newId(),
    // set of typed ids, now I see what it is!
    val sabers: Set<Id<LightSaber>> = emptySet()
)
```

It is easy to transform an ObjectId in Id<> with the *toId()* extension:

```kotlin
LightSaber(ObjectId("myId").toId())
```

> For **kotlinx.serialization** `@Contextual` is mandatory on the `Id` property.
> If you want to reference a list of ids, you need to annotate the list argument:
>
> `val refs: List<@Contextual Id<Data>>`

#### The Id type does not depend on Mongo nor KMongo DSL

This is useful if you need to transfer your data object from a mongo backend
to a frontend that does not need to know your storage engine.

You just have to add the `kmongo-dsl-id` dependency in the frontend to compile.

- with Maven

```xml
<dependency>
  <groupId>org.litote.kmongo</groupId>
  <artifactId>kmongo-dsl-id</artifactId>
  <version>5.14.0</version>
</dependency>
```

- or Gradle

```kotlin
implementation("org.litote.kmongo:kmongo-dsl-id:5.14.0")
```

> `kmongo-dsl-id`, `kmongo-dsl-data`, `kmongo-dsl-id-jackson` and `kmongo-dsl-id-serialization` replace the KMongo
> `kmongo-id`, `kmongo-data`, `kmongo-id-jackson` and `kmongo-id-serialization` artifacts (same classes, same packages):
> do not put both in the same classpath.

#### Id <> Json Jackson serialization

If you use Jackson to serialize your objects to json (in order to transfer your data between frontend and backend),
add the `kmongo-dsl-id-jackson` dependency and register the `IdJacksonModule` module:

```kotlin
val mapper = JsonMapper.builder().addModule(IdJacksonModule()).build()
```

#### Id <> Json Gson serialization

If you prefer to use Gson, you need to use this code:

```kotlin
val gsonBuilder = GsonBuilder()
gsonBuilder.registerTypeAdapter(Id::class.java,
        JsonSerializer<Id<Any>> { id, _, _ -> JsonPrimitive(id?.toString()) })
gsonBuilder.registerTypeAdapter(Id::class.java,
        JsonDeserializer<Id<Any>> { id, _, _ -> id.asString.toId() })
val gson = gsonBuilder.create()
```

#### Id <> Json kotlinx.serialization

If you use kotlinx.serialization to serialize your objects to json (in order to transfer your data between frontend and backend),
add the `kmongo-dsl-id-serialization` dependency and register the `IdKotlinXSerializationModule` module:

```kotlin
@Serializable
data class Data(@Contextual val _id: Id<Data> = newId())

val json = Json { serializersModule = IdKotlinXSerializationModule }
val data = Data()
val encoded = json.encodeToString(data)
```

### Gets an Id from an ObjectId

Just use the .toId() extension:

```kotlin
val id: Id<A> = ObjectId().toId()
```

### Other _id types

The Id type is optional,
you can use `String`, `org.bson.types.ObjectId` or whatever you need as _id type.

#### UUID

In order to use UUID as identifier, you have to specify (as documented in the MongoDB driver):

```kotlin
KMongo.createClient(
    MongoClientSettings
        .builder()
        .uuidRepresentation(UuidRepresentation.STANDARD)
        .applyConnectionString(ConnectionString(connection))
        .build()
)
```

For *jackson*, you also need to use the
[KMongoJacksonFeature.setUUIDRepresentation](api/kmongo-dsl-jackson-mapping/org.litote.kmongo.util/-k-mongo-jackson-feature/index.html)
function.

> The deprecated KMongo `UUID` format depends on the mapping (Jackson: binary subtype 3 - legacy,
> kotlinx.serialization: binary subtype 4 - standard): KMongo DSL keeps both formats.

## Date support

KMongo DSL provides built-in support for these "Date" classes:

- java.util.Date
- java.util.Calendar
- java.time.LocalDateTime
- java.time.LocalDate
- java.time.LocalTime
- java.time.ZonedDateTime
- java.time.OffsetDateTime
- java.time.OffsetTime
- java.time.Instant
- java.time.ZoneId
- the kotlinx-datetime classes (`kotlinx.datetime.Instant`, `LocalDate`, `LocalDateTime`...), if kotlinx-datetime is in the classpath

Dates are always stored in Mongo in UTC. For `Calendar`, `ZonedDateTime`, `OffsetDateTime` and `OffsetTime`,
whatever is the timezone of the saved date, you will get a UTC date when loading the date from Mongo.

So, in this case, the loaded date will not usually be equal to the saved date - the Instant part of the date of course is the same.
If you need to check equality, use a method like `OffsetDateTime#withOffsetSameInstant` on the loaded date.

## Register a custom mongo Codec

Use [ObjectMappingConfiguration.addCustomCodec](api/kmongo-dsl-core/org.litote.kmongo.util/-object-mapping-configuration/index.html)
function in order to register a custom codec.
You have also the option to use [custom Jackson modules](#register-a-custom-jackson-module)
or custom [kotlinx.serialization modules](#additional-modules-and-serializers).

## Explicitly defining the ClassMappingTypeService

If you get an error stating "Service ClassMappingTypeService not found", you are probably using KMongo DSL in an
environment where your application is loaded at runtime (e.g. Minecraft plugins).

To fix this error, you can set the `org.litote.mongo.mapping.service` property to the qualified class name of the
service for the object mapping engine you are using. For kotlinx.serialization this would be the following:

```kotlin
System.setProperty("org.litote.mongo.mapping.service", SerializationClassMappingTypeService::class.qualifiedName!!)
```

For the Jackson mapping, use `org.litote.kmongo.jackson.JacksonClassMappingTypeService`.
