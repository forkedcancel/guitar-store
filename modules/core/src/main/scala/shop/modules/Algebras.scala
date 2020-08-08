package shop.modules

import cats.Parallel
import cats.effect.{ Concurrent, Sync, Timer }
import shop.algebras._

object Algebras {
  def make[F[_]: Concurrent: Parallel: Timer](
      cart: ShoppingCart[F],
      brands: Brands[F],
      categories: Categories[F],
      items: Items[F],
      orders: Orders[F],
      healthCheck: HealthCheck[F]
  ): F[Algebras[F]] = Sync[F].delay(new Algebras[F](cart, brands, categories, items, orders, healthCheck))
}

final class Algebras[F[_]] private (
    val cart: ShoppingCart[F],
    val brands: Brands[F],
    val categories: Categories[F],
    val items: Items[F],
    val orders: Orders[F],
    val healthCheck: HealthCheck[F]
)
