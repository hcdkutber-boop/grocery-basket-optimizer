from __future__ import annotations

from abc import ABC, abstractmethod
from decimal import Decimal

from grocery_optimizer.models import Basket, Capability, StoreProduct


class StoreProvider(ABC):
    id: str
    capabilities: frozenset[Capability]

    def supports(self, capability: Capability) -> bool:
        return capability in self.capabilities

    @abstractmethod
    async def search(self, query: str, *, limit: int = 20) -> list[StoreProduct]:
        raise NotImplementedError

    async def get_cart(self) -> Basket:
        raise NotImplementedError(f"{self.id} does not support baskets")

    async def add_to_cart(self, product_id: str, quantity: Decimal = Decimal("1")) -> Basket:
        raise NotImplementedError(f"{self.id} does not support baskets")
