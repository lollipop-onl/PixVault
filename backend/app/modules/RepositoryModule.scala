package modules

import com.google.inject.AbstractModule
import repositories._
import repositories.impl._

class RepositoryModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[UserRepository]).to(classOf[SlickUserRepository])
    bind(classOf[MediaRepository]).to(classOf[SlickMediaRepository])
  }
}