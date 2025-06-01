package repositories.impl

import javax.inject._
import models._
import models.tables._
import repositories.MediaRepository
import slick.jdbc.PostgresProfile.api._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import java.util.UUID
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlickMediaRepository @Inject()(
    protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends MediaRepository with HasDatabaseConfigProvider[JdbcProfile] {

  private val mediaItems = MediaItemTable.query
  private val mediaTags = MediaTagTable.query

  override def create(mediaItem: MediaItem): Future[MediaItem] = {
    val action = for {
      // Insert media item
      _ <- mediaItems += mediaItem
      // Insert tags if any
      _ <- if (mediaItem.tags.nonEmpty) {
        mediaTags ++= mediaItem.tags.map(tag => (UUID.randomUUID(), mediaItem.id, tag))
      } else {
        DBIO.successful(())
      }
    } yield mediaItem

    db.run(action.transactionally)
  }

  override def findById(id: UUID): Future[Option[MediaItem]] = {
    val query = for {
      item <- mediaItems.filter(_.id === id).result.headOption
      tags <- mediaTags.filter(_.mediaId === id).map(_.tagValue).result
    } yield item.map(_.copy(tags = tags.toList))

    db.run(query)
  }

  override def findByUserId(userId: UUID, offset: Int = 0, limit: Int = 50): Future[Seq[MediaItem]] = {
    val query = for {
      items <- mediaItems
        .filter(_.userId === userId)
        .sortBy(_.uploadedAt.desc)
        .drop(offset)
        .take(limit)
        .result
      // Get tags for all items
      itemIds = items.map(_.id)
      tagsMap <- mediaTags
        .filter(_.mediaId inSet itemIds)
        .result
        .map(_.groupBy(_._2).view.mapValues(_.map(_._3).toList).toMap)
    } yield items.map { item =>
      item.copy(tags = tagsMap.getOrElse(item.id, List.empty))
    }

    db.run(query)
  }

  override def findByHash(fileHash: String): Future[Option[MediaItem]] = {
    val query = for {
      item <- mediaItems.filter(_.fileHash === fileHash).result.headOption
      tags <- item match {
        case Some(mediaItem) => mediaTags.filter(_.mediaId === mediaItem.id).map(_.tagValue).result
        case None => DBIO.successful(Seq.empty)
      }
    } yield item.map(_.copy(tags = tags.toList))

    db.run(query)
  }

  override def update(mediaItem: MediaItem): Future[Option[MediaItem]] = {
    val action = for {
      // Update media item
      updateCount <- mediaItems.filter(_.id === mediaItem.id).update(mediaItem)
      // Delete existing tags and insert new ones
      _ <- mediaTags.filter(_.mediaId === mediaItem.id).delete
      _ <- if (mediaItem.tags.nonEmpty) {
        mediaTags ++= mediaItem.tags.map(tag => (UUID.randomUUID(), mediaItem.id, tag))
      } else {
        DBIO.successful(())
      }
    } yield if (updateCount > 0) Some(mediaItem) else None

    db.run(action.transactionally)
  }

  override def delete(id: UUID): Future[Boolean] = {
    // Tags will be deleted automatically due to CASCADE
    db.run(mediaItems.filter(_.id === id).delete).map(_ > 0)
  }

  override def addTags(mediaId: UUID, tags: Seq[String]): Future[Unit] = {
    val action = mediaTags ++= tags.map(tag => (UUID.randomUUID(), mediaId, tag))
    db.run(action).map(_ => ())
  }

  override def removeTags(mediaId: UUID, tags: Seq[String]): Future[Unit] = {
    val action = mediaTags
      .filter(mt => mt.mediaId === mediaId && mt.tagValue.inSet(tags))
      .delete
    db.run(action).map(_ => ())
  }

  override def updateStorageStatus(id: UUID, status: String, storageClass: Option[String] = None): Future[Boolean] = {
    val updateQuery = storageClass match {
      case Some(sc) =>
        mediaItems
          .filter(_.id === id)
          .map(m => (m.storageStatus, m.storageClass))
          .update((status, sc))
      case None =>
        mediaItems
          .filter(_.id === id)
          .map(_.storageStatus)
          .update(status)
    }
    
    db.run(updateQuery).map(_ > 0)
  }
}