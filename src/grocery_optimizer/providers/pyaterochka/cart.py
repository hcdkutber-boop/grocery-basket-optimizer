from __future__ import annotations

from decimal import Decimal
from typing import Any

from grocery_optimizer.models import Basket
from grocery_optimizer.providers.pyaterochka.normalize import basket_from_mcp


class PyaterochkaCart:
    """Write adapter over shi-kirill/pyaterochka-mcp client."""

    def __init__(self, client: Any):
        self.client = client

    async def get(self) -> Basket:
        return basket_from_mcp(await self.client.get_cart())

    async def add(self, product_id: str, quantity: Decimal = Decimal("1")) -> Basket:
        raw = await self.client.add_to_cart(product_id, float(quantity))
        return basket_from_mcp(raw)
