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
import repositories.{UserRepository, UserWithPassword}
import services.{PasswordService, JwtService}
import scala.concurrent.{Future, ExecutionContext}
import java.time.Instant
import java.util.UUID
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatestplus.mockito.MockitoSugar

class AuthControllerSpec extends PlaySpec with GuiceOneAppPerTest with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def createTestUser: User = User(
    id = "550e8400-e29b-41d4-a716-446655440000",
    email = "test@example.com",
    name = "Test User",
    createdAt = Instant.now,
    storageQuota = 10737418240L, // 10GB
    storageUsed = 0L
  )

  def createUserWithPassword(user: User, passwordHash: String): UserWithPassword = 
    UserWithPassword(user, passwordHash)

  override def fakeApplication(): Application = {
    val mockUserRepository = mock[UserRepository]
    val mockPasswordService = mock[PasswordService]
    val mockJwtService = mock[JwtService]

    new GuiceApplicationBuilder()
      .overrides(
        bind[UserRepository].toInstance(mockUserRepository),
        bind[PasswordService].toInstance(mockPasswordService),
        bind[JwtService].toInstance(mockJwtService)
      )
      .build()
  }

  "AuthController login" should {

    "return 200 OK with auth tokens for valid credentials" in {
      val mockUserRepository = app.injector.instanceOf[UserRepository]
      val mockPasswordService = app.injector.instanceOf[PasswordService]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val testUser = createTestUser
      val passwordHash = "$2a$10$validhash"
      val userWithPassword = createUserWithPassword(testUser, passwordHash)

      when(mockUserRepository.findByEmailWithPassword("test@example.com"))
        .thenReturn(Future.successful(Some(userWithPassword)))
      when(mockPasswordService.verifyPassword("correctpassword", passwordHash))
        .thenReturn(true)
      when(mockJwtService.generateAccessToken(testUser.id, testUser.email))
        .thenReturn("mock.access.token")
      when(mockJwtService.generateRefreshToken(testUser.id))
        .thenReturn("mock.refresh.token")

      val request = FakeRequest(POST, "/v1/auth/login")
        .withJsonBody(Json.obj(
          "email" -> "test@example.com",
          "password" -> "correctpassword"
        ))

      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "accessToken").as[String] mustBe "mock.access.token"
      (json \ "refreshToken").as[String] mustBe "mock.refresh.token"
      (json \ "expiresIn").as[Int] mustBe 3600
      (json \ "user" \ "email").as[String] mustBe "test@example.com"
      (json \ "user" \ "name").as[String] mustBe "Test User"
    }

    "return 401 Unauthorized for incorrect password" in {
      val mockUserRepository = app.injector.instanceOf[UserRepository]
      val mockPasswordService = app.injector.instanceOf[PasswordService]
      val testUser = createTestUser
      val passwordHash = "$2a$10$validhash"
      val userWithPassword = createUserWithPassword(testUser, passwordHash)

      when(mockUserRepository.findByEmailWithPassword("test@example.com"))
        .thenReturn(Future.successful(Some(userWithPassword)))
      when(mockPasswordService.verifyPassword("wrongpassword", passwordHash))
        .thenReturn(false)

      val request = FakeRequest(POST, "/v1/auth/login")
        .withJsonBody(Json.obj(
          "email" -> "test@example.com",
          "password" -> "wrongpassword"
        ))

      val result = route(app, request).get

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid credentials"
    }

    "return 401 Unauthorized for non-existent email" in {
      val mockUserRepository = app.injector.instanceOf[UserRepository]

      when(mockUserRepository.findByEmailWithPassword("nonexistent@example.com"))
        .thenReturn(Future.successful(None))

      val request = FakeRequest(POST, "/v1/auth/login")
        .withJsonBody(Json.obj(
          "email" -> "nonexistent@example.com",
          "password" -> "anypassword"
        ))

      val result = route(app, request).get

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid credentials"
    }

    "return 400 Bad Request for invalid JSON format" in {
      val request = FakeRequest(POST, "/v1/auth/login")
        .withJsonBody(Json.obj(
          "username" -> "test@example.com"  // Missing password field
        ))

      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid request format"
      (json \ "details").isDefined mustBe true
    }

    "return 400 Bad Request for missing email field" in {
      val request = FakeRequest(POST, "/v1/auth/login")
        .withJsonBody(Json.obj(
          "password" -> "password123"
        ))

      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid request format"
    }

    "return 400 Bad Request for missing password field" in {
      val request = FakeRequest(POST, "/v1/auth/login")
        .withJsonBody(Json.obj(
          "email" -> "test@example.com"
        ))

      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid request format"
    }

    "reject test user login when password does not match" in {
      val mockUserRepository = app.injector.instanceOf[UserRepository]
      val mockPasswordService = app.injector.instanceOf[PasswordService]
      val testUser = createTestUser.copy(email = "tanaka.yuki@example.com")
      val passwordHash = "$2a$10$somehash"
      val userWithPassword = createUserWithPassword(testUser, passwordHash)

      when(mockUserRepository.findByEmailWithPassword("tanaka.yuki@example.com"))
        .thenReturn(Future.successful(Some(userWithPassword)))
      when(mockPasswordService.verifyPassword("SecurePass123!", passwordHash))
        .thenReturn(false) // Password verification fails - no more test user bypass

      val request = FakeRequest(POST, "/v1/auth/login")
        .withJsonBody(Json.obj(
          "email" -> "tanaka.yuki@example.com",
          "password" -> "SecurePass123!"
        ))

      val result = route(app, request).get

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid credentials"
    }
  }

  "AuthController refresh" should {

    "return 200 OK with new tokens for valid refresh token" in {
      val mockUserRepository = app.injector.instanceOf[UserRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val testUser = createTestUser
      val userId = testUser.id
      val userUuid = UUID.fromString(userId)
      val validRefreshToken = "valid.refresh.token"
      val newAccessToken = "new.access.token"
      val newRefreshToken = "new.refresh.token"

      when(mockJwtService.isRefreshToken(validRefreshToken)).thenReturn(true)
      when(mockJwtService.extractUserId(validRefreshToken)).thenReturn(Some(userId))
      when(mockJwtService.generateAccessToken(userId, testUser.email)).thenReturn(newAccessToken)
      when(mockJwtService.generateRefreshToken(userId)).thenReturn(newRefreshToken)
      when(mockUserRepository.findById(userUuid)).thenReturn(Future.successful(Some(testUser)))

      val request = FakeRequest(POST, "/v1/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> validRefreshToken))

      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "accessToken").as[String] mustBe newAccessToken
      (json \ "refreshToken").as[String] mustBe newRefreshToken
      (json \ "expiresIn").as[Int] mustBe 3600
      (json \ "user" \ "email").as[String] mustBe testUser.email
    }

    "return 401 Unauthorized for invalid refresh token type" in {
      val mockJwtService = app.injector.instanceOf[JwtService]
      val invalidRefreshToken = "invalid.refresh.token"

      when(mockJwtService.isRefreshToken(invalidRefreshToken)).thenReturn(false)

      val request = FakeRequest(POST, "/v1/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> invalidRefreshToken))

      val result = route(app, request).get

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid refresh token"
    }

    "return 401 Unauthorized when user not found" in {
      val mockUserRepository = app.injector.instanceOf[UserRepository]
      val mockJwtService = app.injector.instanceOf[JwtService]
      val userId = UUID.randomUUID().toString
      val userUuid = UUID.fromString(userId)
      val validRefreshToken = "valid.refresh.token"

      when(mockJwtService.isRefreshToken(validRefreshToken)).thenReturn(true)
      when(mockJwtService.extractUserId(validRefreshToken)).thenReturn(Some(userId))
      when(mockUserRepository.findById(userUuid)).thenReturn(Future.successful(None))

      val request = FakeRequest(POST, "/v1/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> validRefreshToken))

      val result = route(app, request).get

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid refresh token"
    }

    "return 401 Unauthorized when userId cannot be extracted from token" in {
      val mockJwtService = app.injector.instanceOf[JwtService]
      val validRefreshToken = "valid.refresh.token.no.userid"

      when(mockJwtService.isRefreshToken(validRefreshToken)).thenReturn(true)
      when(mockJwtService.extractUserId(validRefreshToken)).thenReturn(None)

      val request = FakeRequest(POST, "/v1/auth/refresh")
        .withJsonBody(Json.obj("refreshToken" -> validRefreshToken))

      val result = route(app, request).get

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid refresh token"
    }

    "return 400 Bad Request for malformed request" in {
      val request = FakeRequest(POST, "/v1/auth/refresh")
        .withJsonBody(Json.obj("invalidField" -> "value"))

      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid request format"
      (json \ "details").isDefined mustBe true
    }

    "return 400 Bad Request for missing refreshToken field" in {
      val request = FakeRequest(POST, "/v1/auth/refresh")
        .withJsonBody(Json.obj())

      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid request format"
    }
  }
}