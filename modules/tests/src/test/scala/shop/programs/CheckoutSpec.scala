package shop.programs

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits.{ catsSyntaxEq => _, _ }
import retry.RetryPolicy
import retry.RetryPolicies._
import shop.algebras._
import shop.arbitraries._
import shop.domain._
import shop.domain.auth._
import shop.domain.cart._
import shop.domain.checkout._
//import shop.domain.item._
import shop.domain.order._
import shop.domain.payment._
import shop.http.clients._
import squants.market._
import suite._

class CheckoutSpec extends PureTestSuite {

  val MaxRetries = 3

  val retryPolicy: RetryPolicy[IO] =
    limitRetries[IO](MaxRetries)

  def unreachableClient: PaymentClient[IO] =
    new PaymentClient[IO] {
      override def process(payment: Payment): IO[PaymentId] =
        IO.raiseError(PaymentError(""))
    }

  def recoveringClient(
      attemptsSoFar: Ref[IO, Int],
      paymentId: PaymentId
  ): PaymentClient[IO] =
    new PaymentClient[IO] {
      override def process(payment: Payment): IO[PaymentId] =
        attemptsSoFar.get.flatMap {
          case n if n === 1 =>
            IO.pure(paymentId)
          case _ =>
            attemptsSoFar.update(_ + 1) *>
                IO.raiseError(PaymentError(""))
        }
    }

  def successfulClient(paymentId: PaymentId): PaymentClient[IO] =
    new PaymentClient[IO] {
      def process(payment: Payment): IO[PaymentId] =
        IO.pure(paymentId)
    }

  def emptyCart: ShoppingCart[IO] =
    new TestCart {
      override def get(userId: UserId): IO[CartTotal] =
        IO.pure(CartTotal(List.empty[CartItem], USD(0)))
    }

  def failingCart(cartTotal: CartTotal): ShoppingCart[IO] =
    new TestCart {
      override def get(userId: UserId): IO[CartTotal] = IO.pure(cartTotal)

      override def delete(userId: UserId): IO[Unit] = IO.raiseError(new Exception(""))
    }

  def successfulCart(cartTotal: CartTotal): ShoppingCart[IO] =
    new TestCart {
      override def get(userId: auth.UserId): IO[CartTotal] = IO.pure(cartTotal)
      override def delete(userId: UserId): IO[Unit]        = IO.unit
    }

  def failingOrders: Orders[IO] =
    new TestOrders {
      override def create(userId: UserId, paymentId: PaymentId, items: List[CartItem], total: Money): IO[OrderId] =
        IO.raiseError(OrderError(""))
    }

  def successfulOrders(oid: OrderId): Orders[IO] =
    new TestOrders {
      override def create(userId: UserId, paymentId: PaymentId, items: List[cart.CartItem], total: Money): IO[OrderId] =
        IO.pure(oid)
    }

  test("empty cart") {
    implicit val bg = shop.background.NoOp
    import shop.logger.NoOp
    forAll { (uid: UserId, pid: PaymentId, oid: OrderId, card: Card) =>
      IOAssertion {
        new CheckoutProgram[IO](successfulClient(pid), emptyCart, successfulOrders(oid), retryPolicy)
          .checkout(uid, card)
          .attempt
          .map {
            case Left(EmptyCartError) =>
              assert(true)
            case _ =>
              fail("Cart was not empty as expected")
          }
      }
    }
  }

  test("failing cart") {
    implicit val bg = shop.background.NoOp
    import shop.logger.NoOp
    forAll { (uid: UserId, pid: PaymentId, oid: OrderId, ct: CartTotal, card: Card) =>
      IOAssertion {
        new CheckoutProgram[IO](successfulClient(pid), failingCart(ct), successfulOrders(oid), retryPolicy)
          .checkout(uid, card)
          .map { id =>
            assert(id === oid)
          }
      }
    }
  }

  test("unreachable payment client") {
    forAll { (uid: UserId, oid: OrderId, ct: CartTotal, card: Card) =>
      IOAssertion {
        Ref.of[IO, List[String]](List.empty).flatMap { logs =>
          implicit val bg     = shop.background.NoOp
          implicit val logger = shop.logger.acc(logs)
          new CheckoutProgram[IO](unreachableClient, successfulCart(ct), successfulOrders(oid), retryPolicy)
            .checkout(uid, card)
            .attempt
            .flatMap {
              case Left(PaymentError(_)) =>
                logs.get.map {
                  case x :: xs => assert(x.contains("Giving up") && xs.size === MaxRetries)
                  case _       => fail(s"Expected $MaxRetries retries")
                }
              case _ => fail("Expected payment error")
            }
        }
      }
    }
  }

  test("recovering payment client") {
    forAll { (uid: UserId, pid: PaymentId, oid: OrderId, ct: CartTotal, card: Card) =>
      IOAssertion {
        Ref.of[IO, List[String]](List.empty).flatMap { logs =>
          Ref.of[IO, Int](0).flatMap { attemptsSoFar =>
            implicit val bg     = shop.background.NoOp
            implicit val logger = shop.logger.acc(logs)
            new CheckoutProgram[IO](
              recoveringClient(attemptsSoFar, pid),
              successfulCart(ct),
              successfulOrders(oid),
              retryPolicy
            ).checkout(uid, card)
              .attempt
              .flatMap {
                case Right(id) =>
                  logs.get.map { xs =>
                    assert(id === oid && xs.size === 1)
                  }
                case _ => fail("Expected payment error")
              }
          }
        }
      }
    }
  }

  test(s"successful checkout") {
    implicit val bg = shop.background.NoOp
    import shop.logger.NoOp
    forAll { (uid: UserId, pid: PaymentId, oid: OrderId, ct: CartTotal, card: Card) =>
      IOAssertion {
        new CheckoutProgram[IO](successfulClient(pid), successfulCart(ct), successfulOrders(oid), retryPolicy)
          .checkout(uid, card)
          .map { id =>
            assert(id === oid)
          }
      }
    }
  }

  test("cannot create order, run in the background") {
    forAll { (uid: UserId, pid: PaymentId, ct: CartTotal, card: Card) =>
      IOAssertion {
        Ref.of[IO, Int](0).flatMap { ref =>
          Ref.of[IO, List[String]](List.empty).flatMap { logs =>
            implicit val bg     = shop.background.counter(ref)
            implicit val logger = shop.logger.acc(logs)
            new CheckoutProgram[IO](successfulClient(pid), successfulCart(ct), failingOrders, retryPolicy)
              .checkout(uid, card)
              .attempt
              .flatMap {
                case Left(OrderError(_)) =>
                  (ref.get, logs.get).mapN {
                    case (c, (x :: y :: xs)) =>
                      assert(
                        x.contains("Rescheduling") &&
                          y.contains("Giving up") &&
                          xs.size === MaxRetries &&
                          c === 1
                      )
                    case _ => fail(s"Expected $MaxRetries retries and reschedule")
                  }
                case _ =>
                  fail("Expected order error")
              }
          }
        }
      }
    }
  }

}

protected class TestCart() extends ShoppingCart[IO] {
  override def add(userId: auth.UserId, itemId: item.ItemId, quantity: cart.Quantity): IO[Unit] = ???

  override def delete(userId: auth.UserId): IO[Unit] = ???

  override def get(userId: auth.UserId): IO[CartTotal] = ???

  override def removeItem(userId: auth.UserId, itemId: item.ItemId): IO[Unit] = ???

  override def update(userId: auth.UserId, cart: Cart): IO[Unit] = ???
}

protected class TestOrders extends Orders[IO] {
  override def get(userId: UserId, orderId: OrderId): IO[Option[order.Order]] = ???

  override def findBy(userId: UserId): IO[List[order.Order]] = ???

  override def create(userId: UserId, paymentId: PaymentId, items: List[cart.CartItem], total: Money): IO[OrderId] = ???
}
