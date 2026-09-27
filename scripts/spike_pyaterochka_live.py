from __future__ import annotations

import argparse
import asyncio
from decimal import Decimal


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Pyaterochka Technical Spike 0.1")
    parser.add_argument("--store-id", help="Pyaterochka SAP/store id")
    parser.add_argument("--query", default="молоко")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--headless", action="store_true")
    parser.add_argument(
        "--add-product-id",
        help="Explicitly add this product id to the authenticated real cart",
    )
    parser.add_argument("--quantity", type=Decimal, default=Decimal("1"))
    return parser.parse_args()


async def main() -> None:
    args = parse_args()

    try:
        from pyaterochka_api import PyaterochkaAPI
    except ImportError as exc:
        raise SystemExit('Install live dependencies first: pip install -e ".[pyaterochka]"') from exc

    from grocery_optimizer.providers.pyaterochka.catalog import PyaterochkaCatalog

    async with PyaterochkaAPI(headless=args.headless) as api:
        store_id = args.store_id
        if not store_id:
            store_info = await api.delivery_panel_store()
            selected = store_info.get("selectedStore", {}) if isinstance(store_info, dict) else {}
            store_id = selected.get("sapCode")
        if not store_id:
            raise SystemExit(
                "No store id available. Select a delivery store on 5ka.ru or pass --store-id."
            )

        catalog = PyaterochkaCatalog(api, store_id=str(store_id))
        products = await catalog.search(args.query, limit=args.limit)

        print(f"Store: {store_id}")
        print(f"Query: {args.query!r}")
        for index, product in enumerate(products, start=1):
            stock = "in stock" if product.in_stock else "out of stock"
            regular = (
                f" (regular {product.regular_price} RUB)"
                if product.regular_price is not None and product.regular_price != product.price
                else ""
            )
            print(
                f"{index:>2}. [{product.product_id}] {product.name} — "
                f"{product.price} RUB{regular}; {product.size or ''} {stock}"
            )

    if not args.add_product_id:
        print("\nRead-only catalogue test completed. Cart was not changed.")
        return

    try:
        from pyaterochka_mcp.client import PyaterochkaClient
        from pyaterochka_mcp.config import load_config
    except ImportError as exc:
        raise SystemExit('Cart driver missing. Install: pip install -e ".[pyaterochka]"') from exc

    from grocery_optimizer.providers.pyaterochka.cart import PyaterochkaCart

    config = load_config()
    config.store_id = str(store_id)
    async with PyaterochkaClient(config) as client:
        cart_driver = PyaterochkaCart(client)
        before = await cart_driver.get()
        print(f"\nCart before: {before.total_cost} RUB, {before.total_quantity} items")

        after = await cart_driver.add(args.add_product_id, args.quantity)
        print(f"Cart after:  {after.total_cost} RUB, {after.total_quantity} items")

        matching = [item for item in after.items if item.product_id == str(args.add_product_id)]
        if not matching:
            raise SystemExit("Cart write did not verify: product is absent after reread.")

        print(
            f"Verified product {args.add_product_id} in cart; "
            f"quantity={matching[0].quantity}. No checkout was performed."
        )


if __name__ == "__main__":
    asyncio.run(main())
