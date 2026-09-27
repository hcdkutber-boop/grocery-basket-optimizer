# Architecture

## Principle

Retailer-specific code is isolated behind provider adapters. The optimizer never depends directly on a retailer API response shape.

## Layers

1. **Retailer drivers** — unofficial or official clients speaking to retailer endpoints.
2. **Provider adapters** — convert retailer-specific responses into common models.
3. **Product normalization** — later maps several store SKUs onto canonical products.
4. **Optimizer** — later allocates requirements across stores.
5. **Application/UI** — later presents baskets and asks for explicit confirmation.

## Core entities

- `StoreProduct`: concrete SKU in a concrete store.
- `Basket`: real current retailer cart.
- `CanonicalProduct`: planned entity for cross-store identity.
- `ProductRequirement`: planned entity for user constraints.

## Capability model

A provider advertises each feature separately:

- search
- basket
- slots
- checkout
- orders

This prevents the system from pretending a partially reverse-engineered retailer supports operations it does not.

## Current Pyaterochka composition

- Read/catalogue path: Open-Inflation `pyaterochka_api`.
- Basket path: `pyaterochka-mcp`.
- Common model: this repository.

The upstream integrations remain replaceable. Their data is normalized at the adapter boundary.
