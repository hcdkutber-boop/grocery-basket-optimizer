from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from enum import StrEnum
from typing import Any


class Capability(StrEnum):
    SEARCH = "search"
    BASKET = "basket"
    SLOTS = "slots"
    CHECKOUT = "checkout"
    ORDERS = "orders"


@dataclass(frozen=True, slots=True)
class StoreProduct:
    provider: str
    store_id: str
    product_id: str
    name: str
    price: Decimal
    regular_price: Decimal | None = None
    currency: str = "RUB"
    size: str | None = None
    unit: str | None = None
    unit_price: Decimal | None = None
    in_stock: bool = True
    stock_limit: Decimal | None = None
    rating: Decimal | None = None
    image_url: str | None = None
    raw: dict[str, Any] = field(default_factory=dict, compare=False, repr=False)


@dataclass(frozen=True, slots=True)
class BasketItem:
    product_id: str
    name: str
    quantity: Decimal
    unit_price: Decimal | None = None
    total_price: Decimal | None = None


@dataclass(frozen=True, slots=True)
class Basket:
    provider: str
    items: tuple[BasketItem, ...]
    total_cost: Decimal
    currency: str = "RUB"
    amount_to_minimum: Decimal | None = None

    @property
    def total_quantity(self) -> Decimal:
        return sum((item.quantity for item in self.items), start=Decimal("0"))
