package services

import javax.inject._
import models._
import repositories.MediaRepository
import play.api.Logging
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import java.util.UUID
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import java.security.MessageDigest
import java.io.{FileInputStream, InputStream}
import org.apache.commons.codec.binary.Hex
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif._
import com.drew.metadata.Directory
import scala.jdk.CollectionConverters._
import java.io.File
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.geometry.Positions
import java.io.ByteArrayOutputStream

@Singleton
class MediaService @Inject()(
  mediaRepository: MediaRepository,
  storageService: StorageService
)(implicit ec: ExecutionContext) extends Logging {
  
  private val ThumbnailSize = 200
  private val PreviewMaxSize = 1920
  
  def uploadMedia(
    userId: UUID,
    file: FilePart[TemporaryFile],
    description: Option[String],
    tags: List[String]
  ): Future[Either[String, MediaItem]] = {
    val tempFile = file.ref.path.toFile
    
    // Calculate file hash
    val fileHash = calculateFileHash(tempFile)
    
    // Check for duplicates
    mediaRepository.findByHash(fileHash).flatMap {
      case Some(existingMedia) =>
        logger.info(s"Duplicate file detected with hash: $fileHash")
        Future.successful(Right(existingMedia))
        
      case None =>
        // Process new file
        processAndUploadNewMedia(userId, file, fileHash, description, tags)
    }
  }
  
  private def processAndUploadNewMedia(
    userId: UUID,
    file: FilePart[TemporaryFile],
    fileHash: String,
    description: Option[String],
    tags: List[String]
  ): Future[Either[String, MediaItem]] = {
    val tempFile = file.ref.path.toFile
    val mediaId = UUID.randomUUID()
    val fileExtension = getFileExtension(file.filename)
    val mediaType = determineMediaType(file.contentType)
    
    // Extract metadata
    val metadata = extractImageMetadata(tempFile)
    val (width, height) = getImageDimensions(tempFile, metadata)
    
    // Generate filenames
    val originalKey = s"users/$userId/media/$mediaId/original.$fileExtension"
    val thumbnailKey = s"users/$userId/media/$mediaId/thumbnail.webp"
    val previewKey = s"users/$userId/media/$mediaId/preview.$fileExtension"
    
    // Upload files
    val uploadFuture = for {
      // Upload original
      originalUrl <- storageService.uploadFile(originalKey, tempFile, file.contentType.getOrElse("application/octet-stream"))
      
      // Generate and upload thumbnail
      thumbnailBytes <- Future(generateThumbnail(tempFile))
      thumbnailUrl <- storageService.uploadBytes(thumbnailKey, thumbnailBytes, "image/webp")
      
      // Generate and upload preview (if image)
      previewUrl <- if (mediaType == MediaType.Photo) {
        val previewBytes = generatePreview(tempFile)
        storageService.uploadBytes(previewKey, previewBytes, file.contentType.getOrElse("image/jpeg"))
      } else {
        Future.successful(originalUrl)
      }
      
      // Create media item
      mediaItem = MediaItem(
        id = mediaId,
        userId = userId,
        `type` = mediaType,
        filename = s"$mediaId.$fileExtension",
        originalFilename = file.filename,
        fileHash = fileHash,
        mimeType = file.contentType.getOrElse("application/octet-stream"),
        size = tempFile.length(),
        width = width,
        height = height,
        duration = None, // TODO: Extract video duration
        description = description,
        tags = tags,
        location = extractLocation(metadata),
        metadata = extractCameraMetadata(metadata),
        storageClass = StorageClass.Standard,
        storageStatus = StorageStatus.Active,
        thumbnailUrl = Some(thumbnailUrl),
        previewUrl = Some(previewUrl),
        originalUrl = Some(originalUrl),
        capturedAt = extractCaptureDate(metadata),
        uploadedAt = Instant.now(),
        archivedAt = None,
        lastAccessedAt = Instant.now()
      )
      
      // Save to database
      saved <- mediaRepository.create(mediaItem)
    } yield Right(saved)
    
    uploadFuture.recover {
      case e: Exception =>
        logger.error(s"Failed to upload media: ${e.getMessage}", e)
        Left(s"Failed to upload media: ${e.getMessage}")
    }
  }
  
  private def calculateFileHash(file: File): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = new Array[Byte](8192)
    val stream = new FileInputStream(file)
    
    try {
      var bytesRead = stream.read(buffer)
      while (bytesRead != -1) {
        digest.update(buffer, 0, bytesRead)
        bytesRead = stream.read(buffer)
      }
      Hex.encodeHexString(digest.digest())
    } finally {
      stream.close()
    }
  }
  
  private def getFileExtension(filename: String): String = {
    filename.lastIndexOf('.') match {
      case -1 => "bin"
      case i => filename.substring(i + 1).toLowerCase
    }
  }
  
  private def determineMediaType(contentType: Option[String]): String = {
    contentType match {
      case Some(ct) if ct.startsWith("image/") => MediaType.Photo
      case Some(ct) if ct.startsWith("video/") => MediaType.Video
      case _ => MediaType.Photo // Default to photo
    }
  }
  
  private def extractImageMetadata(file: File): Option[com.drew.metadata.Metadata] = {
    try {
      Some(ImageMetadataReader.readMetadata(file))
    } catch {
      case e: Exception =>
        logger.warn(s"Failed to extract metadata: ${e.getMessage}")
        None
    }
  }
  
  private def getImageDimensions(file: File, metadata: Option[com.drew.metadata.Metadata]): (Option[Int], Option[Int]) = {
    metadata.flatMap { meta =>
      val exifDir = meta.getFirstDirectoryOfType(classOf[ExifIFD0Directory])
      if (exifDir != null && exifDir.containsTag(ExifDirectoryBase.TAG_IMAGE_WIDTH)) {
        Some((
          Option(exifDir.getInteger(ExifDirectoryBase.TAG_IMAGE_WIDTH)),
          Option(exifDir.getInteger(ExifDirectoryBase.TAG_IMAGE_HEIGHT))
        ))
      } else {
        None
      }
    }.getOrElse((None, None))
  }
  
  private def extractLocation(metadata: Option[com.drew.metadata.Metadata]): Option[Location] = {
    metadata.flatMap { meta =>
      val gpsDir = meta.getFirstDirectoryOfType(classOf[GpsDirectory])
      if (gpsDir != null && gpsDir.getGeoLocation != null) {
        val geoLocation = gpsDir.getGeoLocation
        Some(Location(
          latitude = geoLocation.getLatitude,
          longitude = geoLocation.getLongitude,
          placeName = None
        ))
      } else {
        None
      }
    }
  }
  
  private def extractCameraMetadata(metadata: Option[com.drew.metadata.Metadata]): Option[MediaMetadata] = {
    metadata.flatMap { meta =>
      val exifDir = meta.getFirstDirectoryOfType(classOf[ExifIFD0Directory])
      val subDir = meta.getFirstDirectoryOfType(classOf[ExifSubIFDDirectory])
      
      if (exifDir != null || subDir != null) {
        Some(MediaMetadata(
          camera = Option(exifDir).flatMap(d => Option(d.getString(ExifDirectoryBase.TAG_MODEL))),
          lens = Option(exifDir).flatMap(d => Option(d.getString(ExifDirectoryBase.TAG_LENS_MODEL))),
          iso = Option(subDir).flatMap(d => Option(d.getInteger(ExifDirectoryBase.TAG_ISO_EQUIVALENT))),
          aperture = Option(subDir).flatMap(d => Option(d.getDouble(ExifDirectoryBase.TAG_APERTURE))),
          shutterSpeed = Option(subDir).flatMap(d => Option(d.getString(ExifDirectoryBase.TAG_SHUTTER_SPEED))),
          focalLength = Option(subDir).flatMap(d => Option(d.getDouble(ExifDirectoryBase.TAG_FOCAL_LENGTH)))
        ))
      } else {
        None
      }
    }
  }
  
  private def extractCaptureDate(metadata: Option[com.drew.metadata.Metadata]): Option[Instant] = {
    metadata.flatMap { meta =>
      val exifDir = meta.getFirstDirectoryOfType(classOf[ExifSubIFDDirectory])
      if (exifDir != null && exifDir.hasTagName(ExifDirectoryBase.TAG_DATETIME_ORIGINAL)) {
        Option(exifDir.getDate(ExifDirectoryBase.TAG_DATETIME_ORIGINAL)).map(_.toInstant)
      } else {
        None
      }
    }
  }
  
  private def generateThumbnail(file: File): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()
    
    Thumbnails.of(file)
      .size(ThumbnailSize, ThumbnailSize)
      .crop(Positions.CENTER)
      .outputFormat("webp")
      .outputQuality(0.8)
      .toOutputStream(outputStream)
    
    outputStream.toByteArray
  }
  
  private def generatePreview(file: File): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()
    
    Thumbnails.of(file)
      .size(PreviewMaxSize, PreviewMaxSize)
      .keepAspectRatio(true)
      .outputFormat("jpeg")
      .outputQuality(0.85)
      .toOutputStream(outputStream)
    
    outputStream.toByteArray
  }
}