package models.tables

import slick.jdbc.PostgresProfile.api._
import java.time.Instant
import java.util.UUID

case class UserRow(
  id: UUID,
  email: String,
  passwordHash: String,
  name: String,
  createdAt: Instant,
  updatedAt: Instant,
  storageQuota: Long,
  storageUsed: Long,
  isActive: Boolean
)

class UserTable(tag: Tag) extends Table[UserRow](tag, "users") {
  def id = column[UUID]("id", O.PrimaryKey)
  def email = column[String]("email")
  def passwordHash = column[String]("password_hash")
  def name = column[String]("name")
  def createdAt = column[Instant]("created_at")
  def updatedAt = column[Instant]("updated_at")
  def storageQuota = column[Long]("storage_quota")
  def storageUsed = column[Long]("storage_used")
  def isActive = column[Boolean]("is_active")

  def * = (id, email, passwordHash, name, createdAt, updatedAt, storageQuota, storageUsed, isActive).mapTo[UserRow]
  
  def emailIndex = index("idx_users_email", email, unique = true)
}

object UserTable {
  val users = TableQuery[UserTable]
}