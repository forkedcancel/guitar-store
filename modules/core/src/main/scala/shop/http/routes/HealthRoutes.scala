package shop.http.routes

import cats._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server._
import shop.algebras.HealthCheck
import shop.http.json._

final class HealthRoutes[F[_]: Defer: Monad](healthCheck: HealthCheck[F]) extends Http4sDsl[F] {

  private[routes] val prefixPath = "/healthcheck"

  private val httpRoutes = HttpRoutes.of[F] {
    case GET -> Root =>
      Ok(healthCheck.status)
  }

  val routes = Router(
    prefixPath -> httpRoutes
  )

}
