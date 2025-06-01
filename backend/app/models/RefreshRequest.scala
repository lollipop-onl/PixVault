package models

import play.api.libs.json._

case class RefreshRequest(
  refreshToken: String
)

object RefreshRequest {
  implicit val refreshRequestFormat: OFormat[RefreshRequest] = Json.format[RefreshRequest]
}