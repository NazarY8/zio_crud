package services

import dao.UserDao
import models.{User, UserError, UserValidation}
import zio.{IO, Task, URLayer, ZIO, ZLayer}


trait UserService {
  def create(user: User): IO[UserError, Unit]

  def getByEmail(email: String): IO[UserError, User]

  def update(user: User): IO[UserError, Unit]

  def delete(email: String): IO[UserError, Unit]

  def list(): IO[UserError, List[User]]
}

object UserService {
  def live: URLayer[UserDao, UserService] =
    ZLayer.fromFunction(UserServiceImpl(_))
}

final case class UserServiceImpl(userDao: UserDao) extends UserService {

  override def create(user: User): IO[UserError, Unit] = {
    for {
      _ <- UserValidation.validate(user) // Validate input
      existing <- userDao.getByEmail(user.email)
        .mapError(err => UserError.InvalidInput(List(err.getMessage)))
      _ <- existing match {
        case Some(_) => ZIO.fail(UserError.AlreadyExists(user.email))
        case None => userDao.create(user)
          .mapError(err => UserError.InvalidInput(List(err.getMessage)))
      }
    } yield ()
  }

  override def getByEmail(email: String): IO[UserError, User] = {
    userDao.getByEmail(email)
      .mapError(err => UserError.InvalidInput(List(err.getMessage)))
      .flatMap {
        case Some(user) => ZIO.succeed(user)
        case None => ZIO.fail(UserError.NotFound(email))
      }
  }

  override def update(user: User): IO[UserError, Unit] = {
    for {
      _ <- UserValidation.validate(user) // Validate input
      exists <- userDao.getByEmail(user.email)
        .mapError(err => UserError.InvalidInput(List(err.getMessage)))
      _ <- exists match {
        case Some(_) => userDao.update(user)
          .mapError(err => UserError.InvalidInput(List(err.getMessage)))
          .unit
        case None => ZIO.fail(UserError.NotFound(user.email))
      }
    } yield ()
  }

  override def delete(email: String): IO[UserError, Unit] = {
    userDao.getByEmail(email)
      .mapError(err => UserError.InvalidInput(List(err.getMessage)))
      .flatMap {
        case Some(_) => userDao.delete(email)
          .mapError(err => UserError.InvalidInput(List(err.getMessage)))
          .unit
        case None => ZIO.fail(UserError.NotFound(email))
      }
  }

  override def list(): IO[UserError, List[User]] = {
    userDao.list()
      .mapError(err => UserError.InvalidInput(List(err.getMessage)))
  }
}