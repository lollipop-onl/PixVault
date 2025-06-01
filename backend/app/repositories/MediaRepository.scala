package repositories

import models.MediaItem
import java.util.UUID
import scala.concurrent.Future

trait MediaRepository {
  def create(mediaItem: MediaItem): Future[MediaItem]
  def findById(id: UUID): Future[Option[MediaItem]]
  def findByUserId(userId: UUID, offset: Int = 0, limit: Int = 50): Future[Seq[MediaItem]]
  def findByHash(fileHash: String): Future[Option[MediaItem]]
  def update(mediaItem: MediaItem): Future[Option[MediaItem]]
  def delete(id: UUID): Future[Boolean]
  def addTags(mediaId: UUID, tags: Seq[String]): Future[Unit]
  def removeTags(mediaId: UUID, tags: Seq[String]): Future[Unit]
  def updateStorageStatus(id: UUID, status: String, storageClass: Option[String] = None): Future[Boolean]
}