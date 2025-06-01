package models

import play.api.libs.json._
import java.time.Instant

case class User(
  id: String,
  email: String,
  name: String,
  createdAt: Instant,
  storageQuota: Long,
  storageUsed: Long
)

object User {
  implicit val instantReads: Reads[Instant] = Reads.of[String].map(Instant.parse)
  implicit val instantWrites: Writes[Instant] = Writes.of[String].contramap(_.toString)
  implicit val userFormat: OFormat[User] = Json.format[User]
}