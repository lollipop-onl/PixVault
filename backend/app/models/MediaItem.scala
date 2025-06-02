package models

import play.api.libs.json._
import java.time.Instant
import java.util.UUID

case class MediaItem(
  id: UUID,
  userId: UUID,
  `type`: String, // "photo" or "video"
  filename: String,
  originalFilename: String,
  fileHash: String, // SHA-256 hash for duplicate detection
  mimeType: String,
  size: Long,
  width: Option[Int],
  height: Option[Int],
  duration: Option[Double], // seconds for video
  description: Option[String],
  tags: List[String],
  location: Option[Location],
  metadata: Option[MediaMetadata],
  storageClass: String, // "STANDARD", "STANDARD_IA", "GLACIER", "DEEP_ARCHIVE"
  storageStatus: String, // "ACTIVE", "ARCHIVING", "ARCHIVED", "RESTORING"
  thumbnailUrl: Option[String],
  previewUrl: Option[String],
  originalUrl: Option[String],
  capturedAt: Option[Instant],
  uploadedAt: Instant,
  archivedAt: Option[Instant],
  lastAccessedAt: Instant
)

case class Location(
  latitude: Double,
  longitude: Double,
  placeName: Option[String]
)

case class MediaMetadata(
  camera: Option[String],
  lens: Option[String],
  iso: Option[Int],
  aperture: Option[Double],
  shutterSpeed: Option[String],
  focalLength: Option[Double]
)

object MediaItem {
  implicit val uuidFormat: Format[UUID] = Format(
    Reads.uuidReads,
    Writes.of[String].contramap(_.toString)
  )
  
  implicit val instantReads: Reads[Instant] = Reads.of[String].map(Instant.parse)
  implicit val instantWrites: Writes[Instant] = Writes.of[String].contramap(_.toString)
  implicit val instantFormat: Format[Instant] = Format(instantReads, instantWrites)
  
  implicit val locationFormat: OFormat[Location] = Json.format[Location]
  implicit val mediaMetadataFormat: OFormat[MediaMetadata] = Json.format[MediaMetadata]
  implicit val mediaItemFormat: OFormat[MediaItem] = Json.format[MediaItem]
}

object MediaType {
  val Photo = "photo"
  val Video = "video"
  
  def isValid(value: String): Boolean = value == Photo || value == Video
}

object StorageClass {
  val Standard = "STANDARD"
  val StandardIA = "STANDARD_IA"
  val Glacier = "GLACIER"
  val DeepArchive = "DEEP_ARCHIVE"
  
  def isValid(value: String): Boolean = 
    Set(Standard, StandardIA, Glacier, DeepArchive).contains(value)
}

object StorageStatus {
  val Active = "ACTIVE"
  val Archiving = "ARCHIVING"
  val Archived = "ARCHIVED"
  val Restoring = "RESTORING"
  
  def isValid(value: String): Boolean = 
    Set(Active, Archiving, Archived, Restoring).contains(value)
}