# ZIO CRUD Web Service

## 📋 Project Description

This is a modern web service built using the **ZIO ecosystem** that provides CRUD (Create, Read, Update, Delete) operations for user management. The service communicates with **AWS DynamoDB** as its database backend, leveraging functional programming principles and type-safe APIs.

The application is built with a clean architecture approach:
- **Controller Layer** - handles HTTP requests using Tapir endpoints
- **Service Layer** - contains business logic
- **DAO Layer** - manages database interactions with DynamoDB
- **Models** - domain objects with automatic serialization/deserialization
- **Config Layer** - type-safe configuration management for AWS and application settings

The service exposes RESTful API endpoints and provides interactive API documentation through **Swagger UI**.

---

## 🛠️ Technology Stack

### Core ZIO Libraries

- **ZIO Core** - Functional effect system for Scala providing powerful tools for asynchronous and concurrent programming with strong type safety and error handling.

- **ZIO HTTP** - High-performance HTTP server built on top of ZIO, providing a functional approach to building web services with excellent scalability.

- **ZIO JSON** - Fast and type-safe JSON library for serialization and deserialization, essential for REST API endpoints to convert between JSON and Scala case classes.

- **ZIO Config** - Type-safe configuration library that maps configuration files to Scala case classes, ensuring compile-time safety for application configuration.

- **ZIO Config Typesafe** - Integration with Typesafe Config (HOCON format) allowing to use standard `application.conf` files for configuration management.

- **ZIO Config Magnolia** - Automatic derivation of configuration decoders using Magnolia macro library, eliminating boilerplate code for configuration mapping.

- **ZIO Logging** - Functional logging library integrated with ZIO effects, providing structured logging with context propagation.

### AWS & Database

- **ZIO DynamoDB** - High-level, type-safe API for AWS DynamoDB with automatic serialization/deserialization using ZIO Schema.

- **ZIO AWS DynamoDB** - Low-level AWS SDK wrapper for DynamoDB, providing direct access to the full DynamoDB API when advanced features are needed.

- **ZIO AWS Netty** - Netty-based HTTP client for AWS SDK, offering efficient network communication with AWS services.

### API & Documentation

- **Tapir ZIO HTTP Server** - Integration between Tapir (type-safe API endpoints description library) and ZIO HTTP, enabling declarative endpoint definitions.

- **Tapir JSON ZIO** - JSON codec support for Tapir endpoints using ZIO JSON, providing seamless JSON serialization for API requests/responses.

- **Tapir Swagger UI Bundle** - Automatic OpenAPI/Swagger documentation generation and interactive API explorer UI.

### Testing

- **ZIO Test** - Powerful testing framework built on ZIO with property-based testing, test aspects, and excellent integration with ZIO effects.

- **ZIO Test SBT** - SBT plugin for running ZIO tests with proper reporting and integration with build tools.

---

## 🚀 How to Use

### Prerequisites

1. **AWS Credentials** - Set environment variables:
   ```bash
   export AWS_ACCESS_KEY_ID=your_access_key
   export AWS_SECRET_ACCESS_KEY=your_secret_key
   ```

2. **DynamoDB Table** - Create a table named `Users` with:
   - **Partition Key**: `email` (String)

3. **Scala & SBT** - Ensure you have Scala and SBT installed

### Running the Application

```bash
sbt run
```

The server will start on `http://localhost:8080`

**Swagger UI** will be available at: `http://localhost:8080/docs`

---

## 📡 API Endpoints

### 1. Create User

**POST** `/users`

Creates a new user in the database.

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John",
    "surName": "Doe",
    "email": "john.doe@example.com"
  }'
```

**Response:** `200 OK` (empty body on success)

---

### 2. Get User by Email

**GET** `/users?email={email}`

Retrieves a user by their email address.

```bash
curl -X GET "http://localhost:8080/users/?email=john.doe@example.com"
```

**Response:**
```json
{
  "name": "John",
  "surName": "Doe",
  "email": "john.doe@example.com"
}
```

---

### 3. Update User

**PUT** `/users?email={email}`

Updates user information. **Note:** Email cannot be changed as it's the primary key in DynamoDB.

```bash
curl -X PUT "http://localhost:8080/users/?email=john.doe@example.com" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Updated",
    "surName": "Doe Updated",
    "email": "john.doe@example.com"
  }'
```

**Response:** `true` (success) or `false` (user not found)

---

### 4. Delete User

**DELETE** `/users?email={email}`

Deletes a user by email address.

```bash
curl -X DELETE "http://localhost:8080/users/?email=john.doe@example.com"
```

**Response:** `true` (deleted) or `false` (user not found)

---

### 5. List All Users

**GET** `/users/list`

Retrieves all users from the database.

```bash
curl -X GET http://localhost:8080/users/list
```

**Response:**
```json
[
  {
    "name": "John",
    "surName": "Doe",
    "email": "john.doe@example.com"
  },
  {
    "name": "Jane",
    "surName": "Smith",
    "email": "jane.smith@example.com"
  }
]
```

---

## 📝 Project Architecture

```
src/main/scala/
├── Main.scala                    # Application entry point
├── config/
│   └── AwsConfig.scala          # AWS configuration model
├── models/
│   └── User.scala               # User domain model
├── dao/
│   └── UserDao.scala            # Data access layer
├── services/
│   └── UserService.scala        # Business logic layer
└── controllers/
    └── UserController.scala     # HTTP endpoints & Swagger UI
```

### Design Patterns

- **Layered Architecture** - Clear separation between Controller → Service → DAO → Database
- **Dependency Injection** - Using ZIO's ZLayer for composable dependencies
- **Type-Safe Configuration** - Compile-time checked configuration using ZIO Config
- **Functional Error Handling** - Using ZIO's Task and error channels instead of exceptions

---

## 🎓 Educational Purpose & Future Improvements

This project was created for **educational purposes** to practice and demonstrate the **ZIO ecosystem** capabilities. It showcases:
- Functional programming principles
- Type-safe API development
- AWS DynamoDB integration
- Dependency injection with ZLayer
- Effect management with ZIO

### Potential Enhancements

The following features could be added to make this production-ready:

1. **Authentication & Authorization**
   - JWT token-based authentication
   - Role-based access control (RBAC)
   - OAuth2 integration

2. **Input Validation**
   - Email format validation
   - Required field checks
   - Business rules validation (e.g., unique constraints)

3. **Testing**
   - Unit tests for business logic
   - Integration tests with test containers
   - Property-based testing
   - Mock DynamoDB for testing

4. **Observability**
   - Metrics collection (Prometheus)
   - Distributed tracing (OpenTelemetry)
   - Structured logging with correlation IDs

5. **Resilience**
   - Retry policies for AWS operations
   - Circuit breakers
   - Rate limiting

6. **API Versioning**
   - Support for multiple API versions
   - Backward compatibility handling

---

## 📄 License

This project is for educational purposes.

---

## 🤝 Contributing

Feel free to explore, learn, and extend this project for your own educational purposes!

