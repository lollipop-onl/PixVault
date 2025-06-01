package models.tables

import models._
import models.MediaItem._
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ProvenShape, Tag}
import java.time.Instant
import java.util.UUID
import slick.jdbc.JdbcType
import play.api.libs.json._
import slick.jdbc.PostgresProfile

// Row case class to work around Scala's 22-element tuple limitation
case class MediaItemRow(
  id: UUID,
  userId: UUID,
  `type`: String,
  filename: String,
  originalFilename: String,
  fileHash: String,
  mimeType: String,
  size: Long,
  width: Option[Int],
  height: Option[Int],
  duration: Option[Double],
  description: Option[String],
  location: Option[Location],
  metadata: Option[MediaMetadata],
  storageClass: String,
  storageStatus: String,
  thumbnailUrl: Option[String],
  previewUrl: Option[String],
  originalUrl: Option[String],
  capturedAt: Option[Instant],
  uploadedAt: Instant,
  archivedAt: Option[Instant]
) {
  def toMediaItem: MediaItem = MediaItem(
    id, userId, `type`, filename, originalFilename, fileHash, mimeType, size,
    width, height, duration, description, List.empty[String], location, metadata,
    storageClass, storageStatus, thumbnailUrl, previewUrl, originalUrl, capturedAt,
    uploadedAt, archivedAt, uploadedAt // Use uploadedAt as lastAccessedAt temporarily
  )
}

object MediaItemRow {
  def fromMediaItem(m: MediaItem): MediaItemRow = MediaItemRow(
    m.id, m.userId, m.`type`, m.filename, m.originalFilename, m.fileHash, m.mimeType,
    m.size, m.width, m.height, m.duration, m.description, m.location, m.metadata,
    m.storageClass, m.storageStatus, m.thumbnailUrl, m.previewUrl, m.originalUrl,
    m.capturedAt, m.uploadedAt, m.archivedAt
  )
}

class MediaItemTable(tag: Tag) extends Table[MediaItemRow](tag, "media_items") {
  
  // JSON column mapping
  implicit val jsValueColumnType: JdbcType[JsValue] = 
    MappedColumnType.base[JsValue, String](
      json => Json.stringify(json),
      str => Json.parse(str)
    )
    
  implicit val locationColumnType: JdbcType[Option[Location]] = 
    MappedColumnType.base[Option[Location], String](
      opt => opt.map(loc => Json.stringify(Json.toJson(loc))).getOrElse("null"),
      str => if (str == "null") None else Json.parse(str).asOpt[Location]
    )
  
  implicit val metadataColumnType: JdbcType[Option[MediaMetadata]] = 
    MappedColumnType.base[Option[MediaMetadata], String](
      opt => opt.map(meta => Json.stringify(Json.toJson(meta))).getOrElse("null"),
      str => if (str == "null") None else Json.parse(str).asOpt[MediaMetadata]
    )

  // String list column mapping for tags (will be handled separately via media_tags table)
  implicit val tagsColumnType: JdbcType[List[String]] = 
    MappedColumnType.base[List[String], String](
      tags => tags.mkString(","),
      str => if (str.isEmpty) List.empty else str.split(",").toList
    )

  def id = column[UUID]("id", O.PrimaryKey)
  def userId = column[UUID]("user_id")
  def `type` = column[String]("type")
  def filename = column[String]("filename")
  def originalFilename = column[String]("original_filename")
  def fileHash = column[String]("file_hash")
  def mimeType = column[String]("mime_type")
  def size = column[Long]("size")
  def width = column[Option[Int]]("width")
  def height = column[Option[Int]]("height")
  def duration = column[Option[Double]]("duration")
  def description = column[Option[String]]("description")
  def location = column[Option[Location]]("location")
  def metadata = column[Option[MediaMetadata]]("metadata")
  def storageClass = column[String]("storage_class")
  def storageStatus = column[String]("storage_status")
  def thumbnailUrl = column[Option[String]]("thumbnail_url")
  def previewUrl = column[Option[String]]("preview_url")
  def originalUrl = column[Option[String]]("original_url")
  def capturedAt = column[Option[Instant]]("captured_at")
  def uploadedAt = column[Instant]("uploaded_at")
  def archivedAt = column[Option[Instant]]("archived_at")
  // Temporarily removing lastAccessedAt to get under 22-field limit
  // def lastAccessedAt = column[Instant]("last_accessed_at")

  def * = (
    id, userId, `type`, filename, originalFilename, fileHash, mimeType, size,
    width, height, duration, description, location, metadata, storageClass,
    storageStatus, thumbnailUrl, previewUrl, originalUrl, capturedAt, uploadedAt,
    archivedAt
  ).mapTo[MediaItemRow]
}

class MediaTagTable(tag: Tag) extends Table[(UUID, UUID, String)](tag, "media_tags") {
  def id = column[UUID]("id", O.PrimaryKey)
  def mediaId = column[UUID]("media_id")
  def tagValue = column[String]("tag")
  
  def * = (id, mediaId, tagValue)
  
  def media = foreignKey("fk_media_tags_media", mediaId, TableQuery[MediaItemTable])(_.id, onDelete=ForeignKeyAction.Cascade)
}

object MediaItemTable {
  val query = TableQuery[MediaItemTable]
}

object MediaTagTable {
  val query = TableQuery[MediaTagTable]
}