package models

sealed trait UserError extends Exception {
  def message: String

  override def getMessage: String = message
}

object UserError {
  case class NotFound(email: String) extends UserError {
    val message = s"User with email '$email' not found"
  }

  case class AlreadyExists(email: String) extends UserError {
    val message = s"User with email '$email' already exists"
  }

  case class InvalidInput(errors: List[String]) extends UserError {
    val message = s"Invalid input: ${errors.mkString(", ")}"
  }
}