package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json._

@Singleton
class HealthController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  def health = Action { implicit request: Request[AnyContent] =>
    Ok(Json.obj(
      "status" -> "healthy",
      "service" -> "pixvault-api",
      "version" -> "1.0-SNAPSHOT"
    ))
  }
}