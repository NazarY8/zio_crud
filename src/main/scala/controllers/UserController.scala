package controllers

import models.User
import services.UserService
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.zio._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.{Task, ZIO}
import zio.http.{Response, Routes}


object UserController {
  // Step 1: describe all endpoint with tapir (remember endpoints need jsonCodec for json Serialization and user schema for OpenAPI(swagger), look at user Model)

  private val listUsers: PublicEndpoint[Unit, String, List[User], Any] =
    endpoint.get
      .in("users" / "list")
      .errorOut(stringBody)
      .out(jsonBody[List[User]])
      .name("List all users")
      .description("Retrieve a list of all users")
      .tag("Users")

  private val createUser: PublicEndpoint[User, String, Unit, Any] = endpoint
    .post
    .in("users")
    .in(jsonBody[User])
    .errorOut(stringBody)
    .out(statusCode(StatusCode.Created))
    .name("Create user")
    .description("Create a new user with validation (email format, required fields, uniqueness check)")
    .tag("Users")

  private val getUser: PublicEndpoint[String, String, User, Any] =
    endpoint.get
      .in("users" / query[String]("email"))
      .errorOut(stringBody)
      .out(jsonBody[User])
      .name("Get user by email")
      .description("Retrieve a user by their email address")
      .tag("Users")


  private val updateUser: PublicEndpoint[(String, User), String, Unit, Any] =
    endpoint.put
      .in("users" / query[String]("email"))
      .in(jsonBody[User])
      .errorOut(stringBody)
      .out(statusCode(StatusCode.NoContent))
      .name("Update user")
      .description("Update user information with validation. Email cannot be changed as it's the primary key - email in URL must match email in body")
      .tag("Users")


  private val deleteUser: PublicEndpoint[String, String, Unit, Any] =
    endpoint.delete
      .in("users" / query[String]("email"))
      .errorOut(stringBody)
      .out(statusCode(StatusCode.NoContent))
      .name("Delete user")
      .description("Delete a user by email address")
      .tag("Users")


  // Step 2: Logic implementation based on UserService (controller -> service -> dao -> DB),
  // right now we removed business checks and validation from Controller and moved it to Service. This approach more about best practices.
  private def createUserLogic(userService: UserService): User => ZIO[Any, String, Unit] =
    user => userService.create(user).mapError(_.message)

  private def getUserLogic(userService: UserService): String => ZIO[Any, String, User] =
    email => userService.getByEmail(email).mapError(_.message)

  private def updateUserLogic(userService: UserService): ((String, User)) => ZIO[Any, String, Unit] = {
    case (email, user) =>
      // Ensure email from path matches email in body
      if (email != user.email) {
        ZIO.fail("Email in URL must match email in request body")
      } else {
        userService.update(user).mapError(_.message)
      }
  }

  private def deleteUserLogic(userService: UserService): String => ZIO[Any, String, Unit] =
    email => userService.delete(email).mapError(_.message)

  private def listUsersLogic(userService: UserService): Unit => ZIO[Any, String, List[User]] =
    _ => userService.list().mapError(_.message)


  //Step 3: collect all endpoints with realization

  /*
  .either is a ZIO method that translates ZIO[Any, String, Out] to ZIO[Any, Nothing, Either[String, Out]]
  (that means, all errors will be Left, all success will be Right, as Tapir expects)
   */
  private def userEndpoint(userService: UserService): List[ServerEndpoint[Any, Task]] = List(
    createUser.serverLogic(user => createUserLogic(userService)(user).either),
    getUser.serverLogic(email => getUserLogic(userService)(email).either),
    updateUser.serverLogic { case (email, user) => updateUserLogic(userService)((email, user)).either },
    deleteUser.serverLogic(email => deleteUserLogic(userService)(email).either),
    listUsers.serverLogic(_ => listUsersLogic(userService)(()).either)
  )


  // Step 4: Made zio-http routes from Tapir endpoint
  def routes(userService: UserService): Routes[Any, Response] = {
    val apiEndpoints = userEndpoint(userService)
    
    // Generate Swagger UI documentation
    val swaggerEndpoints = SwaggerInterpreter()
      .fromServerEndpoints[Task](apiEndpoints, "ZIO CRUD API", "1.0.0")
    
    // Combine API routes with Swagger UI routes
    val allEndpoints = apiEndpoints ++ swaggerEndpoints
    
    ZioHttpInterpreter().toHttp(allEndpoints)
  }
}