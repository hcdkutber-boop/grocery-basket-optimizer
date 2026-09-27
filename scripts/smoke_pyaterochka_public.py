from __future__ import annotations

import asyncio
import os

from grocery_optimizer.providers.pyaterochka.catalog import PyaterochkaCatalog


async def main() -> None:
    from pyaterochka_api import PyaterochkaAPI

    longitude = float(os.environ.get("LONGITUDE", "37.6176"))
    latitude = float(os.environ.get("LATITUDE", "55.7558"))
    query = os.environ.get("QUERY", "молоко")

    async with PyaterochkaAPI(headless=True) as api:
        stores_response = await api.Geolocation.find_store(
            longitude=longitude,
            latitude=latitude,
        )
        stores_payload = stores_response.json()

        if isinstance(stores_payload, dict):
            candidates = (
                stores_payload.get("stores")
                or stores_payload.get("items")
                or stores_payload.get("results")
                or []
            )
        elif isinstance(stores_payload, list):
            candidates = stores_payload
        else:
            candidates = []

        if not candidates:
            raise RuntimeError(
                f"No stores returned for public test point: {stores_payload!r}"
            )

        first = candidates[0]
        if not isinstance(first, dict):
            raise RuntimeError(f"Unexpected store payload: {first!r}")

        store_id = (
            first.get("sap_code")
            or first.get("sapCode")
            or first.get("store_id")
            or first.get("id")
        )
        if not store_id:
            raise RuntimeError(f"Store has no recognizable id: {first!r}")

        catalog = PyaterochkaCatalog(api, store_id=str(store_id))
        products = await catalog.search(query, limit=5)
        available = [product for product in products if product.in_stock]

        print(f"Resolved store: {store_id}")
        print(f"Query: {query!r}")
        print(f"Products returned: {len(products)}")
        print(f"Available products: {len(available)}")
        for product in products:
            print(
                f"[{product.product_id}] {product.name} | "
                f"{product.price} RUB | in_stock={product.in_stock}"
            )

        if not products:
            raise RuntimeError("Live catalogue search returned no products.")


if __name__ == "__main__":
    asyncio.run(main())
