from __future__ import annotations

from typing import Any

from grocery_optimizer.models import StoreProduct
from grocery_optimizer.providers.pyaterochka.normalize import product_from_api


class PyaterochkaCatalog:
    """Thin read adapter over Open-Inflation/pyaterochka_api."""

    def __init__(self, api: Any, *, store_id: str):
        self.api = api
        self.store_id = str(store_id)

    async def search(self, query: str, *, limit: int = 20) -> list[StoreProduct]:
        response = await self.api.Catalog.search(
            sap_code_store_id=self.store_id,
            query=query,
            limit=limit,
        )
        payload = response.json()
        products = payload.get("products", []) if isinstance(payload, dict) else []
        result: list[StoreProduct] = []
        for raw in products:
            if not isinstance(raw, dict):
                continue
            try:
                result.append(product_from_api(raw, store_id=self.store_id))
            except ValueError:
                continue
        return result[:limit]
