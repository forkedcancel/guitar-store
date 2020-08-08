package shop.modules

import cats.effect._
import io.chrisdavenport.log4cats.Logger
import shop.effects.Background
import shop.programs.CheckoutProgram

object Programs {
  def make[F[_]: Background: Logger: Sync: Timer](checkout: CheckoutProgram[F]): F[Programs[F]] =
    Sync[F].delay(new Programs[F](checkout))
}

final class Programs[F[_]] private (
    val checkout: CheckoutProgram[F]
)
