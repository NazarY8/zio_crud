package models

import zio.IO
import zio.prelude.Validation

object UserValidation {
  // Email regex pattern
  private val emailRegex =
    """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r

  // Validation rules
  private def validateEmail(email: String): Validation[String, String] = {
    if (email.trim.isEmpty)
      Validation.fail("Email is required")
    else if (!emailRegex.matches(email))
      Validation.fail(s"Invalid email format: '$email'")
    else
      Validation.succeed(email)
  }

  private def validateName(name: String): Validation[String, String] = {
    if (name.trim.isEmpty)
      Validation.fail("Name is required")
    else if (name.length < 2)
      Validation.fail("Name must be at least 2 characters")
    else
      Validation.succeed(name)
  }

  private def validateSurname(surname: String): Validation[String, String] = {
    if (surname.trim.isEmpty)
      Validation.fail("Surname is required")
    else if (surname.length < 2)
      Validation.fail("Surname must be at least 2 characters")
    else
      Validation.succeed(surname)
  }

  // Combine all validations
  def validate(user: User): IO[UserError, User] = {
    Validation.validateWith(
        validateName(user.name),
        validateSurname(user.surName),
        validateEmail(user.email)
      )(User.apply)
      .toZIOParallelErrors
      .mapError(errors => UserError.InvalidInput(errors.toList))
  }
}
