package services

import dao.UserDao
import models.User
import zio.{Task, URLayer, ZIO, ZLayer}


trait UserService {
  def create(user: User): Task[Unit]

  def getByEmail(email: String): Task[Option[User]]

  def update(user: User): Task[Boolean]

  def delete(email: String): Task[Boolean]

  def list(): Task[List[User]]
}

object UserService {
  def live: URLayer[UserDao, UserService] =
    ZLayer.fromFunction(UserServiceImpl(_))
}

final case class UserServiceImpl(userDao: UserDao) extends UserService {

  override def create(user: User): Task[Unit] = {
    for {
      existing <- userDao.getByEmail(user.email)
      _ <- existing match {
        case Some(_) => ZIO.fail(new Exception("User already exist in DB"))
        case None => userDao.create(user)
      }

    } yield ()

  }

  override def getByEmail(email: String): Task[Option[User]] = 
    userDao.getByEmail(email)

  override def update(user: User): Task[Boolean] = 
    userDao.update(user)

  override def delete(email: String): Task[Boolean] = userDao.delete(email)

  override def list(): Task[List[User]] = userDao.list()
}