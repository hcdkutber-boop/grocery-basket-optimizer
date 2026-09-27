from decimal import Decimal

from grocery_optimizer.providers.pyaterochka.normalize import (
    basket_from_mcp,
    product_from_api,
)


def test_product_normalization_uses_discount_price():
    product = product_from_api(
        {
            "plu": 4429993,
            "name": "Напиток",
            "uom": "шт",
            "property_clarification": "1 л",
            "prices": {"regular": "89.99", "discount": "69.99", "cpd_promo_price": None},
            "is_available": True,
            "stock_limit": "5.00",
            "rating": {"rating_average": 4.85},
            "image_links": {"normal": ["https://example.test/item.jpg"]},
        },
        store_id="1234",
    )

    assert product.product_id == "4429993"
    assert product.price == Decimal("69.99")
    assert product.regular_price == Decimal("89.99")
    assert product.in_stock is True
    assert product.size == "1 л"


def test_zero_stock_is_not_in_stock():
    product = product_from_api(
        {
            "plu": 1,
            "name": "Товар",
            "prices": {"regular": "10"},
            "is_available": True,
            "stock_limit": "0.00",
        },
        store_id="1234",
    )
    assert product.in_stock is False


def test_cart_normalization():
    cart = basket_from_mcp(
        {
            "items": [
                {
                    "id": "4429993",
                    "title": "Напиток",
                    "quantity": 2,
                    "unit_price": "69.99",
                    "total_price": "139.98",
                }
            ],
            "total_sum": "139.98",
        }
    )
    assert cart.total_cost == Decimal("139.98")
    assert cart.total_quantity == Decimal("2")
    assert cart.items[0].product_id == "4429993"
