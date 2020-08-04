package shop.domain

import java.util.UUID

import io.estatico.newtype.macros.newtype
import shop.domain.brand._
import shop.domain.category._
import squants.market.Money

object item {

  @newtype case class ItemId(value: UUID)
  @newtype case class ItemName(value: String)
  @newtype case class ItemDescription(value: String)

  case class Item(
                   uuid: ItemId,
                   name: ItemName,
                   description: ItemDescription,
                   price: Money,
                   brand: Brand,
                   category: Category
                 )

  case class CreateItem(
                         name: ItemName,
                         description: ItemDescription,
                         price: Money,
                         brandId: BrandId,
                         categoryId: CategoryId
                       )

  case class UpdateItem(
                         id: ItemId,
                         price: Money
                       )
}
