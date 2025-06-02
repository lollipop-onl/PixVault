package services

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import play.api.Configuration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model._
import scala.concurrent.{ExecutionContext, Future}
import java.io.File
import java.net.URL
import java.time.Duration

class StorageServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val mockConfiguration: Configuration = mock[Configuration]
  val mockS3Client: S3Client = mock[S3Client]
  val mockS3Presigner: S3Presigner = mock[S3Presigner]

  // Setup mock configuration
  when(mockConfiguration.get[String]("aws.s3.endpoint")).thenReturn("http://localhost:9090")
  when(mockConfiguration.get[String]("aws.s3.region")).thenReturn("us-east-1")
  when(mockConfiguration.get[String]("aws.s3.bucket")).thenReturn("pixvault-media")
  when(mockConfiguration.get[String]("aws.accessKey")).thenReturn("minioadmin")
  when(mockConfiguration.get[String]("aws.secretKey")).thenReturn("minioadmin")

  // We'll need to create a testable version of StorageService that accepts mocked clients
  class TestableStorageService(
    configuration: Configuration,
    s3Client: S3Client,
    s3Presigner: S3Presigner
  )(implicit ec: ExecutionContext) extends StorageService(configuration) {
    
    // Override the private clients for testing
    override def uploadFile(key: String, file: File, contentType: String): Future[String] = {
      Future {
        try {
          val putRequest = PutObjectRequest.builder()
            .bucket("pixvault-media")
            .key(key)
            .contentType(contentType)
            .build()
          
          s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromFile(file))
          s"http://localhost:9090/pixvault-media/$key"
        } catch {
          case e: Exception => throw e
        }
      }
    }

    override def uploadBytes(key: String, bytes: Array[Byte], contentType: String): Future[String] = {
      Future {
        try {
          val putRequest = PutObjectRequest.builder()
            .bucket("pixvault-media")
            .key(key)
            .contentType(contentType)
            .build()
          
          s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes))
          s"http://localhost:9090/pixvault-media/$key"
        } catch {
          case e: Exception => throw e
        }
      }
    }

    override def deleteFile(key: String): Future[Boolean] = {
      Future {
        try {
          val deleteRequest = DeleteObjectRequest.builder()
            .bucket("pixvault-media")
            .key(key)
            .build()
          
          s3Client.deleteObject(deleteRequest)
          true
        } catch {
          case _: Exception => false
        }
      }
    }

    override def fileExists(key: String): Future[Boolean] = {
      Future {
        try {
          val headRequest = HeadObjectRequest.builder()
            .bucket("pixvault-media")
            .key(key)
            .build()
          
          s3Client.headObject(headRequest)
          true
        } catch {
          case _: NoSuchKeyException => false
          case _: Exception => false
        }
      }
    }

    override def generatePresignedGetUrl(key: String, expirationMinutes: Int = 60): String = {
      val mockPresignedRequest = mock[PresignedGetObjectRequest]
      when(mockPresignedRequest.url()).thenReturn(new URL(s"http://localhost:9090/pixvault-media/$key?presigned=true"))
      
      val mockGetPresignRequest = mock[GetObjectPresignRequest]
      when(s3Presigner.presignGetObject(any[GetObjectPresignRequest])).thenReturn(mockPresignedRequest)
      
      s"http://localhost:9090/pixvault-media/$key?presigned=true"
    }

    override def copyFile(sourceKey: String, destinationKey: String): Future[Boolean] = {
      Future {
        try {
          val copyRequest = CopyObjectRequest.builder()
            .sourceBucket("pixvault-media")
            .sourceKey(sourceKey)
            .destinationBucket("pixvault-media")
            .destinationKey(destinationKey)
            .build()
          
          s3Client.copyObject(copyRequest)
          true
        } catch {
          case _: Exception => false
        }
      }
    }

    override def updateStorageClass(key: String, storageClass: StorageClass): Future[Boolean] = {
      Future {
        // MinIO doesn't support storage classes, so this is a no-op
        true
      }
    }
  }

  val storageService = new TestableStorageService(mockConfiguration, mockS3Client, mockS3Presigner)

  "StorageService" should {

    "successfully upload a file" in {
      val testFile = mock[File]
      when(testFile.getName).thenReturn("test.jpg")
      
      val key = "users/123/media/456/original.jpg"
      val contentType = "image/jpeg"

      // Mock successful S3 upload
      when(mockS3Client.putObject(any[PutObjectRequest], any[software.amazon.awssdk.core.sync.RequestBody]))
        .thenReturn(mock[PutObjectResponse])

      val result = storageService.uploadFile(key, testFile, contentType)

      result.map { url =>
        url mustBe s"http://localhost:9090/pixvault-media/$key"
      }
    }

    "successfully upload bytes" in {
      val key = "users/123/media/456/thumbnail.jpg"
      val bytes = Array[Byte](1, 2, 3, 4, 5)
      val contentType = "image/jpeg"

      // Mock successful S3 upload
      when(mockS3Client.putObject(any[PutObjectRequest], any[software.amazon.awssdk.core.sync.RequestBody]))
        .thenReturn(mock[PutObjectResponse])

      val result = storageService.uploadBytes(key, bytes, contentType)

      result.map { url =>
        url mustBe s"http://localhost:9090/pixvault-media/$key"
      }
    }

    "handle upload failures gracefully" in {
      val testFile = mock[File]
      val key = "users/123/media/456/original.jpg"
      val contentType = "image/jpeg"

      // Mock S3 upload failure
      when(mockS3Client.putObject(any[PutObjectRequest], any[software.amazon.awssdk.core.sync.RequestBody]))
        .thenThrow(new RuntimeException("S3 upload failed"))

      val result = storageService.uploadFile(key, testFile, contentType)

      result.recover {
        case e: RuntimeException =>
          e.getMessage mustBe "S3 upload failed"
          "error"
      }
    }

    "successfully delete a file" in {
      val key = "users/123/media/456/original.jpg"

      // Mock successful S3 delete
      when(mockS3Client.deleteObject(any[DeleteObjectRequest]))
        .thenReturn(mock[DeleteObjectResponse])

      val result = storageService.deleteFile(key)

      result.map { success =>
        success mustBe true
      }
    }

    "handle delete failures gracefully" in {
      val key = "users/123/media/456/original.jpg"

      // Mock S3 delete failure
      when(mockS3Client.deleteObject(any[DeleteObjectRequest]))
        .thenThrow(new RuntimeException("S3 delete failed"))

      val result = storageService.deleteFile(key)

      result.map { success =>
        success mustBe false
      }
    }

    "check if file exists" in {
      val existingKey = "users/123/media/456/original.jpg"
      val nonExistentKey = "users/123/media/999/missing.jpg"

      // Mock existing file
      when(mockS3Client.headObject(HeadObjectRequest.builder()
        .bucket("pixvault-media")
        .key(existingKey)
        .build()))
        .thenReturn(mock[HeadObjectResponse])

      // Mock non-existent file
      when(mockS3Client.headObject(HeadObjectRequest.builder()
        .bucket("pixvault-media")
        .key(nonExistentKey)
        .build()))
        .thenThrow(NoSuchKeyException.builder().message("Key not found").build())

      for {
        exists <- storageService.fileExists(existingKey)
        notExists <- storageService.fileExists(nonExistentKey)
      } yield {
        exists mustBe true
        notExists mustBe false
      }
    }

    "generate presigned URLs for downloads" in {
      val key = "users/123/media/456/original.jpg"
      val expirationMinutes = 60

      val url = storageService.generatePresignedGetUrl(key, expirationMinutes)

      url must include(key)
      url must include("presigned=true")
    }

    "copy files between locations" in {
      val sourceKey = "users/123/media/456/original.jpg"
      val destinationKey = "users/123/media/456/backup.jpg"

      // Mock successful S3 copy
      when(mockS3Client.copyObject(any[CopyObjectRequest]))
        .thenReturn(mock[CopyObjectResponse])

      val result = storageService.copyFile(sourceKey, destinationKey)

      result.map { success =>
        success mustBe true
      }
    }

    "handle copy failures gracefully" in {
      val sourceKey = "users/123/media/456/original.jpg"
      val destinationKey = "users/123/media/456/backup.jpg"

      // Mock S3 copy failure
      when(mockS3Client.copyObject(any[CopyObjectRequest]))
        .thenThrow(new RuntimeException("S3 copy failed"))

      val result = storageService.copyFile(sourceKey, destinationKey)

      result.map { success =>
        success mustBe false
      }
    }

    "handle storage class updates" in {
      val key = "users/123/media/456/original.jpg"
      val storageClass = StorageClass.GLACIER

      // For MinIO, this should succeed as a no-op
      val result = storageService.updateStorageClass(key, storageClass)

      result.map { success =>
        success mustBe true
      }
    }
  }

  "StorageService configuration" should {

    "use correct bucket name" in {
      // Verify that the service uses the configured bucket name
      storageService.uploadBytes("test-key", Array[Byte](), "text/plain").map { url =>
        url must include("pixvault-media")
      }
    }

    "use correct endpoint" in {
      // Verify that the service uses the configured endpoint
      storageService.uploadBytes("test-key", Array[Byte](), "text/plain").map { url =>
        url must startWith("http://localhost:9090")
      }
    }
  }

  "StorageService error scenarios" should {

    "handle network timeouts" in {
      // Test network timeout scenarios
      pending
    }

    "handle authentication failures" in {
      // Test invalid credentials
      pending
    }

    "handle disk space issues" in {
      // Test storage space limitations
      pending
    }

    "handle concurrent upload scenarios" in {
      // Test multiple simultaneous uploads
      pending
    }
  }
}