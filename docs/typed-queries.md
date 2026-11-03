# Typed Queries

## Type-safe Queries

KMongo DSL provides a type-safe query framework.

[Kotlin property references](https://kotlinlang.org/docs/reflection.html#property-references) are used
to build mongo queries.

For example, here `eq` and `regexp` are infix functions provided by KMongo DSL:

```kotlin
data class Jedi(val name: String)

val yoda = col.findOne(Jedi::name eq "Yoda")

//compile error as 2 is not a String ->
val error = col.findOne(Jedi::name eq 2)

//you can use property reference with instances
val yoda2 = col.findOne(yoda::name regex "Yo.*")
```

### eq & contains

Mongo uses the eq operator both for document value and array value.
In order to be type-safe, KMongo DSL introduces the `contains` function for arrays:

```kotlin
data class Article(val title: String,val tags: List<String>)

col.aggregate<Article>(match(Article::tags contains "virus")) (...)

```

### Nested properties and `/` operator

To define nested properties, KMongo DSL introduces the `/` operator.

So for example:

```kotlin
class Friend(val coor: Coordinate?)
class Coordinate(val lat: Int, val lng : Int)

// the generated query is {"coor.lat":{$lt:0}}
col.findOne(Friend::coor / Coordinate::lat lt 0)
```

#### Unsafe nested properties and `%` operator

Sometimes you would need to bypass typesafe checking when writing nested property path.
Then use the `%` operator - it has the same behaviour as `/` but without typesafe checks:

```kotlin
sealed class Shape(val type: String) {
    data class Circle(val radius: Int) : Shape("Circle")
}

data class Box(val shape: Shape)

val bson = setValue(Box::shape % Shape.Circle::radius, 0)
```

### Debugging Typed queries

You can use the .json extension to print the json version of the query.

For example:

```kotlin
println(
    setValue(
        (Project::reports.filteredPosOp("report") / ProjectReport::points)
        .filteredPosOp("point") / ProjectReportPoint::published,
        now
    ).json
)
```

print
```
{"$set": {"reports.$[report].points.$[point].published": {"$date": "2020-12-06T20:36:21.576Z"}}}
```


### Samples

#### Queries with and & or

```kotlin
col.findOne(or(Jedi::name eq "Yoda", Jedi::age gt 25))
col.findOne(and(Jedi::name eq "Yoda", Jedi::age gt 25))

//The and is implicit
col.findOne(Jedi::name eq "Yoda", Jedi::age gt 25)
```

#### Update

```kotlin
//Both are equivalent
col.updateOne(friend::name eq "Paul", setValue(friend::name, "John"))
col.updateOne(friend::name eq "Paul", Friend::name setTo "John")

//Multi fields update
col.updateOne(friend::name eq "Paul", set( Friend::name setTo "John", Friend::age setTo 25))

//other operations are supported
col.updateOne(Friend::name eq "John", pull(Friend::tags, "t2"))
```

#### Array operators

You can use [positional array operators](api/kmongo-dsl-core/org.litote.kmongo/index.html):

```kotlin

//Both are equivalent
"accesses.0.timestamp"

Container::accesses.pos(0) / Access::timestamp

```


```kotlin

data class EvaluationAnswer(val answers:List<MyAnswer>)
data class MyAnswer(val _id:String, val alreadyUsed: Boolean)

//Both are equivalent

col.updateMany("{ \"answers._id\": { \$in: ${answerIds.json} } }", "{ \$set: { \"answers.\$[].alreadyUsed\": true } }")

col.updateMany(
            (EvaluationAnswer::answers / MyAnswer::_id) `in` answerIds,
            setValue(EvaluationAnswer::answers.allPosOp / MyAnswer::alreadyUsed, true)
        )

```

Cheat Sheet:

```
.0.             = pos(0)
$               = posOp
$[]             = allPosOp
$[<identifier>] = filteredPosOp
```

#### Map operators

```kotlin
data class Friend(val localeMap: Map<Locale, Gift>)
data class Gift(val amount: BigDecimal)

//returns true
assertEquals("localeMap.en.amount", (Friend::localeMap.keyProjection(Locale.ENGLISH) / Gift::amount).path())
```

#### Aggregation

You can chain aggregation operators:

```kotlin
data class Article(
        val title: String,
        val author: String,
        val tags: List<String>,
        val date: Instant = Instant.now(),
        val count: Int = 1,
        val ok: Boolean = true
    )

data class Result(
        @BsonId val title: String,
        val averageYear: Double = 0.0,
        val count: Int = 0,
        val friends: List<Friend> = emptyList()
    )

val r = col.aggregate<Result>(
            match(
                Article::tags contains "virus"
            ),
            group(
                Article::title, Result::friends.push(Friend::name from Article::author)
            ),
            sort(
                ascending(
                    Result::title
                )
            )
        )
```

Other example:

```kotlin
val result = col.aggregate<Result>(
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
```

Lookup sample:

```kotlin
data class Answer(val evaluator: String, val alreadyUsed: Boolean, val answerDate: Instant)

data class EvaluationsForms(val questions: List<String>)

data class EvaluationsFormsWithResults(val questions: List<String>, val results: List<EvaluationRequest>)

data class EvaluationsAnswers(val questionId: String, val evaluated: String, val answers: List<Answer>)

data class EvaluationRequest(val userId: String, val evaluationDate: String)

val bson = lookup(
            "evaluationsAnswers",
            listOf(EvaluationsForms::questions.variableDefinition()),
            EvaluationsFormsWithResults::results,
            match(
                expr(
                    and from listOf(
                        `in` from listOf(EvaluationsAnswers::questionId, EvaluationsForms::questions.variable),
                        eq from listOf(EvaluationsAnswers::evaluated, "id")
                    )
                )
            ),
            EvaluationsAnswers::answers.unwind(),
            match(
                expr(
                    and from listOf(
                        eq from listOf(EvaluationsAnswers::answers / Answer::alreadyUsed, false),
                        gte from listOf(EvaluationsAnswers::answers / Answer::answerDate, Instant.now())
                    )
                )
            ),
            group(
                fields(
                    EvaluationRequest::userId from (EvaluationsAnswers::answers / Answer::evaluator),
                    EvaluationRequest::evaluationDate from (
                            dateToString from (
                                    combine(
                                        "format" from "%Y-%m-%d",
                                        "date" from (EvaluationsAnswers::answers / Answer::answerDate)
                                    )
                                    )
                            )
                )
            ),
            replaceRoot("_id".projection)
        )
```

#### expr & equals

```kotlin
match(
    expr(
        "eq".projection from listOf(
            (HistoryEventWrapper<HistoricVariableInstance>::event / HistoricVariableInstance::processInstanceId).projection,
            HistoricProcessInstance::processInstanceId.variable
        )
    )
)
```

generates

`{"$match": {"$expr": {"$eq": ["$event.processInstanceId", "$$processInstanceId"]}}}`

## Generated property paths

Specifying the property references can be cumbersome, especially for nested properties.

KMongo DSL provides annotation processors to generate these property references.

You just have to annotate the data class with the `@Data` annotation (`kmongo-dsl-data` artifact,
a dependency of `kmongo-dsl-core`).

Then declare the annotation processor:

| Build tool | Processor |
|------------|-----------|
| Gradle     | `kmongo-dsl-ksp` ([KSP](https://kotlinlang.org/docs/ksp-overview.html), recommended: faster, incremental) |
| Maven      | `kmongo-dsl-annotation-processor` ([KAPT](https://kotlinlang.org/docs/kapt.html): the `kotlin-maven-plugin` supports it natively) |

Both generate the same code.

- for Gradle (KSP)

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.13"
}

dependencies {
    ksp("org.litote.kmongo:kmongo-dsl-ksp:5.14.0")
}
```

The `kotlin-kapt` Gradle plugin also works with `kapt("org.litote.kmongo:kmongo-dsl-annotation-processor:5.14.0")`.

- for Maven (KAPT)

```xml
<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>kapt</id>
            <goals>
                <goal>kapt</goal>
            </goals>
            <configuration>
                <sourceDirs>
                    <sourceDir>src/main/kotlin</sourceDir>
                </sourceDirs>
                <annotationProcessorPaths>
                    <annotationProcessorPath>
                        <groupId>org.litote.kmongo</groupId>
                        <artifactId>kmongo-dsl-annotation-processor</artifactId>
                        <version>${kmongo-dsl.version}</version>
                    </annotationProcessorPath>
                </annotationProcessorPaths>
            </configuration>
        </execution>

        <execution>
            <id>compile</id>
            <phase>compile</phase>
            <goals>
                <goal>compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Use the `test-kapt` goal for the `@Data` classes of the test sources.
A complete Maven project is available in the [maven-sample](https://github.com/Litote/kmongo-dsl/tree/main/maven-sample) directory.

Now you can write:

```kotlin
import Friend_.Coor

@Data
class Friend(val coor: Coordinate?)

@Data
class Coordinate(val lat: Int, val lng: Int)

val col: MongoCollection<Friend>
col.findOne(Coor.lat lt 0, Coor.lng gt 0)
```

### @DataRegistry

You can use this annotation (that annotates a dedicated *object* for example, or a file: `@file:DataRegistry([...])`)
if you can't or don't want to annotate directly with @Data the target classes.

> With KAPT, a file annotated with `@file:DataRegistry` must contain at least one top-level function or property
> (KAPT only sees the annotations of the files with top-level declarations) - else annotate a class or an object.

### Notes

- the private and protected properties, and the internal properties of the classes of other modules, are supported
  (through `kotlin-reflect`).
- the type aliases are expanded, the nullable values of the collections and maps of `@Data` classes are supported
  (`List<Address?>`, `Map<String, Address?>`).
- if your classes are internal, you can use `@Data(internal = true)` - then the classes that reference the target class
  must be internal also.
- the classes annotated with `@JacksonData` get also generated property paths if `kmongo-dsl-core` is in the classpath.

## Jackson serializers (@JacksonData)

The `kmongo-dsl-annotation-generator` KAPT processor generates Jackson serializers and deserializers
(`MyClass_Serializer`, `MyClass_Deserializer`) for the classes annotated with `@JacksonData` (`kmongo-dsl-jackson-data` artifact)
or listed in a `@JacksonDataRegistry`. They are registered automatically by the Jackson mapping
(`kmongo-dsl-jackson-module-loader` artifact), and avoid the Jackson reflection at runtime.

```xml
<annotationProcessorPath>
    <groupId>org.litote.kmongo</groupId>
    <artifactId>kmongo-dsl-annotation-generator</artifactId>
    <version>${kmongo-dsl.version}</version>
</annotationProcessorPath>
```

It can be used in the same KAPT execution as `kmongo-dsl-annotation-processor`.

- supported classes: final or open Kotlin classes without superclass and without type parameters, built with their
  primary constructor - the other classes are skipped (Jackson default serialization)
- serialized properties: the properties with a backing field, minus the `@JsonIgnore` and `@Transient` ones;
  `@JsonProperty("name")` sets the JSON name
- missing values use the default values of the constructor parameters
