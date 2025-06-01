package services

import javax.inject._
import play.api.Configuration
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.libs.json.{Json, JsObject}
import java.time.Clock
import scala.concurrent.duration._

@Singleton
class JwtService @Inject()(config: Configuration) {
  
  private val secretKey = config.get[String]("jwt.secret")
  private val algorithm = JwtAlgorithm.HS256
  private val accessTokenExpiration = config.get[Duration]("jwt.access.expiration")
  private val refreshTokenExpiration = config.get[Duration]("jwt.refresh.expiration")
  
  implicit val clock: Clock = Clock.systemUTC
  
  def generateAccessToken(userId: String, email: String): String = {
    val claim = JwtClaim()
      .issuedNow
      .expiresIn(accessTokenExpiration.toSeconds)
      .+("userId", userId)
      .+("email", email)
      .+("type", "access")
    
    JwtJson.encode(claim, secretKey, algorithm)
  }
  
  def generateRefreshToken(userId: String): String = {
    val claim = JwtClaim()
      .issuedNow
      .expiresIn(refreshTokenExpiration.toSeconds)
      .+("userId", userId)
      .+("type", "refresh")
    
    JwtJson.encode(claim, secretKey, algorithm)
  }
  
  def validateToken(token: String): Option[JwtClaim] = {
    JwtJson.decode(token, secretKey, Seq(algorithm)).toOption
  }
  
  def extractUserId(token: String): Option[String] = {
    validateToken(token).flatMap { claim =>
      (Json.parse(claim.content) \ "userId").asOpt[String]
    }
  }
  
  def extractEmail(token: String): Option[String] = {
    validateToken(token).flatMap { claim =>
      (Json.parse(claim.content) \ "email").asOpt[String]
    }
  }
  
  def isAccessToken(token: String): Boolean = {
    validateToken(token).exists { claim =>
      (Json.parse(claim.content) \ "type").asOpt[String].contains("access")
    }
  }
  
  def isRefreshToken(token: String): Boolean = {
    validateToken(token).exists { claim =>
      (Json.parse(claim.content) \ "type").asOpt[String].contains("refresh")
    }
  }
}