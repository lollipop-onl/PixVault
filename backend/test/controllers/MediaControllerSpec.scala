package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application
import models._
import repositories.MediaRepository
import services.{MediaService, JwtService}
import scala.concurrent.{Future, ExecutionContext}
import java.time.Instant
import java.util.UUID
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{MultipartFormData, Result}
import play.api.libs.Files.TemporaryFile
import java.io.File
import akka.util.ByteString
import play.api.http.Writeable

class MediaControllerSpec extends PlaySpec with GuiceOneAppPerTest with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def createTestMediaItem: MediaItem = MediaItem(
    id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
    userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678"),
    `type` = "photo",
    filename = "550e8400-e29b-41d4-a716-446655440000.jpg",
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

  val validJwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NDg3OTk2NTEsImlhdCI6MTc0ODc5NjA1MSwidXNlcklkIjoiN2YyYTRiOGUtMTIzNC01Njc4LTkwYWItY2RlZjEyMzQ1Njc4IiwiZW1haWwiOiJ0YW5ha2EueXVraUBleGFtcGxlLmNvbSIsInR5cGUiOiJhY2Nlc3MifQ.FK8R55tC8PFVjIuUbroi75wnaocIsTNJ5fM3S55IKZM"

  override def fakeApplication(): Application = {
    val mockMediaRepository = mock[MediaRepository]
    val mockMediaService = mock[MediaService]
    val mockJwtService = mock[JwtService]

    new GuiceApplicationBuilder()
      .overrides(
        bind[MediaRepository].toInstance(mockMediaRepository),
        bind[MediaService].toInstance(mockMediaService),
        bind[JwtService].toInstance(mockJwtService)
      )
      .build()
  }

  "MediaController upload" should {

    "return 201 Created for successful file upload" in {
      val mockMediaService = app.injector.instanceOf[MediaService]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val testMedia = createTestMediaItem

      // Mock JWT service
      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("7f2a4b8e-1234-5678-90ab-cdef12345678"))

      // Mock successful media upload
      when(mockMediaService.uploadMedia(any[UUID], any[MultipartFormData.FilePart[TemporaryFile]], any[Option[String]], any[List[String]]))
        .thenReturn(Future.successful(Right(testMedia)))

      // Create test file content
      val fileContent = "fake image content"
      val tempFile = TemporaryFile(play.api.libs.Files.SingletonTemporaryFileCreator, "test", ".jpg")
      val filePart = MultipartFormData.FilePart(
        key = "file",
        filename = "sample.jpg",
        contentType = Some("image/jpeg"),
        ref = tempFile
      )

      val request = FakeRequest(POST, "/v1/media")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map(
              "description" -> Seq("Test upload"),
              "tags[]" -> Seq("test", "upload")
            ),
            files = Seq(filePart),
            badParts = Seq.empty
          )
        )

      val result = route(app, request)(implicitly).get

      status(result) mustBe CREATED
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "id").as[String] mustBe "550e8400-e29b-41d4-a716-446655440000"
      (json \ "originalFilename").as[String] mustBe "sample.jpg"
      (json \ "mimeType").as[String] mustBe "image/jpeg"
      (json \ "description").as[String] mustBe "Test image"
    }

    "return 401 Unauthorized for missing authorization" in {
      val tempFile = TemporaryFile(play.api.libs.Files.SingletonTemporaryFileCreator, "test", ".jpg")
      val filePart = MultipartFormData.FilePart(
        key = "file",
        filename = "sample.jpg",
        contentType = Some("image/jpeg"),
        ref = tempFile
      )

      val request = FakeRequest(POST, "/v1/media")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map.empty,
            files = Seq(filePart),
            badParts = Seq.empty
          )
        )

      val result = route(app, request)(implicitly).get

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid or missing authorization"
    }

    "return 401 Unauthorized for invalid JWT token" in {
      val mockJwtService = app.injector.instanceOf[JwtService]

      // Mock invalid JWT
      when(mockJwtService.extractUserId("invalid.jwt.token"))
        .thenReturn(None)

      val tempFile = TemporaryFile(play.api.libs.Files.SingletonTemporaryFileCreator, "test", ".jpg")
      val filePart = MultipartFormData.FilePart(
        key = "file",
        filename = "sample.jpg", 
        contentType = Some("image/jpeg"),
        ref = tempFile
      )

      val request = FakeRequest(POST, "/v1/media")
        .withHeaders("Authorization" -> "Bearer invalid.jwt.token")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map.empty,
            files = Seq(filePart),
            badParts = Seq.empty
          )
        )

      val result = route(app, request)(implicitly).get

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/json")
    }

    "return 400 Bad Request for missing file" in {
      val mockJwtService = app.injector.instanceOf[JwtService]

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("7f2a4b8e-1234-5678-90ab-cdef12345678"))

      val request = FakeRequest(POST, "/v1/media")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map("description" -> Seq("Test without file")),
            files = Seq.empty, // No file
            badParts = Seq.empty
          )
        )

      val result = route(app, request)(implicitly).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Missing file"
    }

    "return 400 Bad Request for media service error" in {
      val mockMediaService = app.injector.instanceOf[MediaService]
      val mockJwtService = app.injector.instanceOf[JwtService]

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("7f2a4b8e-1234-5678-90ab-cdef12345678"))

      // Mock media service failure
      when(mockMediaService.uploadMedia(any[UUID], any[MultipartFormData.FilePart[TemporaryFile]], any[Option[String]], any[List[String]]))
        .thenReturn(Future.successful(Left("Unsupported file format")))

      val tempFile = TemporaryFile(play.api.libs.Files.SingletonTemporaryFileCreator, "test", ".txt")
      val filePart = MultipartFormData.FilePart(
        key = "file",
        filename = "document.txt",
        contentType = Some("text/plain"),
        ref = tempFile
      )

      val request = FakeRequest(POST, "/v1/media")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")
        .withMultipartFormDataBody(
          MultipartFormData(
            dataParts = Map.empty,
            files = Seq(filePart),
            badParts = Seq.empty
          )
        )

      val result = route(app, request)(implicitly).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Unsupported file format"
    }
  }

  "MediaController getMedia" should {

    "return 200 OK for valid media ID and owner" in {
      val mockMediaRepository = app.injector.instanceOf[MediaRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val testMedia = createTestMediaItem
      val mediaId = testMedia.id

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("7f2a4b8e-1234-5678-90ab-cdef12345678"))

      when(mockMediaRepository.findById(mediaId))
        .thenReturn(Future.successful(Some(testMedia)))

      val request = FakeRequest(GET, s"/v1/media/${mediaId.toString}")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")

      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "id").as[String] mustBe mediaId.toString
      (json \ "originalFilename").as[String] mustBe "sample.jpg"
    }

    "return 403 Forbidden for non-owner access" in {
      val mockMediaRepository = app.injector.instanceOf[MediaRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val testMedia = createTestMediaItem
      val mediaId = testMedia.id

      // Different user ID
      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("different-user-id"))

      when(mockMediaRepository.findById(mediaId))
        .thenReturn(Future.successful(Some(testMedia)))

      val request = FakeRequest(GET, s"/v1/media/${mediaId.toString}")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")

      val result = route(app, request).get

      status(result) mustBe FORBIDDEN
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Access denied"
    }

    "return 404 Not Found for non-existent media" in {
      val mockMediaRepository = app.injector.instanceOf[MediaRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val mediaId = UUID.randomUUID()

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("7f2a4b8e-1234-5678-90ab-cdef12345678"))

      when(mockMediaRepository.findById(mediaId))
        .thenReturn(Future.successful(None))

      val request = FakeRequest(GET, s"/v1/media/${mediaId.toString}")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")

      val result = route(app, request).get

      status(result) mustBe NOT_FOUND
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Media not found"
    }

    "return 400 Bad Request for invalid UUID format" in {
      val mockJwtService = app.injector.instanceOf[JwtService]

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("7f2a4b8e-1234-5678-90ab-cdef12345678"))

      val request = FakeRequest(GET, "/v1/media/invalid-uuid")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")

      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid media ID"
    }
  }

  "MediaController listMedia" should {

    "return 200 OK with paginated media list" in {
      val mockMediaRepository = app.injector.instanceOf[MediaRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val testMedia = createTestMediaItem
      val userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some(userId.toString))

      when(mockMediaRepository.findByUserId(userId, 0, 50))
        .thenReturn(Future.successful(List(testMedia)))

      val request = FakeRequest(GET, "/v1/media")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")

      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "items").as[JsArray].value.length mustBe 1
      (json \ "pagination" \ "offset").as[Int] mustBe 0
      (json \ "pagination" \ "limit").as[Int] mustBe 50
    }

    "handle pagination parameters correctly" in {
      val mockMediaRepository = app.injector.instanceOf[MediaRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some(userId.toString))

      when(mockMediaRepository.findByUserId(userId, 10, 25))
        .thenReturn(Future.successful(List.empty))

      val request = FakeRequest(GET, "/v1/media?offset=10&limit=25")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")

      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "pagination" \ "offset").as[Int] mustBe 10
      (json \ "pagination" \ "limit").as[Int] mustBe 25
    }

    "limit maximum page size to 100" in {
      val mockMediaRepository = app.injector.instanceOf[MediaRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val userId = UUID.fromString("7f2a4b8e-1234-5678-90ab-cdef12345678")

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some(userId.toString))

      when(mockMediaRepository.findByUserId(userId, 0, 100))
        .thenReturn(Future.successful(List.empty))

      val request = FakeRequest(GET, "/v1/media?limit=500") // Request too large
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")

      val result = route(app, request).get

      status(result) mustBe OK

      val json = contentAsJson(result)
      (json \ "pagination" \ "limit").as[Int] mustBe 100 // Capped at 100
    }
  }

  "MediaController updateMedia" should {

    "return 200 OK for successful metadata update" in {
      val mockMediaRepository = app.injector.instanceOf[MediaRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val testMedia = createTestMediaItem
      val updatedMedia = testMedia.copy(
        description = Some("Updated description"),
        tags = List("updated", "test")
      )

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("7f2a4b8e-1234-5678-90ab-cdef12345678"))

      when(mockMediaRepository.findById(testMedia.id))
        .thenReturn(Future.successful(Some(testMedia)))

      when(mockMediaRepository.update(any[MediaItem]))
        .thenReturn(Future.successful(Some(updatedMedia)))

      val request = FakeRequest(PUT, s"/v1/media/${testMedia.id.toString}")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")
        .withJsonBody(Json.obj(
          "description" -> "Updated description",
          "tags" -> Json.arr("updated", "test")
        ))

      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")

      val json = contentAsJson(result)
      (json \ "description").as[String] mustBe "Updated description"
      (json \ "tags").as[List[String]] mustBe List("updated", "test")
    }
  }

  "MediaController deleteMedia" should {

    "return 204 No Content for successful deletion" in {
      val mockMediaRepository = app.injector.instanceOf[MediaRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val testMedia = createTestMediaItem

      when(mockJwtService.extractUserId(validJwtToken))
        .thenReturn(Some("7f2a4b8e-1234-5678-90ab-cdef12345678"))

      when(mockMediaRepository.findById(testMedia.id))
        .thenReturn(Future.successful(Some(testMedia)))

      when(mockMediaRepository.delete(testMedia.id))
        .thenReturn(Future.successful(true))

      val request = FakeRequest(DELETE, s"/v1/media/${testMedia.id.toString}")
        .withHeaders("Authorization" -> s"Bearer $validJwtToken")

      val result = route(app, request).get

      status(result) mustBe NO_CONTENT
    }
  }
}