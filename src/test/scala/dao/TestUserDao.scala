package dao

import models.User
import zio._

object TestUserDao {
  
  // In-memory storage using Ref
  def make: UIO[UserDao] = 
    Ref.make(Map.empty[String, User]).map(new TestUserDaoImpl(_))
  
  // Test layer
  val layer: ULayer[UserDao] = 
    ZLayer.fromZIO(make)
}

class TestUserDaoImpl(ref: Ref[Map[String, User]]) extends UserDao {
  
  override def create(user: User): Task[Unit] =
    ref.update(_ + (user.email -> user)).unit
  
  override def getByEmail(email: String): Task[Option[User]] =
    ref.get.map(_.get(email))
  
  override def update(user: User): Task[Boolean] =
    ref.modify { map =>
      if (map.contains(user.email))
        (true, map.updated(user.email, user))
      else
        (false, map)
    }
  
  override def delete(email: String): Task[Boolean] =
    ref.modify { map =>
      if (map.contains(email))
        (true, map - email)
      else
        (false, map)
    }
  
  override def list(): Task[List[User]] =
    ref.get.map(_.values.toList)
}

