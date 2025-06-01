package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import models._
import repositories.UserRepository
import services.{PasswordService, JwtService}
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject()(
    val controllerComponents: ControllerComponents,
    userRepository: UserRepository,
    passwordService: PasswordService,
    jwtService: JwtService
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
    userRepository.findByEmailWithPassword(email).map { userWithPasswordOpt =>
      userWithPasswordOpt.flatMap { case userWithPassword =>
        // Verify password against stored hash
        if (passwordService.verifyPassword(password, userWithPassword.passwordHash)) {
          val user = userWithPassword.user
          val accessToken = jwtService.generateAccessToken(user.id, user.email)
          val refreshToken = jwtService.generateRefreshToken(user.id)

          Some(AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = 3600, // 1 hour in seconds
            user = user
          ))
        } else {
          None
        }
      }
    }
  }

}