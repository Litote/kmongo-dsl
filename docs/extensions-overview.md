# Extensions Overview

This overview presents the extensions for the sync flavor (`org.litote.kmongo` package).

Equivalent (suspend) functions are available for the coroutine flavor (`org.litote.kmongo.coroutine` package).

## Insert, Update & Delete

### Insert
Note that by default, KMongo DSL serializes null values during insertion of instances.

```kotlin
class TFighter(val version: String, val pilot: Pilot?)

database.getCollection<TFighter>().insertOne(TFighter("v1", null))
val col = database.getCollection<Document>("tfighter")
// the document class is required (use org.bson.Document for raw documents)

println(col.findOne()) // print: Document{{_id=(...), version=v1, pilot=null}}
```

The query shell format is supported:

```kotlin
database.getCollection<TFighter>().insertOne("{'version':'v1'}")
```

### Save

KMongo DSL provides a convenient [`MongoCollection<T>.save(target: T)`](api/kmongo-dsl-sync/org.litote.kmongo/save.html) method.
If the instance passed as argument has no id, or a null id, it is inserted (and the id generated on server side or client side, respectively).
Otherwise, a `replaceOneById` with `upsert=true` is performed (its `UpdateResult` is returned).

### Update

KMongo DSL provides various [`MongoCollection<T>.updateOne`](api/kmongo-dsl-sync/org.litote.kmongo/update-one.html) methods.

- you can perform an update using shell query format:

```kotlin
col.updateOne("{name:'Paul'}", "{\$set:{name:'John'}}")
```

- or with typed queries:

```kotlin
col.updateOne(Friend::name eq "Paul", setValue(Friend::name, "John"))
// or with the generated property paths ->
col.updateOne(Friend_.Name eq "Paul", setValue(Friend_.Name, "John"))
```

- You can also update an instance by its _id (all properties of the instance are updated):

```kotlin
// explicitly
col.updateOneById(friend._id, newFriend)
// or implicitly
// note that if newFriend does not contain an _id value, the operation fails
col.updateOne(newFriend)
```

- Null properties are taken into account during the update (they are set to null in the document in MongoDb).
If you prefer to ignore null properties during the update, you can use the `updateOnlyNotNullProperties` parameter:

```kotlin
col.updateOne(newFriend, updateOnlyNotNullProperties = true)
```

If you think it should be the default behaviour for all updates:

```kotlin
UpdateConfiguration.updateOnlyNotNullProperties = true
```

[`updateMany`](api/kmongo-dsl-sync/org.litote.kmongo/update-many.html)
is also supported.

### Replace

The [`replaceOne`](api/kmongo-dsl-sync/org.litote.kmongo/replace-one.html)
and [`replaceOneById`](api/kmongo-dsl-sync/org.litote.kmongo/replace-one-by-id.html)
methods automatically remove the _id of the replacement when using shell query format, and look like update methods:

```kotlin
// ObjectId of friend replacement does not matter
col.replaceOne("{name:'Peter'}", Friend(ObjectId(), "John"))
// explicit id filter ->
col.replaceOneById(friend._id, Friend(ObjectId(), "John"))
// implicit id filter ->
col.replaceOne(Friend(friend._id, "John"))
```

But for typed queries you need to use [`replaceOneWithFilter`](api/kmongo-dsl-sync/org.litote.kmongo/replace-one-with-filter.html)
as the default driver method does not remove the _id:

```kotlin
// ObjectId of friend replacement is removed from the replace query
// before it is sent to mongo
col.replaceOneWithFilter(Friend::name eq "Peter", Friend(ObjectId(), "John"))
// if the driver method is used, it will fail at runtime
// as the _id is updated ->
col.replaceOne(Friend::name eq "Peter", Friend(ObjectId(), "John"))
```

### Delete

[`deleteOne`](api/kmongo-dsl-sync/org.litote.kmongo/delete-one.html)
and [`deleteOneById`](api/kmongo-dsl-sync/org.litote.kmongo/delete-one-by-id.html)
work as expected:

```kotlin
col.deleteOne("{name:'John'}")
col.deleteOne(Friend::name eq "John")
```

As does [`deleteMany`](api/kmongo-dsl-sync/org.litote.kmongo/delete-many.html).

### FindOneAnd...

The driver variants are also supported:

- [`findOneAndUpdate`](api/kmongo-dsl-sync/org.litote.kmongo/find-one-and-update.html)
- [`findOneAndReplace`](api/kmongo-dsl-sync/org.litote.kmongo/find-one-and-replace.html)
- [`findOneAndDelete`](api/kmongo-dsl-sync/org.litote.kmongo/find-one-and-delete.html)

### Bulk Write

If you want to do many operations with only one query (for performance reasons),
[`bulkWrite`](api/kmongo-dsl-sync/org.litote.kmongo/bulk-write.html)
is supported.

With query shell format:

```kotlin
// $set is a Kotlin template in a raw string, see the Mongo shell queries chapter
import org.litote.kmongo.MongoOperator.set

val friend = Friend("John", "22 Wall Street Avenue")
val result = col.bulkWrite(
   """ [
      { insertOne : { "document" : ${friend.json} } },
      { updateOne : {
                      "filter" : {name:"Fred"},
                      "update" : {$set:{address:"221B Baker Street"}},
                      "upsert" : true
                    }
      },
      { updateMany : {
                          "filter" : {},
                          "update" : {$set:{address:"nowhere"}}
                      }
      },
      { replaceOne : {
                          "filter" : {name:"Max"},
                          "replacement" : {name:"Joe"},
                          "upsert" : true
                      }
      },
      { deleteOne :  { "filter" : {name:"Joe"} }},
      { deleteMany :  { "filter" : {} } }
      ] """
 )

 assertEquals(1, result.insertedCount)
 assertEquals(2, result.matchedCount)
 assertEquals(3, result.deletedCount)
 assertEquals(2, result.modifiedCount)
 assertEquals(2, result.upserts.size)
 assertEquals(0, col.countDocuments())
```

or typed queries:

```kotlin
with(friend) {
    col.bulkWrite(
        insertOne(friend),
        updateOne(
            ::name eq "Fred",
            set(::address setTo "221B Baker Street"),
            upsert()
        ),
        updateMany(
            EMPTY_BSON,
            set(::address setTo "nowhere")
        ),
        replaceOne(
            ::name eq "Max",
            Friend("Joe"),
            replaceUpsert()
        ),
        deleteOne(::name eq "Max"),
        deleteMany(EMPTY_BSON)
    )
}
```

## Retrieve Data

### find

[`findOne`](api/kmongo-dsl-sync/org.litote.kmongo/find-one.html),
[`findOneById`](api/kmongo-dsl-sync/org.litote.kmongo/find-one-by-id.html)
and [`find`](api/kmongo-dsl-sync/org.litote.kmongo/find.html)
work as expected.

With the shell query format:

```kotlin
col.findOne("{name:'John'}")
```

or the type-safe query format:

```kotlin
col.findOne(Friend::name eq "John")
// implicit $and is used if more than one criterion is set ->
col.findOne(Friend::name eq "John", Friend::address eq "22 Wall Street Avenue")
```

The driver `FindIterable` has also KMongo DSL extensions (`descendingSort`, `projection`...).
The most used is usually `toList`:

```kotlin
val list: List<Friend> = col.find(Friend::name eq "John").toList()
```

> With the official sync driver, `col.find().first()` **throws** an exception if there is no result:
> use `firstOrNull()`.

### countDocuments

[`countDocuments`](api/kmongo-dsl-sync/org.litote.kmongo/count-documents.html)
is supported:

```kotlin
col.countDocuments("{name:{\$exists:true}}")
```

### distinct

As is [`distinct`](api/kmongo-dsl-sync/org.litote.kmongo/distinct.html):

With shell query format:

```kotlin
col.distinct<String>("address")
```

Or type-safe queries:

```kotlin
col.distinct(Friend::address)
```

### projection

You can use [`MongoCollection.projection`](api/kmongo-dsl-sync/org.litote.kmongo/projection.html)
extension functions in order to retrieve only one, two or three fields:

```kotlin
col.bulkWrite(
    insertOne(Friend("Joe")),
    insertOne(Friend("Bob"))
)
val result: List<String?> = col.projection(Friend::name).toList()
```

If you need to retrieve more than three fields - you have two options:

- Use a custom dedicated class with the `FindIterable.projection` functions:

```kotlin
data class FriendWithNameOnly(val name: String)

col.withDocumentClass<FriendWithNameOnly>()
    .find()
    .projection(FriendWithNameOnly::name)
    .toList()
```

- Use projection with org.bson.Document and use the `findValue` extension function:

```kotlin
col.insertOne(Friend("Joe", Coordinate(1, 2)))
val (name, lat, lng) =
   col.withDocumentClass<Document>()
       .find()
       .descendingSort(Friend::name)
       .projection(
           fields(
               include(
                   Friend::name,
                   Friend::coordinate / Coordinate::lat,
                   Friend::coordinate / Coordinate::lng
               ),
               excludeId()
           )
       )
       .map {
           Triple(
               // classic Document.getString method
               it.getString("name"),
               // get two levels property value
               it.findValue<Int>("coordinate.lat"),
               // use typed values
               it.findValue(Friend::coordinate / Coordinate::lng)
           )
       }
       .first()
```

## Aggregate

The [aggregation framework](https://www.mongodb.com/docs/manual/aggregation/) is supported.

It works with shell query format, with [several limitations](mongo-shell-support.md):

```kotlin
col.aggregate<Article>("[{\$match:{tags:'virus'}},{\$limit:1}]").toList()
```

But this is an area where type-safe style shines:

```kotlin
data class Result(val title: String, val count: Int, val averageYear: Double)

val r = col.aggregate<Result>(
    match(
        Article::tags contains "virus"
    ),
    project(
        Article::title from Article::title,
        Article::ok from cond(Article::ok, 1, 0),
        Result::averageYear from year(Article::date)
    ),
    group(
        Article::title,
        Result::count sum Article::ok,
        Result::averageYear avg Result::averageYear
    ),
    sort(
        ascending(
            Result::title
        )
    )
)
    .toList()
```

Map-reduce is deprecated by MongoDB, and not provided by KMongo DSL: use an aggregation pipeline.

## Indexes

KMongo DSL provides several methods to manage indexes.

### ensureIndex

[`ensureIndex`](api/kmongo-dsl-sync/org.litote.kmongo/ensure-index.html)
and [`ensureUniqueIndex`](api/kmongo-dsl-sync/org.litote.kmongo/ensure-unique-index.html)
ensure that index is created even if it's structure has changed.

You can create an index with shell query format:

```kotlin
col.ensureIndex("{'id':1,'type':1}", IndexOptions().unique(true))
```

or with type-safe style:

```kotlin
col.ensureUniqueIndex(Friend::id, Friend::type)
```

### dropIndex

With [`dropIndexOfKeys`](api/kmongo-dsl-sync/org.litote.kmongo/drop-index-of-keys.html),
you can drop index, based on their keys:

```kotlin
col.dropIndexOfKeys("{'id':1,'type':1}")
```

Do not use `dropIndex` with these keys: `col.dropIndex("{'id':1,'type':1}")` calls the driver function, which takes the
name of the index, and fails with an `IndexNotFound` error.

## MongoIterable

The `MongoIterable` of the official Kotlin sync driver is not a Kotlin `Iterable`: its `cursor()` must be closed
(or use the driver `forEach`, `map`, `firstOrNull`, `toList`... that close it).

KMongo DSL adds the collection-like extensions of `Iterable` to `MongoIterable` (`filter`, `associateBy`, `contains`,
`last`, `single`, `indexOf`...): they close the cursor too.

```kotlin
col.find().filter { it.name != "Joe" }
col.find().associateBy { it.name }
friend in col.find()
```

> These extensions are in the `org.litote.kmongo` package (they were in `kotlin.collections` with the deprecated KMongo).
> `col.find().filter { }` needs the `org.litote.kmongo.filter` import (the driver member `filter(Bson)` is not applicable to a lambda).

### Use Sequences

The `MongoIterable.asSequence()` extension loads in memory all the documents returned by the query.
This is because it's required to close the Mongo cursor in `asSequence()` method, in order to avoid a memory leak!

KMongo DSL provides an [evaluate](api/kmongo-dsl-sync/org.litote.kmongo/evaluate.html) method that loads only the needed rows in memory,
and also ensures that the cursor is closed:

```kotlin
val notJoe = col.find().evaluate {
  // this is a sequence evaluation
  // If the first row has a name like "Fred", only one row is loaded in memory!
  filter { it.name != "Joe" }.first()
}

// If col.find() returns 1M of documents, they are all loaded in memory!
col.find().asSequence().filter { it.name != "Joe" }.first()
```

## withKMongo

The recommended method, in order to setup KMongo DSL, is to use `KMongo.createClient()`
to get a `MongoClient` instance (with the KMongo DSL object mapping).

However you can also create the `MongoClient` with the official driver and then call the
`MongoDatabase#withKMongo` or `MongoCollection#withKMongo` extensions to enable the KMongo DSL object mapping.

You can also configure the `MongoClientSettings` of the driver with `KMongoClientSettings.settings(settings)`.

## Coroutine

The coroutine flavor extends the classes of the official coroutine driver (`com.mongodb.kotlin.client.coroutine`):
the find and aggregate queries return [flows](https://kotlinlang.org/docs/flow.html) (`FindFlow`, `AggregateFlow`...).

```kotlin
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*

val col = KMongo.createClient().getDatabase("test").getCollection<Friend>()

suspend fun friends(): List<Friend> = col.find(Friend::name eq "John").toList()
suspend fun john(): Friend? = col.find(Friend::name eq "John").firstOrNull()
```

> Use `firstOrNull()` (`kotlinx.coroutines.flow`) when there may be no result: `first()` throws.

## Transactions

Transactions are supported. Remember you have to use the extension methods with `ClientSession` parameter.

With the sync flavor:

```kotlin
mongoClient.startSession().use { clientSession ->
    clientSession.startTransaction()
    col.insertOne(clientSession, Friend("Bob"))
    // optional
    clientSession.commitTransaction()
}
```

With coroutines:

```kotlin
runBlocking {
    mongoClient.startSession().use { clientSession ->
        clientSession.startTransaction()
        col.insertOne(clientSession, Friend("Bob"))
        clientSession.commitTransaction()
    }
}
```

For additional transactions topics, please look at the [dedicated mongo documentation](https://www.mongodb.com/docs/manual/core/transactions/).

## Change streams

The official drivers resume a change stream once after a resumable error. For long-running listeners
(configuration reloading, cache invalidation...), KMongo DSL provides change streams that survive the errors
and the invalidations:

- after any `MongoException` (server down, election, network...), the change stream is reopened after a delay
  (doubled after each consecutive error, up to a maximum), **after the last received change** (the resume token is kept)
- if the resume token is not usable any more (history lost), it restarts from the current time
- if the change stream is invalidated (collection dropped or renamed), it is reopened
- a change that can not be decoded is skipped: the exception is sent to `onError`, the next changes are received

```kotlin
// coroutine: a Flow - cancel the coroutine to stop watching
scope.launch {
    col.watchIndefinitely(fullDocument = FullDocument.UPDATE_LOOKUP, onError = { logger.warn(it) { "change stream error" } })
        .collect { change -> reload(change.fullDocument) }
}

// sync: a daemon thread - close() stops the listening
val listening = col.listenIndefinitely(fullDocument = FullDocument.UPDATE_LOOKUP) { change -> reload(change.fullDocument) }
listening.close()
```

Both accept a `pipeline` (to filter the changes), `retryDelay` / `maxRetryDelay`, and a `configure` lambda
for the other change stream options (`fullDocumentBeforeChange`, `batchSize`...).
The sync listener exceptions are sent to `onError` (the listening continues); the coroutine collector exceptions
cancel the flow (they are not retried).

## GridFS

The official Kotlin drivers do not provide GridFS (and the Java client of a Kotlin client is not accessible). Create a Java driver client with the KMongo DSL mapping,
and the Kotlin client on top of it (they share the same connection pool):

```kotlin
val javaClient: com.mongodb.client.MongoClient = KMongo.createJavaClient(settings) // or (connectionString)
val client: com.mongodb.kotlin.client.MongoClient = KMongo.createClient(javaClient)

val bucket = GridFSBuckets.create(javaClient.getDatabase("db"))
```

If you only use the coroutine flavor, add the `org.mongodb:mongodb-driver-sync` dependency to use `KMongo.createJavaClient`
from `kmongo-dsl-sync`, or create the Java client with `MongoClients.create(KMongoClientSettings.settings(settings))`.

## KDoc

Please consult the [KDoc](api/index.html)
for an exhaustive list of the KMongo DSL extensions.
