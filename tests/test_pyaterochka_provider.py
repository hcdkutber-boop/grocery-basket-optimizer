from decimal import Decimal

import pytest

from grocery_optimizer.providers.pyaterochka.provider import PyaterochkaProvider


class FakeResponse:
    def json(self):
        return {
            "products": [
                {
                    "plu": 7,
                    "name": "Молоко тестовое 1л",
                    "prices": {"regular": "100.00", "discount": "80.00"},
                    "is_available": True,
                    "stock_limit": "3.00",
                }
            ]
        }


class FakeCatalog:
    async def search(self, **kwargs):
        assert kwargs["sap_code_store_id"] == "store-1"
        assert kwargs["query"] == "молоко"
        return FakeResponse()


class FakeCatalogAPI:
    Catalog = FakeCatalog()


class FakeCartClient:
    async def get_cart(self):
        return {"items": [], "total_sum": "0"}

    async def add_to_cart(self, product_id, quantity):
        assert product_id == "7"
        assert quantity == 1.0
        return {
            "items": [
                {
                    "id": "7",
                    "title": "Молоко тестовое 1л",
                    "quantity": 1,
                    "unit_price": "80.00",
                    "total_price": "80.00",
                }
            ],
            "total_sum": "80.00",
        }


@pytest.mark.asyncio
async def test_provider_end_to_end_contract():
    provider = PyaterochkaProvider(
        store_id="store-1",
        catalog_api=FakeCatalogAPI(),
        cart_client=FakeCartClient(),
    )

    products = await provider.search("молоко")
    assert len(products) == 1
    assert products[0].price == Decimal("80.00")

    cart = await provider.add_to_cart(products[0].product_id)
    assert cart.total_cost == Decimal("80.00")
    assert cart.items[0].product_id == "7"
