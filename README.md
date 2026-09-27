# Grocery Basket Optimizer

Technical prototype for comparing grocery prices across stores and building real shopping carts.

## Goal

The first milestone (Technical Spike 0.1) proves one end-to-end path for Pyaterochka:

1. select a concrete store;
2. search the live catalogue;
3. normalize a store SKU into a common product model;
4. add the selected SKU to the user's real cart;
5. read the cart back;
6. verify the actual cart total.

No automatic payment or order submission is part of Spike 0.1.

## Architecture

```text
User request
    |
    v
Product requirement
    |
    v
Shopping service
    |
    +--> Store provider (capabilities)
            |
            +--> catalogue driver
            +--> cart driver
    |
    v
Normalized StoreProduct / Cart
```

A provider declares capabilities independently: `search`, `basket`, `slots`, `checkout`, `orders`.
This lets catalogue-only integrations coexist with stores where a real account/cart integration is available.

## Initial integrations

- Pyaterochka catalogue: adapter around Open-Inflation/pyaterochka_api.
- Pyaterochka cart: adapter around shi-kirill/pyaterochka-mcp.
- Perekrestok and Chizhik catalogue adapters are planned next.

These upstream projects are unofficial and can break when retailer APIs change.

## Safety boundary

The optimizer may search, compare and populate carts. It must not place or pay for an order without an explicit user confirmation at the final step.

## Development status

Current phase: **Technical Spike 0.1**

Acceptance criterion:

> A search query returns real products from a chosen Pyaterochka store, one product can be added to the user's real cart, and the application reads back the resulting cart and actual total.

## Local development

Python 3.12+.

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
pytest
```

Live retailer drivers are optional and installed separately:

```bash
pip install -e ".[pyaterochka]"
```

## Repository layout

```text
src/grocery_optimizer/
  models.py
  providers/
    base.py
    pyaterochka/
      catalog.py
      cart.py
tests/
docs/
```
