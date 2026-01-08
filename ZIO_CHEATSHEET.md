# ZIO Ecosystem Cheatsheet for Senior Scala Developers

## Main Questions

### 1. What is ZIO and how does it compare to Future?

**ZIO** is a purely functional effect system for Scala. Unlike `Future`, which executes eagerly and lacks proper error handling, ZIO is **lazy**, **typed**, and provides **structured concurrency**.

```scala
import zio._

// Future - executes IMMEDIATELY when created
val future: Future[Int] = Future {
  println("Running!")  // Prints immediately
  42
}

// ZIO - just a DESCRIPTION, doesn't execute until interpreted
val zio: Task[Int] = ZIO.attempt {
  println("Running!")  // Won't print until .run or unsafeRun
  42
}

// ZIO has 3 type parameters: ZIO[R, E, A]
// R = Environment (dependencies)
// E = Error type (typed errors!)
// A = Success value
```

**Key differences:**
- **Future** = eager, untyped errors (only Throwable), context-dependent execution
- **ZIO** = lazy, typed errors, explicit dependencies, structured concurrency

---

### 2. How does ZIO Effect work? What does "lazy by default" mean?

A ZIO effect is a **pure description** of a computation, not the computation itself. It's like a recipe, not a cooked meal.

```scala
import zio._

// Creating an effect - NO execution happens here
val effect: Task[Unit] = ZIO.attempt {
  println("Hello from ZIO!")
  // This won't print until we run it
}

// Composing effects - still just building a bigger recipe
val composed: Task[Int] = 
  ZIO.attempt(println("Step 1")) *>
  ZIO.attempt(println("Step 2")) *>
  ZIO.succeed(42)

// ONLY when we run the effect does it execute
object MyApp extends ZIOAppDefault {
  def run = composed  // ZIOAppDefault will execute this
}

// Or in tests/scripts
Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(composed)
}
```

**Why lazy is powerful:**
- **Composable**: Build complex workflows without executing
- **Referentially transparent**: Can store, pass, and reuse effects
- **Testable**: Can replace real effects with test doubles
- **Retryable**: Easy to retry since it's just a description

---

### 3. How does ZIO handle errors? (Typed Error Channel)

ZIO has **two error channels**: expected errors (`E`) and unexpected defects (like runtime exceptions).

```scala
import zio._

// Task[A] = ZIO[Any, Throwable, A] - can fail with any Throwable
def riskyOperation: Task[Int] = 
  ZIO.attempt(10 / 0)  // Can throw

// IO[E, A] = ZIO[Any, E, A] - typed error
sealed trait UserError
case class NotFound(email: String) extends UserError
case class AlreadyExists(email: String) extends UserError

def getUser(email: String): IO[UserError, User] = 
  ZIO.fail(NotFound(email))

// Error handling
val handled: IO[UserError, Option[User]] = 
  getUser("john@example.com")
    .map(Some(_))                    // Success path
    .catchAll {                      // Handle ALL errors
      case NotFound(_) => ZIO.succeed(None)
      case AlreadyExists(email) => 
        ZIO.fail(AlreadyExists(email)) // Can re-fail
    }

// Or use .catchSome for partial handling
val partial: IO[UserError, User] = 
  getUser("john@example.com")
    .catchSome {
      case NotFound(email) => 
        createDefaultUser(email)  // Handle only NotFound
    }

// Convert errors to defects
val orDie: Task[User] = 
  getUser("john@example.com")
    .orDie  // NotFound becomes a defect (like throwing)

// Fold over both channels
val folded: UIO[String] = 
  getUser("john@example.com")
    .fold(
      error => s"Error: $error",
      user => s"Success: ${user.name}"
    )
```

**Key types:**
- `Task[A]` = `ZIO[Any, Throwable, A]` - can fail with any error
- `IO[E, A]` = `ZIO[Any, E, A]` - typed error channel
- `UIO[A]` = `ZIO[Any, Nothing, A]` - cannot fail (infallible)

---

### 4. How do Fibers work in ZIO? How are they mapped to JVM threads?

**Fibers** are ZIO's unit of concurrency - lightweight, green threads managed by ZIO runtime. Many fibers run on a small pool of JVM threads.

```scala
import zio._

// Create a fiber with .fork
val fiber: UIO[Fiber[Throwable, Int]] = 
  ZIO.attempt {
    Thread.sleep(1000)
    42
  }.fork  // Returns immediately with Fiber handle

// Full example - running computation in parallel
val program: Task[Int] = for {
  fiber <- ZIO.attempt {
             println(s"Running on: ${Thread.currentThread().getName}")
             Thread.sleep(2000)
             100
           }.fork  // Spawns a new fiber
  
  _ <- ZIO.attempt(println("Main fiber continues..."))
  
  result <- fiber.join  // Wait for fiber to complete
} yield result

// Multiple fibers in parallel
def fetchUser(id: Int): Task[String] = 
  ZIO.attempt(s"user-$id").delay(1.second)

val parallelFetch: Task[List[String]] = 
  ZIO.foreachPar(List(1, 2, 3, 4, 5))(fetchUser)
  // All 5 fetches run in parallel on separate fibers
```

**How fibers map to JVM threads:**
```scala
// ZIO Runtime has multiple thread pools:

1. Compute Pool (default: # of CPU cores)
   - For CPU-bound work
   - Fibers are multiplexed onto these threads
   - Many fibers can run on one thread (cooperative multitasking)

2. Blocking Pool (unbounded, cached)
   - For blocking I/O operations
   - Use ZIO.blocking or ZIO.attemptBlocking

// Example:
val cpuBound: Task[Int] = 
  ZIO.attempt {
    // Runs on compute pool
    (1 to 1000000).sum
  }

val blocking: Task[String] = 
  ZIO.attemptBlocking {
    // Runs on blocking pool - won't starve compute threads
    scala.io.Source.fromFile("file.txt").mkString
  }
```

**Fiber properties:**
- **Lightweight**: Millions can exist (vs thousands of threads)
- **Cooperative**: Yield at async boundaries (`.flatMap`, `.map`, etc.)
- **Structured**: Automatic cleanup with `.ensuring` and scopes
- **Interruptible**: Can be cancelled cleanly

---

### 5. ZIO Parallelism: `zipPar`, `foreachPar`, `collectAllPar` - how do they work?

ZIO provides many operators for parallel execution. Unlike sequential composition, these run effects concurrently on separate fibers.

```scala
import zio._

def fetchUser(id: Int): Task[User] = 
  ZIO.attempt(User(s"user-$id")).delay(1.second)

def fetchOrders(userId: String): Task[List[Order]] = 
  ZIO.succeed(List.empty).delay(1.second)

// 1. zipPar - run 2 effects in parallel
val parallel2: Task[(User, List[Order])] = 
  fetchUser(1).zipPar(fetchOrders("user-1"))
  // Both run simultaneously, returns when BOTH complete

// 2. foreachPar - parallel map over collection
val parallelMany: Task[List[User]] = 
  ZIO.foreachPar(List(1, 2, 3, 4, 5))(fetchUser)
  // All 5 fetchUser calls run in parallel

// 3. collectAllPar - run list of effects in parallel
val effects: List[Task[User]] = 
  List(1, 2, 3).map(fetchUser)

val parallelAll: Task[List[User]] = 
  ZIO.collectAllPar(effects)

// 4. raceAll - first to complete wins
val racing: Task[String] = 
  ZIO.raceAll(
    ZIO.succeed("fast").delay(100.millis),
    List(
      ZIO.succeed("slow").delay(1.second),
      ZIO.succeed("slower").delay(2.seconds)
    )
  )  // Returns "fast"

// 5. Parallel with parN (limit concurrency)
val limited: Task[List[User]] = 
  ZIO.foreachPar(1 to 100)(fetchUser)
    .withParallelism(10)  // Max 10 concurrent fibers
```

**Sequential vs Parallel:**
```scala
// Sequential (one after another)
val seq: Task[List[String]] = 
  ZIO.foreach(List(1, 2, 3)) { id =>
    ZIO.attempt(s"user-$id").delay(1.second)
  }
  // Takes 3 seconds total

// Parallel (all at once)
val par: Task[List[String]] = 
  ZIO.foreachPar(List(1, 2, 3)) { id =>
    ZIO.attempt(s"user-$id").delay(1.second)
  }
  // Takes 1 second total (all run together)
```

---

### 6. What is ZIO Environment (R parameter)? How does Dependency Injection work?

The `R` parameter in `ZIO[R, E, A]` represents **dependencies** your effect needs. This is ZIO's approach to **dependency injection**.

```scala
import zio._

// Define a service
trait UserRepository {
  def getUser(id: String): Task[User]
  def createUser(user: User): Task[Unit]
}

// Effect that requires UserRepository
val program: ZIO[UserRepository, Throwable, User] = 
  for {
    repo <- ZIO.service[UserRepository]  // Access the dependency
    user <- repo.getUser("123")
  } yield user

// Or use ZIO.serviceWithZIO for cleaner syntax
val program2: ZIO[UserRepository, Throwable, User] = 
  ZIO.serviceWithZIO[UserRepository](_.getUser("123"))

// Provide the dependency with ZLayer
val userRepoLayer: ULayer[UserRepository] = 
  ZLayer.succeed(new UserRepository {
    def getUser(id: String): Task[User] = 
      ZIO.succeed(User(s"user-$id"))
    def createUser(user: User): Task[Unit] = 
      ZIO.succeed(())
  })

// Run with dependency
val runnable: Task[User] = 
  program.provide(userRepoLayer)
  // Now R = Any, can be executed

// Multiple dependencies
trait Database
trait EmailService
trait UserService

val app: ZIO[Database & EmailService & UserService, Throwable, Unit] = 
  for {
    db <- ZIO.service[Database]
    email <- ZIO.service[EmailService]
    users <- ZIO.service[UserService]
    // ... use services
  } yield ()

// Compose layers
val dbLayer: ULayer[Database] = ???
val emailLayer: ULayer[EmailService] = ???
val userLayer: URLayer[Database & EmailService, UserService] = ???

val fullStack: ULayer[Database & EmailService & UserService] = 
  dbLayer ++ emailLayer >+> userLayer

val executable: Task[Unit] = 
  app.provide(fullStack)
```

**ZLayer patterns:**
```scala
// 1. Simple layer (no dependencies)
val simple: ULayer[Service] = 
  ZLayer.succeed(new ServiceImpl)

// 2. Layer from ZIO effect
val fromZIO: TaskLayer[Service] = 
  ZLayer.fromZIO(
    ZIO.attempt(new ServiceImpl)
  )

// 3. Layer with dependencies
val withDeps: URLayer[Database, UserRepo] = 
  ZLayer.fromFunction((db: Database) => new UserRepoImpl(db))

// 4. Layer with resource management (acquire/release)
val managed: TaskLayer[Connection] = 
  ZLayer.scoped(
    ZIO.acquireRelease(
      acquire = ZIO.attempt(openConnection())
    )(
      release = conn => ZIO.succeed(conn.close())
    )
  )
```

---

### 7. How to convert `List[ZIO]` to `ZIO[List]`? (Like Cats' sequence)

ZIO provides `collectAll`, `foreach`, and parallel variants.

```scala
import zio._

def fetchUser(id: Int): Task[String] = 
  ZIO.attempt(s"user-$id")

val ids: List[Int] = List(1, 2, 3, 4, 5)

// 1. If you already have List[ZIO]
val effects: List[Task[String]] = 
  ids.map(fetchUser)

val sequential: Task[List[String]] = 
  ZIO.collectAll(effects)  // Sequential execution

val parallel: Task[List[String]] = 
  ZIO.collectAllPar(effects)  // Parallel execution

// 2. If you have List[A] and function A => ZIO
val fromList: Task[List[String]] = 
  ZIO.foreach(ids)(fetchUser)  // Sequential

val fromListPar: Task[List[String]] = 
  ZIO.foreachPar(ids)(fetchUser)  // Parallel

// 3. Discard results (just effects)
val discarded: Task[Unit] = 
  ZIO.foreachDiscard(ids)(id => 
    ZIO.attempt(println(s"Processing $id"))
  )
```

**Comparison with Cats:**
```scala
// Cats Effect
import cats.effect.IO
import cats.implicits._

val catsList: List[IO[String]] = ???
val catsResult: IO[List[String]] = catsList.sequence

// ZIO equivalent
val zioList: List[Task[String]] = ???
val zioResult: Task[List[String]] = ZIO.collectAll(zioList)
```

---

### 8. What is ZIO Schema and how does it enable automatic serialization?

**ZIO Schema** is a reified representation of Scala types that enables automatic derivation of codecs, migrations, diffs, and more.

```scala
import zio.schema.{DeriveSchema, Schema}

case class User(name: String, email: String, age: Int)

object User {
  // Derive schema automatically
  implicit val schema: Schema[User] = DeriveSchema.gen[User]
}

// Now you can:

// 1. Serialize to different formats
import zio.schema.codec.JsonCodec

val user = User("John", "john@example.com", 30)
val json: String = JsonCodec.encode(User.schema)(user)
// {"name":"John","email":"john@example.com","age":30}

val decoded: Either[String, User] = 
  JsonCodec.decode(User.schema)(json)

// 2. DynamoDB serialization (as in your project)
import zio.dynamodb.{ToAttributeValue, FromAttributeValue}

// Schema automatically provides these instances
val toAttr = ToAttributeValue.fromSchema(User.schema)
val fromAttr = FromAttributeValue.fromSchema(User.schema)

// 3. Diffs between values
import zio.schema.diff._

val user1 = User("John", "john@example.com", 30)
val user2 = User("John", "john@example.com", 31)

val diff = user1.diff(user2)
// Shows: age changed from 30 to 31

// 4. Migrations
import zio.schema.migration._

case class UserV2(name: String, email: String, age: Int, verified: Boolean)

object UserV2 {
  implicit val schema: Schema[UserV2] = DeriveSchema.gen[UserV2]
}

val migration: Migration[User, UserV2] = 
  Migration.addField("verified", true)

val upgraded: UserV2 = migration(user1)
```

**Why ZIO Schema is powerful:**
- **Single source of truth**: Define your type once, get everything else
- **Type-safe**: Compile-time guarantees
- **Flexible**: Works with JSON, Protobuf, DynamoDB, etc.
- **Composable**: Schemas for complex types built from simple ones

---

### 9. What is the ZIO Runtime and how does it execute effects?

The **Runtime** is the execution engine that interprets ZIO effects. It manages fiber scheduling, thread pools, and effect execution.

```scala
import zio._

// Default runtime
val runtime = Runtime.default

// Execute an effect
val effect: Task[Int] = ZIO.attempt(42)

// Method 1: Using ZIOAppDefault (recommended)
object MyApp extends ZIOAppDefault {
  def run = effect.debug  // Runtime handles execution
}

// Method 2: Unsafe execution (for testing/scripts)
Unsafe.unsafe { implicit unsafe =>
  val result: Int = runtime.unsafe.run(effect).getOrThrowFiberFailure()
}

// Custom runtime with configuration
val customRuntime = Runtime.unsafe.fromLayer(
  Runtime.removeDefaultLoggers ++ 
  Runtime.setConfigProvider(ConfigProvider.fromMap(Map.empty))
)

// Runtime provides:
// 1. Fiber management
// 2. Thread pools (compute + blocking)
// 3. Clock, Random, Console services
// 4. Error reporting
// 5. Interruption handling
```

**Thread pools in ZIO Runtime:**
```scala
// Compute pool: for CPU-bound work
// - Size: # of CPU cores by default
// - Fibers are multiplexed here
val cpuWork: Task[Int] = 
  ZIO.attempt((1 to 1000000).sum)
  // Runs on compute pool

// Blocking pool: for blocking I/O
// - Unbounded, cached thread pool
// - Prevents compute pool starvation
val blockingWork: Task[String] = 
  ZIO.attemptBlocking {
    scala.io.Source.fromFile("big-file.txt").mkString
  }
  // Runs on blocking pool

// You can also customize pools
val customRuntime = Runtime.unsafe.fromLayer(
  Runtime.setExecutor(Executor.fromThreadPoolExecutor {
    val corePoolSize = 8
    val maxPoolSize = 16
    new java.util.concurrent.ThreadPoolExecutor(
      corePoolSize, maxPoolSize, 60L, 
      java.util.concurrent.TimeUnit.SECONDS,
      new java.util.concurrent.LinkedBlockingQueue[Runnable]()
    )
  })
)
```

---

### 10. ZIO vs Cats Effect vs Future - When to use what?

**Future:**
```scala
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val future: Future[Int] = Future {
  println("Executing now!")  // Runs IMMEDIATELY
  42
}
```
✅ **Good for:** Simple async operations, legacy code  
❌ **Bad for:** Complex error handling, resource safety, testing

**Cats Effect:**
```scala
import cats.effect.IO

val io: IO[Int] = IO {
  println("Lazy!")  // Won't run until .unsafeRunSync
  42
}
```
✅ **Good for:** Pure FP, interop with Typelevel ecosystem (http4s, fs2, doobie)  
❌ **Bad for:** Learning curve, requires Cats knowledge

**ZIO:**
```scala
import zio._

val zio: Task[Int] = ZIO.attempt {
  println("Lazy!")
  42
}
```
✅ **Good for:** Typed errors, dependency injection, built-in services, great docs  
❌ **Bad for:** Ecosystem lock-in (though interop exists)

**Feature comparison:**
| Feature | Future | Cats Effect | ZIO |
|---------|--------|-------------|-----|
| Lazy | ❌ | ✅ | ✅ |
| Typed Errors | ❌ | ❌ | ✅ |
| DI Built-in | ❌ | ❌ | ✅ (ZLayer) |
| Structured Concurrency | ❌ | ✅ | ✅ |
| Resource Safety | ❌ | ✅ | ✅ |
| Learning Curve | Low | High | Medium |
| Performance | Good | Excellent | Excellent |

---

## Quick Reference

### Common ZIO Types
```scala
type Task[A]       = ZIO[Any, Throwable, A]     // Can fail
type UIO[A]        = ZIO[Any, Nothing, A]       // Cannot fail
type RIO[R, A]     = ZIO[R, Throwable, A]       // Needs env, can fail
type URIO[R, A]    = ZIO[R, Nothing, A]         // Needs env, cannot fail
type IO[E, A]      = ZIO[Any, E, A]             // No env, typed error
```

### Common Operations
```scala
// Creation
ZIO.succeed(42)                    // UIO[Int]
ZIO.fail("error")                  // IO[String, Nothing]
ZIO.attempt(riskyCode)             // Task[A]
ZIO.async[R, E, A](callback => ...)// From async callback

// Transformation
effect.map(x => x + 1)             // Transform success
effect.flatMap(x => otherEffect)   // Chain effects
effect.mapError(e => ...)          // Transform error
effect.catchAll(e => ...)          // Handle errors

// Parallelism
effect1.zipPar(effect2)            // Run 2 in parallel
ZIO.foreachPar(list)(f)            // Parallel map
ZIO.collectAllPar(effects)         // Run all in parallel

// Resources
ZIO.acquireRelease(acquire)(release)(use)
ZIO.scoped(scopedEffect)

// Time
effect.delay(1.second)             // Delay start
effect.timeout(5.seconds)          // Fail after timeout
effect.retry(Schedule.recurs(3))   // Retry on failure
```

---

## Summary

**ZIO's main advantages:**
1. **Typed errors**: Know what can fail at compile time
2. **ZLayer**: Powerful, composable dependency injection
3. **Structured concurrency**: Fibers are automatically managed
4. **Performance**: Lightweight fibers on small thread pool
5. **Testability**: Mock environment dependencies easily
6. **Ecosystem**: zio-http, zio-json, zio-config, zio-test, etc.

**When to choose ZIO:**
- Starting a new project with complex requirements
- Need strong dependency injection
- Want typed errors
- Building microservices
- Team comfortable with functional programming

Your `zio_crud` project is a great example of ZIO's strengths: typed errors, ZLayer DI, clean architecture, and integration with AWS services!



