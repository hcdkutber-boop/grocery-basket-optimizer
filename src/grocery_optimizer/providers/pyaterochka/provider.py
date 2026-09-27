from __future__ import annotations

from decimal import Decimal
from typing import Any

from grocery_optimizer.models import Basket, Capability, StoreProduct
from grocery_optimizer.providers.base import StoreProvider
from grocery_optimizer.providers.pyaterochka.cart import PyaterochkaCart
from grocery_optimizer.providers.pyaterochka.catalog import PyaterochkaCatalog


class PyaterochkaProvider(StoreProvider):
    id = "pyaterochka"
    capabilities = frozenset({Capability.SEARCH, Capability.BASKET})

    def __init__(self, *, store_id: str, catalog_api: Any, cart_client: Any | None = None):
        self.store_id = str(store_id)
        self.catalog = PyaterochkaCatalog(catalog_api, store_id=self.store_id)
        self.cart = PyaterochkaCart(cart_client) if cart_client is not None else None

    async def search(self, query: str, *, limit: int = 20) -> list[StoreProduct]:
        return await self.catalog.search(query, limit=limit)

    async def get_cart(self) -> Basket:
        if self.cart is None:
            raise RuntimeError("Pyaterochka cart client is not configured")
        return await self.cart.get()

    async def add_to_cart(self, product_id: str, quantity: Decimal = Decimal("1")) -> Basket:
        if self.cart is None:
            raise RuntimeError("Pyaterochka cart client is not configured")
        return await self.cart.add(product_id, quantity)
