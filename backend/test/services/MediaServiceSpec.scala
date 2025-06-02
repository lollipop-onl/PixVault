package services

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import models._
import repositories.MediaRepository
import services.{MediaService, StorageService}
import scala.concurrent.{Future, ExecutionContext}
import java.util.UUID
import java.time.Instant
import java.io.File
import play.api.mvc.MultipartFormData
import play.api.libs.Files.TemporaryFile

class MediaServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val mockMediaRepository: MediaRepository = mock[MediaRepository]
  val mockStorageService: StorageService = mock[StorageService]
  
  val mediaService = new MediaService(mockMediaRepository, mockStorageService)

  def createTestMediaItem: MediaItem = MediaItem(
    id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
    userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678"),
    `type` = "photo",
    filename = "test.jpg",
    originalFilename = "sample.jpg",
    fileHash = "abc123def456",
    mimeType = "image/jpeg",
    size = 1024L,
    width = Some(800),
    height = Some(600),
    duration = None,
    description = Some("Test image"),
    tags = List("test", "sample"),
    location = None,
    metadata = None,
    storageClass = "STANDARD",
    storageStatus = "ACTIVE",
    thumbnailUrl = Some("http://localhost:9090/bucket/thumb.jpg"),
    previewUrl = Some("http://localhost:9090/bucket/preview.jpg"),
    originalUrl = Some("http://localhost:9090/bucket/original.jpg"),
    capturedAt = None,
    uploadedAt = Instant.now(),
    archivedAt = None,
    lastAccessedAt = Instant.now()
  )

  def createTestFile: MultipartFormData.FilePart[TemporaryFile] = {
    val tempFile = TemporaryFile(play.api.libs.Files.SingletonTemporaryFileCreator, "test", ".jpg")
    MultipartFormData.FilePart(
      key = "file",
      filename = "sample.jpg",
      contentType = Some("image/jpeg"),
      ref = tempFile
    )
  }

  "MediaService" should {

    "successfully upload a new media file" in {
      val userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")
      val file = createTestFile
      val description = Some("Test upload")
      val tags = List("test", "upload")
      val testMedia = createTestMediaItem

      // Mock repository behavior - no existing file with same hash
      when(mockMediaRepository.findByHash(any[String]))
        .thenReturn(Future.successful(None))
      
      // Mock storage service behavior
      when(mockStorageService.uploadFile(any[String], any[File], any[String]))
        .thenReturn(Future.successful("http://localhost:9090/bucket/original.jpg"))
      when(mockStorageService.uploadBytes(any[String], any[Array[Byte]], any[String]))
        .thenReturn(Future.successful("http://localhost:9090/bucket/thumb.jpg"))

      // Mock repository create
      when(mockMediaRepository.create(any[MediaItem]))
        .thenReturn(Future.successful(testMedia))

      val result = mediaService.uploadMedia(userId, file, description, tags)

      result.map { uploadResult =>
        uploadResult mustBe a[Right[?, ?]]
        uploadResult.map { media =>
          media.userId mustBe userId
          media.originalFilename mustBe "sample.jpg"
          media.mimeType mustBe "image/jpeg"
          media.description mustBe description
          media.tags mustBe tags
        }
      }
    }

    "return existing media when duplicate file is uploaded" in {
      val userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")
      val file = createTestFile
      val description = Some("Duplicate upload")
      val tags = List("duplicate")
      val existingMedia = createTestMediaItem

      // Mock repository behavior - existing file found
      when(mockMediaRepository.findByHash(any[String]))
        .thenReturn(Future.successful(Some(existingMedia)))

      val result = mediaService.uploadMedia(userId, file, description, tags)

      result.map { uploadResult =>
        uploadResult mustBe a[Right[?, ?]]
        uploadResult.map { media =>
          media.id mustBe existingMedia.id
          media.fileHash mustBe existingMedia.fileHash
        }
      }

      // Verify that storage operations are not called for duplicates
      verify(mockStorageService, never()).uploadFile(any[String], any[File], any[String])
      verify(mockMediaRepository, never()).create(any[MediaItem])
    }

    "handle storage service failures gracefully" in {
      val userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")
      val file = createTestFile
      val description = Some("Test upload")
      val tags = List("test")

      // Mock repository behavior - no existing file
      when(mockMediaRepository.findByHash(any[String]))
        .thenReturn(Future.successful(None))
      
      // Mock storage service failure
      when(mockStorageService.uploadFile(any[String], any[File], any[String]))
        .thenReturn(Future.failed(new RuntimeException("S3 upload failed")))

      val result = mediaService.uploadMedia(userId, file, description, tags)

      result.map { uploadResult =>
        uploadResult mustBe a[Left[?, ?]]
        uploadResult.left.map { error =>
          error must include("Failed to upload media")
        }
      }
    }

    "reject files that are too large" in {
      val userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")
      val file = createTestFile
      val description = None
      val tags = List.empty

      // Create a mock file that reports large size
      val largeFile = mock[File]
      when(largeFile.length()).thenReturn(55L * 1024 * 1024 * 1024) // 55GB

      // We would need to modify the MediaService to check file size
      // For now, this test documents the expected behavior
      pending
    }

    "extract EXIF metadata from image files" in {
      // This test would verify EXIF metadata extraction
      // Requires test image files with known EXIF data
      pending
    }

    "generate thumbnails for uploaded images" in {
      // This test would verify thumbnail generation
      // Requires testing the thumbnail generation pipeline
      pending
    }

    "calculate SHA-256 hash correctly" in {
      // This test would verify file hash calculation
      // Could use a known test file with expected hash
      pending
    }
  }

  "MediaService duplicate detection" should {

    "identify identical files by hash" in {
      val userId1 = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")
      val userId2 = UUID.fromString("8f3a5b9e-2345-6789-01bc-def123456789")
      val file = createTestFile
      val existingMedia = createTestMediaItem.copy(userId = userId1)

      // Mock repository to return existing media from different user
      when(mockMediaRepository.findByHash(any[String]))
        .thenReturn(Future.successful(Some(existingMedia)))

      val result = mediaService.uploadMedia(userId2, file, None, List.empty)

      result.map { uploadResult =>
        uploadResult mustBe a[Right[?, ?]]
        uploadResult.map { media =>
          // Should return existing media even if uploaded by different user
          media.id mustBe existingMedia.id
          media.userId mustBe userId1 // Original owner
        }
      }
    }

    "handle hash collisions gracefully" in {
      // This would test the unlikely case of SHA-256 hash collisions
      // In practice, this is extremely rare but should be handled
      pending
    }
  }

  "MediaService error handling" should {

    "handle repository failures" in {
      val userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")
      val file = createTestFile

      // Mock repository failure
      when(mockMediaRepository.findByHash(any[String]))
        .thenReturn(Future.failed(new RuntimeException("Database connection failed")))

      val result = mediaService.uploadMedia(userId, file, None, List.empty)

      result.map { uploadResult =>
        uploadResult mustBe a[Left[?, ?]]
      }
    }

    "handle invalid file formats" in {
      // Test with unsupported file types
      pending
    }

    "handle corrupted files" in {
      // Test with files that can't be processed
      pending
    }
  }
}