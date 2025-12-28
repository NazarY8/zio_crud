package models

import zio.json.{DeriveJsonCodec, JsonCodec}
import sttp.tapir.{Schema => tapirSchema}
import zio.dynamodb.ProjectionExpression
import zio.schema.{DeriveSchema, Schema => ZioSchema}

case class User(name: String, surName: String, email: String)

object User {
  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]

  //needed for openAPI (swagger)
  implicit val schema: tapirSchema[User] = tapirSchema.derived

  // needed for zio-dynamoDB automatic serialization
  implicit val zioSchema: ZioSchema.CaseClass3[String, String, String, User] = DeriveSchema.gen[User]
  
  val (name, surName, email) = ProjectionExpression.accessors[User]
}