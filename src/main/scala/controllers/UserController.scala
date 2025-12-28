package controllers

import models.User
import services.UserService

import sttp.tapir._
import sttp.tapir.json.zio._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import zio.{Task, ZIO}
import zio.http.{Response, Routes}


object UserController {
  // Step 1: describe all endpoint with tapir (remember endpoints need jsonCodec for json Serialization and user schema for OpenAPI(swagger), look at user Model)

  val listUsers: PublicEndpoint[Unit, String, List[User], Any] =
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
    .out(emptyOutput)
    .name("Create user")
    .description("Create a new user")
    .tag("Users")

  val getUser: PublicEndpoint[String, String, User, Any] =
    endpoint.get
      .in("users" / query[String]("email"))
      .errorOut(stringBody)
      .out(jsonBody[User])
      .name("Get user by email")
      .description("Retrieve a user by their email address")
      .tag("Users")


  val updateUser: PublicEndpoint[(String, User), String, Boolean, Any] =
    endpoint.put
      .in("users" / query[String]("email"))
      .in(jsonBody[User])
      .errorOut(stringBody)
      .out(jsonBody[Boolean])
      .name("Update user")
      .description("Update user information (email cannot be changed as it's the primary key)")
      .tag("Users")


  val deleteUser: PublicEndpoint[String, String, Boolean, Any] =
    endpoint.delete
      .in("users" / query[String]("email"))
      .errorOut(stringBody)
      .out(jsonBody[Boolean])
      .name("Delete user")
      .description("Delete a user by email")
      .tag("Users")


  // Step 2: Logic implementation based on UserService (controller -> service -> dao -> DB)
  def createUserLogic(userService: UserService): User => ZIO[Any, String, Unit] =
    user => userService.create(user).mapError(_.getMessage)

  def getUserLogic(userService: UserService): String => ZIO[Any, String, User] =
    email =>
      userService.getByEmail(email).flatMap {
        case Some(user) => ZIO.succeed(user)
        case None => ZIO.fail("User not found")
      }.mapError {
        case t: Throwable => t.getMessage
        case s: String => s
        case other => other.toString
      }


  def updateUserLogic(userService: UserService): ((String, User)) => ZIO[Any, String, Boolean] = {
    case (email, user) =>
      userService.update(user).flatMap { updated =>
        if (updated) ZIO.succeed(true)
        else ZIO.fail("User not found for update")
      }.mapError {
        case t: Throwable => t.getMessage
        case s: String => s
        case other => other.toString
      }
  }

  def deleteUserLogic(userService: UserService): String => ZIO[Any, String, Boolean] =
    email =>
      userService.delete(email).flatMap { deleted =>
        if (deleted) ZIO.succeed(true)
        else ZIO.fail("User not found for delete")
      }.mapError {
        case t: Throwable => t.getMessage
        case s: String => s
        case other => other.toString
      }


  def listUsersLogic(userService: UserService): Unit => ZIO[Any, String, List[User]] =
    _ =>
      userService.list().mapError {
        case t: Throwable => t.getMessage
        case other => other.toString
      }


  //Step 3: collect all endpoints with realization

  /*
  .either is a ZIO method that translates ZIO[Any, String, Out] to ZIO[Any, Nothing, Either[String, Out]]
  (that means, all errors will be Left, all success will be Right, as Tapir expects)
   */
  def userEndpoint(userService: UserService): List[ServerEndpoint[Any, Task]] = List(
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