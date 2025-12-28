package dao

import models.User
import zio._
import zio.dynamodb.DynamoDBQuery._
import zio.dynamodb._

// UIO type - this is clear async operation ZIO[Any, Nothing, A] which always return success result (needed for in-memory realization)
// In real realization with AWS, I should use Task[A], because operations can fails;

trait UserDao {
  def create(user: User): Task[Unit]

  def getByEmail(email: String): Task[Option[User]]

  def update(user: User): Task[Boolean]

  def delete(email: String): Task[Boolean]

  def list(): Task[List[User]]
}

/*
The UserDao object is needed to conveniently create a ZLayer - this is how you describe how UserDao will be presented to the rest of the application.
This is a standard in the ZIO world (pattern: trait + object with live).
UserDao.live - declares a “production” implementation of the dao as a ZLayer:
This allows you to automatically inject this DAO into your service or controller via DI, without manually writing all dependencies throughout the application.
 */

object UserDao {
  def live: URLayer[DynamoDBExecutor, UserDao] =
    ZLayer.fromFunction(new UserDaoImpl(_))
}


class UserDaoImpl(executor: DynamoDBExecutor) extends UserDao {
  private val tableName = "Users"
  override def create(user: User): Task[Unit] = {
    executor.execute(put(tableName, user))
      .tap(_ => ZIO.logInfo(s"User created successfully: ${user.email}"))
      .mapError(err => new Exception(s"DynamoDB error: $err"))
      .tapError(err => ZIO.logError(s"Failed to create user ${user.email}: ${err.getMessage}"))
      .unit
  }


  override def getByEmail(email: String): Task[Option[User]] = {
    executor.execute(get(tableName)(User.email.partitionKey === email))
      .flatMap {
        case Right(user) => 
          ZIO.logInfo(s"User retrieved successfully: $email") *>
          ZIO.succeed(Some(user))
        case Left(_) => 
          ZIO.succeed(None)
      }
      .mapError(err => new Exception(s"DynamoDB error: $err"))
      .tapError(err => ZIO.logError(s"Failed to get user $email: ${err.getMessage}"))
  }

  override def update(user: User): Task[Boolean] = {
    (for {
      existing <- getByEmail(user.email)
      result <- existing match {
        case Some(_) =>
          executor.execute(put(tableName, user))
            .tap(_ => ZIO.logInfo(s"User updated successfully: ${user.email}"))
            .as(true)
            .mapError(err => new Exception(s"DynamoDB error: $err"))
        case None =>
          ZIO.succeed(false)
      }
    } yield result)
      .tapError(err => ZIO.logError(s"Failed to update user ${user.email}: ${err.getMessage}"))
  }

  override def delete(email: String): Task[Boolean] = {
    (for {
      existing <- getByEmail(email)
      result <- existing match {
        case Some(_) =>
          executor.execute(deleteItem(tableName, PrimaryKey("email" -> email)))
            .tap(_ => ZIO.logInfo(s"User deleted successfully: $email"))
            .as(true)
            .mapError(err => new Exception(s"DynamoDB error: $err"))
        case None =>
          ZIO.succeed(false)
      }
    } yield result)
      .tapError(err => ZIO.logError(s"Failed to delete user $email: ${err.getMessage}"))
  }

  override def list(): Task[List[User]] = {
    executor.execute(scanAll[User](tableName))
      .flatMap(stream => stream.runCollect)
      .map(_.toList)
      .tap(users => ZIO.logInfo(s"Listed ${users.size} users"))
      .mapError(err => new Exception(s"DynamoDB error: $err"))
      .tapError(err => ZIO.logError(s"Failed to list users: ${err.getMessage}"))
  }
}


//TODO example for in-memory CRUD implementation
//private class InMemoryUserDao(ref: Ref[Map[String, User]]) extends UserDao {
//  def create(user: User): Task[Unit] =
//    ref.update(_ + (user.email -> user)).unit
//
//  def getByEmail(email: String): Task[Option[User]] =
//    ref.get.map(_.get(email))
//
//  def update(user: User): Task[Boolean] =
//    ref.modify { map =>
//      if (map.contains(user.email))
//        (true, map.updated(user.email, user))
//      else
//        (false, map)
//    }
//
//  def delete(email: String): Task[Boolean] =
//    ref.modify { map =>
//      if (map.contains(email))
//        (true, map - email)
//      else
//        (false, map)
//    }
//
//  def list(): Task[List[User]] =
//    ref.get.map(_.values.toList)
//}
//
//object InMemoryUserDao {
//  val layer: ULayer[UserDao] =
//    ZLayer.fromZIO(Ref.make(Map.empty[String, User]).map(new InMemoryUserDao(_)))
//}


