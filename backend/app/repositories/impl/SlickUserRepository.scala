package repositories.impl

import javax.inject.{Inject, Singleton}
import repositories.{UserRepository, UserWithPassword}
import models.{User, tables}
import models.tables.{UserTable, UserRow}
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{Future, ExecutionContext}
import java.util.UUID
import java.time.Instant
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

@Singleton
class SlickUserRepository @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends UserRepository with HasDatabaseConfigProvider[JdbcProfile] {

  private val users = UserTable.users

  override def findByEmail(email: String): Future[Option[User]] = {
    db.run(users.filter(_.email === email).filter(_.isActive === true).result.headOption).map { userRowOpt =>
      userRowOpt.map(rowToUser)
    }
  }
  
  override def findByEmailWithPassword(email: String): Future[Option[UserWithPassword]] = {
    db.run(users.filter(_.email === email).filter(_.isActive === true).result.headOption).map { userRowOpt =>
      userRowOpt.map { row =>
        UserWithPassword(rowToUser(row), row.passwordHash)
      }
    }
  }

  override def findById(id: UUID): Future[Option[User]] = {
    db.run(users.filter(_.id === id).filter(_.isActive === true).result.headOption).map { userRowOpt =>
      userRowOpt.map(rowToUser)
    }
  }

  override def create(email: String, passwordHash: String, name: String): Future[User] = {
    val now = Instant.now()
    val userId = UUID.randomUUID()
    val userRow = UserRow(
      id = userId,
      email = email,
      passwordHash = passwordHash,
      name = name,
      createdAt = now,
      updatedAt = now,
      storageQuota = 107374182400L, // 100GB
      storageUsed = 0L,
      isActive = true
    )

    db.run(users += userRow).map { _ =>
      rowToUser(userRow)
    }
  }

  override def update(user: User): Future[Option[User]] = {
    val query = for {
      u <- users if u.id === UUID.fromString(user.id) && u.isActive === true
    } yield (u.email, u.name, u.storageQuota, u.updatedAt)

    db.run(query.update((user.email, user.name, user.storageQuota, Instant.now()))).flatMap { rowsUpdated =>
      if (rowsUpdated > 0) {
        findById(UUID.fromString(user.id))
      } else {
        Future.successful(None)
      }
    }
  }

  override def delete(id: UUID): Future[Boolean] = {
    val query = for {
      u <- users if u.id === id
    } yield u.isActive

    db.run(query.update(false)).map(_ > 0)
  }

  override def updateStorageUsed(id: UUID, storageUsed: Long): Future[Boolean] = {
    val query = for {
      u <- users if u.id === id && u.isActive === true
    } yield (u.storageUsed, u.updatedAt)

    db.run(query.update((storageUsed, Instant.now()))).map(_ > 0)
  }

  private def rowToUser(row: UserRow): User = {
    User(
      id = row.id.toString,
      email = row.email,
      name = row.name,
      createdAt = row.createdAt,
      storageQuota = row.storageQuota,
      storageUsed = row.storageUsed
    )
  }
}