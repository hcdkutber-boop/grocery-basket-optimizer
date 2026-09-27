from __future__ import annotations

from decimal import Decimal, InvalidOperation
from typing import Any

from grocery_optimizer.models import Basket, BasketItem, StoreProduct


def _decimal(value: Any) -> Decimal | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        return Decimal(str(value).replace("\xa0", "").replace(" ", "").replace(",", "."))
    except (InvalidOperation, ValueError):
        return None


def product_from_api(raw: dict[str, Any], *, store_id: str) -> StoreProduct:
    prices = raw.get("prices") if isinstance(raw.get("prices"), dict) else {}
    regular = _decimal(prices.get("regular"))
    discount = _decimal(prices.get("discount"))
    cpd_promo = _decimal(prices.get("cpd_promo_price"))
    price = discount or cpd_promo or regular
    if price is None:
        raise ValueError(f"Product {raw.get('plu')!r} has no usable price")

    images = raw.get("image_links") if isinstance(raw.get("image_links"), dict) else {}
    normal_images = images.get("normal") if isinstance(images.get("normal"), list) else []
    small_images = images.get("small") if isinstance(images.get("small"), list) else []
    rating_data = raw.get("rating") if isinstance(raw.get("rating"), dict) else {}

    stock_limit = _decimal(raw.get("stock_limit"))
    in_stock = bool(raw.get("is_available", True))
    if stock_limit is not None and stock_limit <= 0:
        in_stock = False

    clarification = raw.get("property_clarification")
    return StoreProduct(
        provider="pyaterochka",
        store_id=str(store_id),
        product_id=str(raw.get("plu") or raw.get("id") or ""),
        name=str(raw.get("name") or ""),
        price=price,
        regular_price=regular,
        size=str(clarification) if clarification else None,
        unit=str(raw.get("uom")) if raw.get("uom") else None,
        in_stock=in_stock,
        stock_limit=stock_limit,
        rating=_decimal(rating_data.get("rating_average")),
        image_url=(normal_images or small_images or [None])[0],
        raw=dict(raw),
    )


def basket_from_mcp(raw: dict[str, Any]) -> Basket:
    items: list[BasketItem] = []
    for item in raw.get("items") or []:
        if not isinstance(item, dict):
            continue
        quantity = _decimal(item.get("quantity")) or Decimal("0")
        unit_price = _decimal(item.get("unit_price") or item.get("price"))
        total_price = _decimal(item.get("total_price") or item.get("sum"))
        items.append(
            BasketItem(
                product_id=str(item.get("id") or item.get("product_id") or ""),
                name=str(item.get("title") or item.get("name") or ""),
                quantity=quantity,
                unit_price=unit_price,
                total_price=total_price,
            )
        )

    total = _decimal(raw.get("total_cost") or raw.get("total_sum") or raw.get("total"))
    if total is None:
        total = sum(
            (item.total_price or Decimal("0") for item in items),
            start=Decimal("0"),
        )

    return Basket(
        provider="pyaterochka",
        items=tuple(items),
        total_cost=total,
        amount_to_minimum=_decimal(raw.get("amount_to_minimum")),
    )
