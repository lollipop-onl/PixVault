package repositories

import models.User
import scala.concurrent.Future
import java.util.UUID

case class UserWithPassword(user: User, passwordHash: String)

trait UserRepository {
  def findByEmail(email: String): Future[Option[User]]
  def findByEmailWithPassword(email: String): Future[Option[UserWithPassword]]
  def findById(id: UUID): Future[Option[User]]
  def create(email: String, passwordHash: String, name: String): Future[User]
  def update(user: User): Future[Option[User]]
  def delete(id: UUID): Future[Boolean]
  def updateStorageUsed(id: UUID, storageUsed: Long): Future[Boolean]
}