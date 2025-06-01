package modules

import com.google.inject.AbstractModule
import repositories.UserRepository
import repositories.impl.SlickUserRepository

class RepositoryModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[UserRepository]).to(classOf[SlickUserRepository])
  }
}