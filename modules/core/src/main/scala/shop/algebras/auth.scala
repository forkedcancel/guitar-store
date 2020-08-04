package shop.algebras

import shop.domain.auth.{ JwtToken, Password, UserId, UserName }

trait Auth[F[_]] {
  def findUser(token: JwtToken): F[Option[User]]

  def newUser(username: UserName, password: Password): F[JwtToken]

  def login(username: UserName, password: Password): F[JwtToken]

  def logout(token: JwtToken, username: UserName): F[Unit]
}

case class User(
    id: UserId,
    userName: UserName
)
