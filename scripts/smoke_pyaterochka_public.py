from __future__ import annotations

import asyncio
import os

from pyaterochka_mcp.client import PyaterochkaClient
from pyaterochka_mcp.config import Config, Location


async def main() -> None:
    longitude = float(os.environ.get("LONGITUDE") or "37.6176")
    latitude = float(os.environ.get("LATITUDE") or "55.7558")
    query = os.environ.get("QUERY") or "молоко"

    config = Config(
        location=Location(lat=latitude, lon=longitude),
        mode="delivery",
        transport="http",
    )

    async with PyaterochkaClient(config) as client:
        store = await client.find_store(latitude, longitude)
        store_id = store.get("sap_code") or store.get("store_id")
        if not store_id:
            raise RuntimeError(f"No store returned for public test point: {store!r}")

        config.store_id = str(store_id)
        products = await client.search(query, limit=5)
        available = [product for product in products if product.get("in_stock")]

        print(f"Resolved store: {store_id}")
        print(f"Store name: {store.get('name') or ''}")
        print(f"Query: {query!r}")
        print(f"Products returned: {len(products)}")
        print(f"Available products: {len(available)}")
        for product in products:
            print(
                f"[{product.get('id')}] {product.get('title')} | "
                f"{product.get('price')} RUB | "
                f"in_stock={product.get('in_stock')}"
            )

        if not products:
            raise RuntimeError("Live catalogue search returned no products.")


if __name__ == "__main__":
    asyncio.run(main())
