package models

import zio.test._
import zio.test.Assertion._

object UserValidationSpec extends ZIOSpecDefault {
  
  def spec = suite("UserValidation")(
    test("should reject invalid email format") {
      val user = User(
        name = "John",
        surName = "Doe",
        email = "invalid-email"
      )
      
      for {
        result <- UserValidation.validate(user).flip
      } yield assertTrue(
        result.message.contains("Invalid email format")
      )
    },
    
    test("should reject empty name") {
      val user = User(
        name = "",
        surName = "Doe",
        email = "john@example.com"
      )
      
      for {
        result <- UserValidation.validate(user).flip
      } yield assertTrue(
        result.message.contains("Name is required")
      )
    },
    
    test("should reject empty surname") {
      val user = User(
        name = "John",
        surName = "",
        email = "john@example.com"
      )
      
      for {
        result <- UserValidation.validate(user).flip
      } yield assertTrue(
        result.message.contains("Surname is required")
      )
    },
    
    test("should accept valid user") {
      val user = User(
        name = "John",
        surName = "Doe",
        email = "john.doe@example.com"
      )
      
      for {
        result <- UserValidation.validate(user)
      } yield assertTrue(result == user)
    }
  )
}

