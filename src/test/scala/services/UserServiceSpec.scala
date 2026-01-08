package services

import dao.{TestUserDao, UserDao}
import models.{User, UserError}
import zio._
import zio.test._
import zio.test.Assertion._

object UserServiceSpec extends ZIOSpecDefault {
  
  val testUser = User(
    name = "John",
    surName = "Doe",
    email = "john.doe@example.com"
  )
  
  def spec = suite("UserService")(
    test("should create user successfully") {
      for {
        service <- ZIO.service[UserService]
        _       <- service.create(testUser)
        result  <- service.getByEmail(testUser.email)
      } yield assertTrue(result == testUser)
    },
    
    test("should fail to create duplicate user") {
      for {
        service <- ZIO.service[UserService]
        _       <- service.create(testUser)
        result  <- service.create(testUser).flip
      } yield assertTrue(
        result.isInstanceOf[UserError.AlreadyExists]
      )
    },
    
    test("should get user by email") {
      for {
        service <- ZIO.service[UserService]
        _       <- service.create(testUser)
        result  <- service.getByEmail(testUser.email)
      } yield assertTrue(result == testUser)
    },
    
    test("should fail to get non-existent user") {
      for {
        service <- ZIO.service[UserService]
        result  <- service.getByEmail("nonexistent@example.com").flip
      } yield assertTrue(
        result.isInstanceOf[UserError.NotFound]
      )
    },
    
    test("should update existing user") {
      val updatedUser = testUser.copy(name = "Jane")
      
      for {
        service <- ZIO.service[UserService]
        _       <- service.create(testUser)
        _       <- service.update(updatedUser)
        result  <- service.getByEmail(testUser.email)
      } yield assertTrue(result.name == "Jane")
    },
    
    test("should fail to update non-existent user") {
      for {
        service <- ZIO.service[UserService]
        result  <- service.update(testUser).flip
      } yield assertTrue(
        result.isInstanceOf[UserError.NotFound]
      )
    },
    
    test("should delete existing user") {
      for {
        service <- ZIO.service[UserService]
        _       <- service.create(testUser)
        _       <- service.delete(testUser.email)
        result  <- service.getByEmail(testUser.email).flip
      } yield assertTrue(
        result.isInstanceOf[UserError.NotFound]
      )
    },
    
    test("should fail to delete non-existent user") {
      for {
        service <- ZIO.service[UserService]
        result  <- service.delete("nonexistent@example.com").flip
      } yield assertTrue(
        result.isInstanceOf[UserError.NotFound]
      )
    },
    
    test("should list all users") {
      val user2 = testUser.copy(email = "jane@example.com", name = "Jane")
      
      for {
        service <- ZIO.service[UserService]
        _       <- service.create(testUser)
        _       <- service.create(user2)
        result  <- service.list()
      } yield assertTrue(
        result.size == 2 &&
        result.contains(testUser) &&
        result.contains(user2)
      )
    }
  ).provide(
    TestUserDao.layer,
    UserService.live
  )
}

