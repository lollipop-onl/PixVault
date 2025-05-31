package models

import play.api.libs.json._

case class AuthResponse(
  accessToken: String,
  refreshToken: String,
  expiresIn: Int,
  user: User
)

object AuthResponse {
  implicit val authResponseFormat: OFormat[AuthResponse] = Json.format[AuthResponse]
}