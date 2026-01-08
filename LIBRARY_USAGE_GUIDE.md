# 📚 Library Usage Guide

This document provides a detailed mapping of where each library from `build.sbt` is used in the project, with code examples and visual diagrams.

---

## 📖 Table of Contents

1. [Core ZIO Libraries](#core-zio-libraries)
2. [AWS & Database](#aws--database)
3. [API & Documentation](#api--documentation)
4. [Testing](#testing)
5. [Visual Dependency Maps](#visual-dependency-maps)

---

## Core ZIO Libraries

### 🔵 ZIO Core

**Dependency**: `"dev.zio" %% "zio" % "2.1.21"`

**Used**: Throughout the entire project

**Key Locations**:
- `Main.scala` - `ZIO`, `ZLayer`, `ZIOAppDefault`
- `dao/UserDao.scala` - `Task`, `ZIO.succeed`, `ZIO.fail`
- `services/UserService.scala` - `IO[UserError, User]`
- `controllers/UserController.scala` - `ZIO[Any, String, User]`

**Example** (`dao/UserDao.scala:53-59`):
```scala
override def getByEmail(email: String): Task[Option[User]] = {
  executor.execute(get(tableName)(User.email.partitionKey === email))
    .flatMap {  // ← ZIO effect composition
      case Right(user) => 
        ZIO.logInfo(s"User retrieved successfully: $email") *>  // ← ZIO effect
        ZIO.succeed(Some(user))  // ← Pure value lifted into ZIO
      case Left(_) => ZIO.succeed(None)
    }
}
```

**Purpose**: The foundation of the entire application. Provides the effect system for managing side effects, composing operations, and handling errors in a type-safe, functional way.

---

### 🔵 ZIO HTTP

**Dependency**: `"dev.zio" %% "zio-http" % "3.3.3"`

**Used**: HTTP server and routing

**Key Locations**:

**File**: `controllers/UserController.scala:12`
```scala
import zio.http.{Response, Routes}  // ← HTTP types

def routes(userService: UserService): Routes[Any, Response] = {
  //                                  ^^^^^^^^^^^^^^^^^^^^^^ ZIO HTTP route type
  val allEndpoints = apiEndpoints ++ swaggerEndpoints
  ZioHttpInterpreter().toHttp(allEndpoints)
}
```

**File**: `Main.scala:13, 57`
```scala
import zio.http.Server  // ← Server component

val serverLayer: ZLayer[Any, Throwable, Unit] =
  ZLayer.fromZIO(Server.serve(routes).provide(Server.default))
  //             ^^^^^^^^^^^^^ - Starts HTTP server on port 8080
```

**Purpose**: High-performance HTTP server built on ZIO. Handles incoming HTTP requests and routes them to appropriate endpoints.

---

### 🔵 ZIO JSON

**Dependency**: `"dev.zio" %% "zio-json" % "0.7.44"`

**Used**: JSON serialization/deserialization

**Key Location**: `models/User.scala:3-13`
```scala
import zio.json.{DeriveJsonCodec, JsonCodec}  // ← JSON codecs

case class User(name: String, surName: String, email: String)

object User {
  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
  //                  ^^^^^^^^^^^^^ Automatic JSON conversion
}
```

**Data Flow**:
```
HTTP Request Body (JSON String)
    ↓
{"name": "John", "surName": "Doe", "email": "john@example.com"}
    ↓
ZIO JSON Decoder (implicit codec)
    ↓
User("John", "Doe", "john@example.com")  // Scala case class
    ↓
Business Logic
    ↓
ZIO JSON Encoder (implicit codec)
    ↓
{"name": "John", "surName": "Doe", "email": "john@example.com"}
    ↓
HTTP Response Body (JSON String)
```

**Purpose**: Automatically converts between JSON strings and Scala case classes, making REST API development seamless.

---

### 🔵 ZIO Config

**Dependency**: `"dev.zio" %% "zio-config" % "4.0.2"`

**Used**: Type-safe configuration types

**Key Location**: `config/AwsConfig.scala:3`
```scala
import zio.Config  // ← Configuration type

case class AwsConfig(region: String, accessKeyId: String, secretAccessKey: String)

object AwsConfig {
  implicit val config: Config[AwsConfig] = deriveConfig[AwsConfig].nested("aws")
  //                   ^^^^^^ Type from zio.Config library
}
```

**Purpose**: Defines the `Config[A]` type, which represents a blueprint for reading configuration in a type-safe way.

---

### 🔵 ZIO Config Typesafe

**Dependency**: `"dev.zio" %% "zio-config-typesafe" % "4.0.2"`

**Used**: Reading HOCON configuration files

**Key Location**: `Main.scala:11, 27`
```scala
import zio.config.typesafe.TypesafeConfigProvider  // ← HOCON reader

val configLayer: ZLayer[Any, Throwable, AwsConfig] =
  ZLayer.fromZIO(TypesafeConfigProvider.fromResourcePath().load[AwsConfig])
  //             ^^^^^^^^^^^^^^^^^^^^^^ Reads application.conf
```

**Configuration File** (`src/main/resources/application.conf`):
```hocon
aws {
  region = "eu-north-1"
  accessKeyId = ${AWS_ACCESS_KEY_ID}
  secretAccessKey = ${AWS_SECRET_ACCESS_KEY}
}
```

**Purpose**: Parses HOCON (Human-Optimized Config Object Notation) format from `application.conf` files.

---

### 🔵 ZIO Config Magnolia

**Dependency**: `"dev.zio" %% "zio-config-magnolia" % "4.0.2"`

**Used**: Automatic configuration decoder generation

**Key Location**: `config/AwsConfig.scala:4`
```scala
import zio.config.magnolia.DeriveConfig.deriveConfig  // ← Auto-derivation

object AwsConfig {
  implicit val config: Config[AwsConfig] = deriveConfig[AwsConfig].nested("aws")
  //                                       ^^^^^^^^^^^^^ Macro magic!
}
```

**Without Magnolia** (manual approach):
```scala
// You would have to write this manually:
implicit val config: Config[AwsConfig] = (
  Config.string("region") ++
  Config.string("accessKeyId") ++
  Config.string("secretAccessKey")
).map { case (region, keyId, secret) => AwsConfig(region, keyId, secret) }
```

**With Magnolia**:
```scala
// Just one line!
implicit val config: Config[AwsConfig] = deriveConfig[AwsConfig].nested("aws")
```

**Purpose**: Eliminates boilerplate by automatically generating configuration decoders using compile-time macros.

---

### 🔵 ZIO Logging

**Dependency**: `"dev.zio" %% "zio-logging" % "2.5.0"`

**Used**: Structured logging throughout the application

**Key Locations**:

**File**: `dao/UserDao.scala`
```scala
ZIO.logInfo(s"User retrieved successfully: $email")  // ← Success logs
ZIO.logError(s"Failed to get user $email: ${err.getMessage}")  // ← Error logs
```

**File**: `Main.scala:71-78`
```scala
ZIO.logInfo("Starting application...")
ZIO.logInfo(s"AWS_ACCESS_KEY_ID present: ${awsKeyId.isDefined}")
ZIO.logInfo("UserService loaded successfully")
```

**Output Example**:
```
timestamp=2025-12-28T13:02:01.275962Z level=INFO thread=#zio-fiber-455410048 
message="User retrieved successfully: john@example.com" 
location=dao.UserDaoImpl.getByEmail file=UserDao.scala line=55
```

**Purpose**: Functional logging where logs are ZIO effects that can be composed, tested, and have context propagation built-in.

---

### 🔵 ZIO Prelude

**Dependency**: `"dev.zio" %% "zio-prelude" % "1.0.0-RC36"`

**Used**: Type-safe validation with error accumulation

**Key Location**: `models/UserValidation.scala:3-4`
```scala
import zio.prelude.Validation  // ← Validation type
import zio.prelude._

object UserValidation {
  private def validateEmail(email: String): Validation[String, String] = {
    if (email.contains("@") && email.contains(".")) 
      Validation.succeed(email)
    else 
      Validation.fail(s"Invalid email format: '$email'")
  }

  def validate(user: User): IO[UserError, Unit] = {
    val validations = Validation.validateAll(  // ← Accumulates ALL errors
      validateEmail(user.email),
      validateName(user.name),
      validateSurname(user.surName)
    )
    // ...
  }
}
```

**Comparison**:

**Without ZIO Prelude** (stops at first error):
```scala
for {
  _ <- validateEmail(email)     // Fails here? Stops immediately
  _ <- validateName(name)       // Never reached
  _ <- validateSurname(surname) // Never reached
} yield ()
```

**With ZIO Prelude** (collects all errors):
```scala
Validation.validateAll(
  validateEmail(email),     // Invalid email
  validateName(name),       // Empty name
  validateSurname(surname)  // Empty surname
)
// Returns: "Invalid input: Invalid email format: 'bad', Name is required, Surname is required"
```

**Purpose**: Provides composable validation with error accumulation, so users see all validation errors at once instead of one at a time.

---

## AWS & Database

### 🔵 ZIO DynamoDB

**Dependency**: `"dev.zio" %% "zio-dynamodb" % "1.0.0-RC24"`

**Used**: High-level, type-safe DynamoDB API

**Key Locations**:

**File**: `dao/UserDao.scala:5-6`
```scala
import zio.dynamodb._  // ← High-level API
import zio.dynamodb.DynamoDBQuery._

override def getByEmail(email: String): Task[Option[User]] = {
  executor.execute(get(tableName)(User.email.partitionKey === email))
  //                ^^^ High-level query builder
  //                                    ^^^^^^^^^^^^^^^^^ Type-safe query
}
```

**File**: `models/User.scala:5, 17-19`
```scala
import zio.dynamodb.ProjectionExpression  // ← Type-safe field accessors
import zio.schema.{DeriveSchema, Schema => ZioSchema}

object User {
  implicit val zioSchema: ZioSchema.CaseClass3[String, String, String, User] = 
    DeriveSchema.gen[User]
  
  val (name, surName, email) = ProjectionExpression.accessors[User]
  //  ^^^ Compile-time safe field names for queries
}
```

**Type Safety Example**:
```scala
// ✅ Compiles - 'email' is a valid User field
get(tableName)(User.email.partitionKey === "john@example.com")

// ❌ Doesn't compile - 'age' is not a User field
get(tableName)(User.age.partitionKey === 30)
//                  ^^^ Compilation error!
```

**Purpose**: Provides a type-safe, functional API on top of AWS DynamoDB with automatic serialization/deserialization using ZIO Schema.

---

### 🔵 ZIO AWS DynamoDB

**Dependency**: `"dev.zio" %% "zio-aws-dynamodb" % "7.39.6.4"`

**Used**: Low-level AWS SDK wrapper

**Key Location**: `Main.scala:9, 41-45`
```scala
import zio.aws.dynamodb.DynamoDb  // ← Low-level client

val dynamoDbLayer: ZLayer[HttpClient & CommonAwsConfig, Throwable, DynamoDb] = 
  zio.aws.dynamodb.DynamoDb.live
  //                        ^^^^ AWS SDK wrapper
```

**Architecture**:
```
Your Code (UserDao)
    ↓
ZIO DynamoDB (high-level, type-safe)
    ↓
ZIO AWS DynamoDB (low-level, AWS SDK wrapper) ← THIS LIBRARY
    ↓
AWS DynamoDB Service
```

**Purpose**: Wraps the official AWS SDK for DynamoDB in ZIO effects. Used internally by `zio-dynamodb` for actual AWS communication.

---

### 🔵 ZIO AWS Netty

**Dependency**: `"dev.zio" %% "zio-aws-netty" % "7.39.6.4"`

**Used**: HTTP client for AWS SDK

**Key Location**: `Main.scala:10, 38-39`
```scala
import zio.aws.netty.NettyHttpClient  // ← Netty-based HTTP client

val nettyLayer: ZLayer[Any, Throwable, HttpClient] = 
  NettyHttpClient.default
  // ^^^^^^^^^^^^^^ Uses Netty for network I/O
```

**Network Stack**:
```
Your Code
    ↓
ZIO AWS DynamoDB
    ↓
ZIO AWS Netty ← THIS LIBRARY (HTTP transport)
    ↓
Netty (async I/O)
    ↓
Network → AWS DynamoDB Service
```

**Purpose**: Provides an efficient, asynchronous HTTP client based on Netty for communicating with AWS services. Alternative to the default Apache HTTP client.

---

## API & Documentation

### 🔵 Tapir ZIO HTTP Server

**Dependency**: `"com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % "1.13.3"`

**Used**: Converting Tapir endpoints to ZIO HTTP routes

**Key Location**: `controllers/UserController.scala:9, 119`
```scala
import sttp.tapir.server.ziohttp.ZioHttpInterpreter  // ← Integration layer

def routes(userService: UserService): Routes[Any, Response] = {
  val apiEndpoints = userEndpoint(userService)
  val swaggerEndpoints = SwaggerInterpreter()
    .fromServerEndpoints[Task](apiEndpoints, "ZIO CRUD API", "1.0.0")
  
  val allEndpoints = apiEndpoints ++ swaggerEndpoints
  
  ZioHttpInterpreter().toHttp(allEndpoints)
  // ^^^^^^^^^^^^^^^^^^^ Converts Tapir → ZIO HTTP
}
```

**Flow**:
```
Tapir Endpoint Description (declarative, type-safe)
    ↓
ZioHttpInterpreter.toHttp()  ← THIS LIBRARY
    ↓
ZIO HTTP Routes (executable)
    ↓
ZIO HTTP Server
```

**Purpose**: Bridges Tapir's declarative endpoint definitions with ZIO HTTP's server implementation.

---

### 🔵 Tapir JSON ZIO

**Dependency**: `"com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.13.3"`

**Used**: JSON codec integration between Tapir and ZIO JSON

**Key Location**: `controllers/UserController.scala:7, 30`
```scala
import sttp.tapir.json.zio._  // ← Imports jsonBody[T] using ZIO JSON

private val createUser: PublicEndpoint[User, String, Unit, Any] = endpoint
  .post
  .in("users")
  .in(jsonBody[User])  // ← Automatically uses implicit JsonCodec[User] from ZIO JSON
  .out(statusCode(StatusCode.Created))
```

**Integration**:
```
HTTP Request JSON
    ↓
Tapir jsonBody[User]  ← THIS LIBRARY (knows to use ZIO JSON)
    ↓
ZIO JSON implicit codec
    ↓
User case class
```

**Purpose**: Makes Tapir aware of ZIO JSON codecs, so `jsonBody[User]` automatically uses the `JsonCodec[User]` defined in the User companion object.

---

### 🔵 Tapir Swagger UI Bundle

**Dependency**: `"com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.13.3"`

**Used**: Auto-generating Swagger UI documentation

**Key Location**: `controllers/UserController.scala:10, 113-114`
```scala
import sttp.tapir.swagger.bundle.SwaggerInterpreter  // ← Swagger generator

val swaggerEndpoints = SwaggerInterpreter()
  .fromServerEndpoints[Task](apiEndpoints, "ZIO CRUD API", "1.0.0")
  //                          ^^^^^^^^^^^^ Generates OpenAPI spec + UI
```

**Endpoint Metadata** (`controllers/UserController.scala:27-35`):
```scala
private val createUser: PublicEndpoint[User, String, Unit, Any] = endpoint
  .post
  .in("users")
  .in(jsonBody[User])
  .errorOut(stringBody)
  .out(statusCode(StatusCode.Created))
  .name("Create user")  // ← Shows in Swagger
  .description("Create a new user with validation (email format, required fields, uniqueness check)")  // ← Shows in Swagger
  .tag("Users")  // ← Groups in Swagger
```

**Result**: Visit `http://localhost:8080/docs` to see interactive API documentation with "Try it out" functionality.

**Purpose**: Automatically generates OpenAPI/Swagger documentation and serves an interactive UI for exploring and testing the API.

---

## Testing

### 🔵 ZIO Test

**Dependency**: `"dev.zio" %% "zio-test" % "2.1.23" % Test`

**Used**: Test framework for all tests

**Key Location**: `test/scala/models/UserValidationSpec.scala:3`
```scala
import zio.test._  // ← Test DSL

object UserValidationSpec extends ZIOSpecDefault {
  //                        ^^^^^^^^^^^^^^ Base class for ZIO tests
  
  def spec = suite("UserValidation")(
    test("should reject invalid email format") {
      val user = User("John", "Doe", "invalid-email")
      val result = UserValidation.validate(user)
      
      assertZIO(result.exit)(fails(anything))
      //        ^^^^^^ Tests ZIO effects
    },
    test("should accept valid user") {
      val user = User("John", "Doe", "john@example.com")
      val result = UserValidation.validate(user)
      
      assertZIO(result.exit)(succeeds(anything))
    }
  )
}
```

**Key Features**:
- `suite()` - Groups related tests
- `test()` - Individual test case
- `assertZIO()` - Assertions for ZIO effects
- `succeeds()` / `fails()` - Success/failure matchers

**Purpose**: Powerful testing framework built on ZIO with excellent support for testing effects, property-based testing, and test aspects.

---

### 🔵 ZIO Test SBT

**Dependency**: `"dev.zio" %% "zio-test-sbt" % "2.1.23" % Test`

**Used**: SBT integration for running tests

**Key Location**: `build.sbt:20`
```scala
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
//                                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Test runner
```

**Enables**:
```bash
$ sbt test
[info] UserValidation
[info]   + should reject invalid email format
[info]   + should reject empty surname
[info]   + should reject empty name
[info]   + should accept valid user
[info] UserService
[info]   + should create user successfully
[info]   + should fail to create duplicate user
[info]   ...
[info] 13 tests passed. 0 tests failed. 0 tests ignored.
```

**Purpose**: Integrates ZIO Test with SBT build tool, providing proper test discovery, execution, and reporting.

---

## Visual Dependency Maps

### 🗺️ Configuration Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    application.conf (HOCON file)                    │
│  aws {                                                               │
│    region = "eu-north-1"                                            │
│    accessKeyId = ${AWS_ACCESS_KEY_ID}                               │
│    secretAccessKey = ${AWS_SECRET_ACCESS_KEY}                       │
│  }                                                                   │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│              ZIO Config Typesafe (reads HOCON format)               │
│  TypesafeConfigProvider.fromResourcePath().load[AwsConfig]          │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│     ZIO Config Magnolia (generates decoder via macro magic)         │
│  deriveConfig[AwsConfig].nested("aws")                              │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│              ZIO Config (provides Config[AwsConfig] type)           │
│  implicit val config: Config[AwsConfig]                             │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                    AwsConfig case class instance                    │
│  AwsConfig(region = "eu-north-1", accessKeyId = "...", ...)         │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 🗺️ HTTP Request Processing Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                   HTTP Request (curl, browser)                      │
│  POST http://localhost:8080/users                                   │
│  Body: {"name": "John", "surName": "Doe", "email": "john@ex.com"}  │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      ZIO HTTP Server                                │
│  Server.serve(routes).provide(Server.default)                       │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                   Tapir ZIO HTTP Server                             │
│  ZioHttpInterpreter().toHttp(endpoints)                             │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      Tapir JSON ZIO                                 │
│  jsonBody[User] → uses ZIO JSON codec                               │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        ZIO JSON                                     │
│  JsonCodec[User].decode(json) → User case class                     │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                       Controller Layer                              │
│  createUserLogic(user) → validates email in path                    │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        Service Layer                                │
│  + ZIO Prelude validation (email format, required fields)          │
│  + Business logic (duplicate check)                                 │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                          DAO Layer                                  │
│  + ZIO Logging (success/error logs)                                │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                       ZIO DynamoDB                                  │
│  High-level API: put(tableName)(user)                               │
│  Type-safe queries with ZIO Schema                                  │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                     ZIO AWS DynamoDB                                │
│  Low-level AWS SDK wrapper                                          │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      ZIO AWS Netty                                  │
│  HTTP client for AWS communication                                  │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                     AWS DynamoDB Service                            │
│  Stores data in the cloud                                           │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 🗺️ Testing Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         sbt test command                            │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                       ZIO Test SBT                                  │
│  Test discovery and execution via SBT                               │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────────────┐
│                         ZIO Test                                    │
│  Test framework (suite, test, assertZIO)                            │
└────────────────────────────┬────────────────────────────────────────┘
                             ↓
                    ┌────────┴────────┐
                    ↓                 ↓
┌──────────────────────────┐  ┌──────────────────────────┐
│  UserValidationSpec      │  │  UserServiceSpec         │
│  (tests validation)      │  │  (tests business logic)  │
└──────────┬───────────────┘  └───────────┬──────────────┘
           ↓                              ↓
┌──────────────────────────┐  ┌──────────────────────────┐
│  ZIO Prelude             │  │  TestUserDao             │
│  (validation logic)      │  │  (in-memory mock)        │
└──────────────────────────┘  └──────────────────────────┘
```

---

### 🗺️ Complete Dependency Graph

```
┌─────────────────────────────────────────────────────────────────────┐
│                            Your Code                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │  Controller  │→→│   Service    │→→│     DAO      │             │
│  │   (Tapir)    │  │  (Business)  │  │  (Database)  │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└───────────┬─────────────────┬─────────────────┬───────────────────┘
            ↓                 ↓                 ↓
┌───────────────────┐ ┌──────────────┐ ┌──────────────────┐
│  Tapir Libraries  │ │ ZIO Prelude  │ │ ZIO DynamoDB     │
│  - ZIO HTTP       │ │ (Validation) │ │ (High-level API) │
│  - JSON ZIO       │ └──────────────┘ └────────┬─────────┘
│  - Swagger UI     │                           ↓
└─────────┬─────────┘                  ┌──────────────────┐
          ↓                            │ ZIO AWS DynamoDB │
┌───────────────────┐                  │ (Low-level SDK)  │
│    ZIO HTTP       │                  └────────┬─────────┘
│   (Web Server)    │                           ↓
└─────────┬─────────┘                  ┌──────────────────┐
          ↓                            │  ZIO AWS Netty   │
┌───────────────────┐                  │  (HTTP Client)   │
│     ZIO Core      │                  └────────┬─────────┘
│   (Foundation)    │◄─────────────────────────┘
└─────────┬─────────┘
          ↓
┌───────────────────────────────────────────────────┐
│         Supporting Libraries                      │
│  - ZIO JSON (serialization)                       │
│  - ZIO Config + Typesafe + Magnolia (config)     │
│  - ZIO Logging (structured logs)                 │
└───────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────┐
│              Testing Layer                        │
│  - ZIO Test + ZIO Test SBT                        │
└───────────────────────────────────────────────────┘
```

---

## 📝 Summary Table

| Library | Primary File(s) | Key Import | Purpose |
|---------|----------------|------------|---------|
| **ZIO Core** | All files | `zio._` | Effect system foundation |
| **ZIO HTTP** | `Main.scala`, `UserController.scala` | `zio.http._` | HTTP server |
| **ZIO JSON** | `User.scala` | `zio.json._` | JSON serialization |
| **ZIO Config** | `AwsConfig.scala` | `zio.Config` | Config types |
| **ZIO Config Typesafe** | `Main.scala` | `zio.config.typesafe._` | HOCON reader |
| **ZIO Config Magnolia** | `AwsConfig.scala` | `zio.config.magnolia._` | Auto-derivation |
| **ZIO Logging** | `Main.scala`, `UserDao.scala` | `ZIO.logInfo` | Logging |
| **ZIO Prelude** | `UserValidation.scala` | `zio.prelude._` | Validation |
| **ZIO DynamoDB** | `UserDao.scala`, `User.scala` | `zio.dynamodb._` | High-level DB API |
| **ZIO AWS DynamoDB** | `Main.scala` | `zio.aws.dynamodb._` | Low-level SDK |
| **ZIO AWS Netty** | `Main.scala` | `zio.aws.netty._` | HTTP transport |
| **Tapir ZIO HTTP** | `UserController.scala` | `sttp.tapir.server.ziohttp._` | Endpoint interpreter |
| **Tapir JSON ZIO** | `UserController.scala` | `sttp.tapir.json.zio._` | JSON codec integration |
| **Tapir Swagger UI** | `UserController.scala` | `sttp.tapir.swagger.bundle._` | API documentation |
| **ZIO Test** | `test/scala/**/*Spec.scala` | `zio.test._` | Testing framework |
| **ZIO Test SBT** | `build.sbt` | N/A | Test runner |

---

## 🎯 Quick Reference: Where to Look

**Need to understand...**
- **Configuration loading?** → `Main.scala` (lines 26-27) + `AwsConfig.scala`
- **HTTP routing?** → `UserController.scala` (lines 109-120)
- **JSON conversion?** → `User.scala` (lines 11-13)
- **Database queries?** → `UserDao.scala` (lines 53-62, 79-84)
- **Validation logic?** → `UserValidation.scala` (lines 13-34)
- **Logging setup?** → Any DAO/Service file (search for `ZIO.logInfo`)
- **Test structure?** → `test/scala/services/UserServiceSpec.scala`
- **Swagger docs?** → `UserController.scala` (lines 113-114, 27-35)

---

## 📚 Further Reading

- [ZIO Documentation](https://zio.dev)
- [Tapir Documentation](https://tapir.softwaremill.com)
- [ZIO DynamoDB GitHub](https://github.com/zio/zio-dynamodb)
- [ZIO Prelude Guide](https://zio.dev/zio-prelude)

---

*This guide is part of the ZIO CRUD educational project. See [README.md](./README.md) for more information.*

