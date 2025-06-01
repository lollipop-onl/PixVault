package services

import javax.inject.Singleton
import org.mindrot.jbcrypt.BCrypt

@Singleton
class PasswordService {
  
  private val workFactor = 10
  
  def hashPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt(workFactor))
  }
  
  def verifyPassword(password: String, hash: String): Boolean = {
    BCrypt.checkpw(password, hash)
  }
}