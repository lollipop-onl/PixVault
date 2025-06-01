package models

import play.api.libs.json._

case class LoginRequest(
  email: String,
  password: String
)

object LoginRequest {
  implicit val loginRequestFormat: OFormat[LoginRequest] = Json.format[LoginRequest]
}