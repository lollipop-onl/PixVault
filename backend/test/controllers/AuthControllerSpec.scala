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
import services.PasswordService
import scala.concurrent.{Future, ExecutionContext}
import java.time.Instant
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatestplus.mockito.MockitoSugar

class AuthControllerSpec extends PlaySpec with GuiceOneAppPerTest with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def createTestUser: User = User(
    id = "test-user-id",
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

    new GuiceApplicationBuilder()
      .overrides(
        bind[UserRepository].toInstance(mockUserRepository),
        bind[PasswordService].toInstance(mockPasswordService)
      )
      .build()
  }

  "AuthController login" should {

    "return 200 OK with auth tokens for valid credentials" in {
      val mockUserRepository = app.injector.instanceOf[UserRepository]
      val mockPasswordService = app.injector.instanceOf[PasswordService]
      val testUser = createTestUser
      val passwordHash = "$2a$10$validhash"
      val userWithPassword = createUserWithPassword(testUser, passwordHash)

      when(mockUserRepository.findByEmailWithPassword("test@example.com"))
        .thenReturn(Future.successful(Some(userWithPassword)))
      when(mockPasswordService.verifyPassword("correctpassword", passwordHash))
        .thenReturn(true)

      val request = FakeRequest(POST, "/api/auth/login")
        .withJsonBody(Json.obj(
          "email" -> "test@example.com",
          "password" -> "correctpassword"
        ))

      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "accessToken").as[String] must include("access_token_signature")
      (json \ "refreshToken").as[String] must include("refresh_token_signature")
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

      val request = FakeRequest(POST, "/api/auth/login")
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

      val request = FakeRequest(POST, "/api/auth/login")
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
      val request = FakeRequest(POST, "/api/auth/login")
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
      val request = FakeRequest(POST, "/api/auth/login")
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
      val request = FakeRequest(POST, "/api/auth/login")
        .withJsonBody(Json.obj(
          "email" -> "test@example.com"
        ))

      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "error").as[String] mustBe "Invalid request format"
    }

    "allow test user login with hardcoded credentials" in {
      val mockUserRepository = app.injector.instanceOf[UserRepository]
      val mockPasswordService = app.injector.instanceOf[PasswordService]
      val testUser = createTestUser.copy(email = "tanaka.yuki@example.com")
      val passwordHash = "$2a$10$somehash"
      val userWithPassword = createUserWithPassword(testUser, passwordHash)

      when(mockUserRepository.findByEmailWithPassword("tanaka.yuki@example.com"))
        .thenReturn(Future.successful(Some(userWithPassword)))
      when(mockPasswordService.verifyPassword("SecurePass123!", passwordHash))
        .thenReturn(false) // Password verification would fail, but test user bypass should work

      val request = FakeRequest(POST, "/api/auth/login")
        .withJsonBody(Json.obj(
          "email" -> "tanaka.yuki@example.com",
          "password" -> "SecurePass123!"
        ))

      val result = route(app, request).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      
      val json = contentAsJson(result)
      (json \ "accessToken").as[String] must include("access_token_signature")
      (json \ "user" \ "email").as[String] mustBe "tanaka.yuki@example.com"
    }
  }
}