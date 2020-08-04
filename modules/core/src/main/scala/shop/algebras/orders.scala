package shop.algebras

import shop.domain.auth.UserId
import shop.domain.cart.CartItem
import shop.domain.order._
import squants.market.Money

trait Orders[F[_]] {
  def get(
           userId: UserId,
           orderId: OrderId
         ): F[Option[Order]]

  def findBy(userId: UserId): F[List[Order]]

  def create(userId: UserId,
             paymentId: PaymentId,
             items: List[CartItem],
             total: Money
            ): F[OrderId]

}

object LiveOrders {

}