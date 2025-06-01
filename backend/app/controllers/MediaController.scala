package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import models._
import services._
import repositories.MediaRepository
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID
import play.api.libs.Files.TemporaryFile

@Singleton
class MediaController @Inject()(
  val controllerComponents: ControllerComponents,
  mediaService: MediaService,
  mediaRepository: MediaRepository,
  jwtService: JwtService
)(implicit ec: ExecutionContext) extends BaseController with Logging {
  
  private def extractUserId(request: RequestHeader): Option[UUID] = {
    request.headers.get("Authorization").flatMap { authHeader =>
      val token = authHeader.replace("Bearer ", "")
      jwtService.extractUserId(token).flatMap { userIdStr =>
        try {
          Some(UUID.fromString(userIdStr))
        } catch {
          case _: IllegalArgumentException => None
        }
      }
    }
  }
  
  def upload = Action.async(parse.multipartFormData) { implicit request =>
    extractUserId(request) match {
      case Some(userId) =>
        request.body.file("file") match {
          case Some(file) =>
            val description = request.body.dataParts.get("description").flatMap(_.headOption)
            val tags = request.body.dataParts.get("tags[]").getOrElse(Seq.empty).toList
            
            mediaService.uploadMedia(userId, file, description, tags).map {
              case Right(mediaItem) =>
                Created(Json.toJson(mediaItem))
              case Left(error) =>
                BadRequest(Json.obj("error" -> error))
            }
            
          case None =>
            Future.successful(BadRequest(Json.obj("error" -> "Missing file")))
        }
        
      case None =>
        Future.successful(Unauthorized(Json.obj("error" -> "Invalid or missing authorization")))
    }
  }
  
  def getMedia(id: String) = Action.async { implicit request =>
    extractUserId(request) match {
      case Some(userId) =>
        try {
          val mediaId = UUID.fromString(id)
          mediaRepository.findById(mediaId).map {
            case Some(media) if media.userId == userId =>
              Ok(Json.toJson(media))
            case Some(_) =>
              Forbidden(Json.obj("error" -> "Access denied"))
            case None =>
              NotFound(Json.obj("error" -> "Media not found"))
          }
        } catch {
          case _: IllegalArgumentException =>
            Future.successful(BadRequest(Json.obj("error" -> "Invalid media ID")))
        }
        
      case None =>
        Future.successful(Unauthorized(Json.obj("error" -> "Invalid or missing authorization")))
    }
  }
  
  def listMedia = Action.async { implicit request =>
    extractUserId(request) match {
      case Some(userId) =>
        val offset = request.getQueryString("offset").flatMap(_.toIntOption).getOrElse(0)
        val limit = request.getQueryString("limit").flatMap(_.toIntOption).getOrElse(50).min(100)
        
        mediaRepository.findByUserId(userId, offset, limit).map { items =>
          Ok(Json.obj(
            "items" -> Json.toJson(items),
            "pagination" -> Json.obj(
              "offset" -> offset,
              "limit" -> limit,
              "total" -> items.size
            )
          ))
        }
        
      case None =>
        Future.successful(Unauthorized(Json.obj("error" -> "Invalid or missing authorization")))
    }
  }
  
  def updateMedia(id: String) = Action.async(parse.json) { implicit request =>
    extractUserId(request) match {
      case Some(userId) =>
        try {
          val mediaId = UUID.fromString(id)
          
          val description = (request.body \ "description").asOpt[String]
          val tags = (request.body \ "tags").asOpt[List[String]].getOrElse(List.empty)
          
          mediaRepository.findById(mediaId).flatMap {
            case Some(media) if media.userId == userId =>
              val updated = media.copy(
                description = description.orElse(media.description),
                tags = tags
              )
              mediaRepository.update(updated).map {
                case Some(updatedMedia) =>
                  Ok(Json.toJson(updatedMedia))
                case None =>
                  InternalServerError(Json.obj("error" -> "Failed to update media"))
              }
              
            case Some(_) =>
              Future.successful(Forbidden(Json.obj("error" -> "Access denied")))
            case None =>
              Future.successful(NotFound(Json.obj("error" -> "Media not found")))
          }
        } catch {
          case _: IllegalArgumentException =>
            Future.successful(BadRequest(Json.obj("error" -> "Invalid media ID")))
        }
        
      case None =>
        Future.successful(Unauthorized(Json.obj("error" -> "Invalid or missing authorization")))
    }
  }
  
  def deleteMedia(id: String) = Action.async { implicit request =>
    extractUserId(request) match {
      case Some(userId) =>
        try {
          val mediaId = UUID.fromString(id)
          
          mediaRepository.findById(mediaId).flatMap {
            case Some(media) if media.userId == userId =>
              mediaRepository.delete(mediaId).map { success =>
                if (success) {
                  NoContent
                } else {
                  InternalServerError(Json.obj("error" -> "Failed to delete media"))
                }
              }
              
            case Some(_) =>
              Future.successful(Forbidden(Json.obj("error" -> "Access denied")))
            case None =>
              Future.successful(NotFound(Json.obj("error" -> "Media not found")))
          }
        } catch {
          case _: IllegalArgumentException =>
            Future.successful(BadRequest(Json.obj("error" -> "Invalid media ID")))
        }
        
      case None =>
        Future.successful(Unauthorized(Json.obj("error" -> "Invalid or missing authorization")))
    }
  }
}