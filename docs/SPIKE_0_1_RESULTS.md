# Technical Spike 0.1 — interim results

Date: 2026-09-27

## What was tested

A read-only Pyaterochka catalogue smoke test was run from a clean GitHub Actions runner
against a public Moscow test point. No user account, cookies, address, cart or payment data
were used.

## Results

### 1. Open-Inflation browser warmup

The Open-Inflation `pyaterochka_api` stack installed successfully after pinning the
compatible `human-requests` revision.

On a GitHub runner, its browser warmup reached `https://5ka.ru/` but the application
never exposed the expected `#app` element. The navigation passed through X5 ID and the
warmup timed out.

Conclusion: this browser-warmup approach is not reliable in a headless cloud CI runner.

### 2. Direct Pyaterochka HTTP catalogue route

The `pyaterochka-mcp` direct HTTP client was then tested without account/session data.
The first store-discovery request:

`GET https://5d.5ka.ru/api/orders/v1/orders/stores/`

returned HTTP 403.

Conclusion: the current X5 catalogue path cannot be treated as an anonymous cloud API.
A valid browser/session context is required even for store discovery from this environment.

## Architectural consequence

Pyaterochka must currently be classified as:

- search: **session/browser required**
- basket: **session/browser required**
- checkout: **session/browser required**

The application should therefore run the X5 connector near the user's authenticated browser
(or import a locally captured session) rather than scrape X5 anonymously from a central
cloud worker.

This does not invalidate the product architecture. It changes the execution location of the
X5 provider.

## Next experiment

Run the existing local Spike 0.1 script with the user's own browser/session context:

1. capture/import the local Pyaterochka session;
2. select a concrete delivery store;
3. search a harmless grocery item;
4. read the current real cart;
5. explicitly add one selected SKU;
6. reread and verify the cart;
7. do not checkout or pay.

If this succeeds, the core feasibility question for Pyaterochka is answered.
