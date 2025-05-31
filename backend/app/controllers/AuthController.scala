package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import models._
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject()(
    val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext) extends BaseController {

  def login: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[LoginRequest] match {
      case JsSuccess(loginRequest, _) =>
        authenticateUser(loginRequest.email, loginRequest.password).map {
          case Some(authResponse) =>
            Ok(Json.toJson(authResponse))
          case None =>
            Unauthorized(Json.obj(
              "error" -> "Invalid credentials"
            ))
        }
      
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj(
          "error" -> "Invalid request format",
          "details" -> JsError.toJson(errors)
        )))
    }
  }

  private def authenticateUser(email: String, password: String): Future[Option[AuthResponse]] = {
    Future.successful {
      if (email == "tanaka.yuki@example.com" && password == "SecurePass123!") {
        val user = User(
          id = "7f2a4b8e-1234-5678-90ab-cdef12345678",
          email = email,
          name = "田中 由紀",
          createdAt = Instant.parse("2023-01-15T08:00:00.000Z"),
          storageQuota = 107374182400L,
          storageUsed = 45678901234L
        )

        val accessToken = generateMockJWT("access", user.id, email)
        val refreshToken = generateMockJWT("refresh", user.id, email)

        Some(AuthResponse(
          accessToken = accessToken,
          refreshToken = refreshToken,
          expiresIn = 3600,
          user = user
        ))
      } else {
        None
      }
    }
  }

  private def generateMockJWT(tokenType: String, userId: String, email: String): String = {
    val header = """{"alg":"HS256","typ":"JWT"}"""
    val now = System.currentTimeMillis() / 1000
    val exp = now + 3600
    
    val payload = if (tokenType == "access") {
      s"""{"userId":"$userId","email":"$email","iat":$now,"exp":$exp}"""
    } else {
      s"""{"userId":"$userId","type":"refresh","iat":$now}"""
    }
    
    val encodedHeader = java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(header.getBytes("UTF-8"))
    val encodedPayload = java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(payload.getBytes("UTF-8"))
    
    s"$encodedHeader.$encodedPayload.${tokenType}_token_signature"
  }
}