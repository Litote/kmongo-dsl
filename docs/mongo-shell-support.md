# Mongo Shell Queries

In Kotlin the $ sign is used to define expressions in string templates.
Unfortunately $ is also used by MongoDB to define operators.
Use [multi-dollar string interpolation](#multi-dollar-string-interpolation) to write mongo shell queries in Kotlin.

## Multi-dollar string interpolation

Since Kotlin 2.2, a string prefixed with `$$` only interpolates `$$name` and `$${expression}`:
a single `$` is a plain character, in single-line strings and in raw strings
([KT-2425](https://youtrack.jetbrains.com/issue/KT-2425)).
This is the recommended way to write mongo shell queries: copy/paste them as is.

```kotlin
val yoda = col.findOne($$"{name: {$regex: 'Yo.*'}}")!!

val luke = col.aggregate<Jedi>($$"""
[
  { $match: { age: { $lt: $${yoda.age} } } },
  { $sample: { size: 1 } }
]
""").firstOrNull()

// no workaround needed for the $in operator or the $ field paths
col.find($$"{name: {$in: ['Luke Skywalker', 'Yoda']}}")

col.aggregate<Document>($$"""
[
  { $match: { status: "A" } },
  { $group: { _id: "$cust_id", total: { $sum: "$amount" } } },
  { $sort: { total: -1 } }
]
""")
```

## The MongoOperator enum class
Without the `$$` prefix, add an import org.litote.kmongo.MongoOperator.* and then use these operators in the string template. To reuse a previous example :

```kotlin
import org.litote.kmongo.MongoOperator.lt
import org.litote.kmongo.MongoOperator.match
import org.litote.kmongo.MongoOperator.regex
import org.litote.kmongo.MongoOperator.sample

val yoda = col.findOne("{name: {$regex: 'Yo.*'}}")!! //mongo shell query format is supported
//raw strings and string templates too
val luke = col.aggregate<Jedi>("""[ {$match:{age:{$lt : ${yoda.age}}}},
                                    {$sample:{size:1}}
                                 ]""").firstOrNull()
```

## The json extension property

Now you want to use also real template expressions.
Except for very simple type (like Int), the values of these expressions have to be json encoded.
So KMongo DSL provides a json extension property for all classes. For example :

```kotlin
data class Jedi(val name: String, val age: Int)

val luke = Jedi("Luke Skywalker", 19)

//Real life example
col.findOne("{jediName:${luke.name.json}}")
//with multi-dollar interpolation
col.findOne($$"{jediName: $${luke.name.json}}")

//The json property works with all objects, using Mongo Extended Json format
println("${ObjectId().json}") //print {"$oid":"575b17284ce9b17526c26e46"}
println("${luke.json}") //print {"name":"Luke Skywalker","age":19}
```
