# Technical Spike 0.1 — Pyaterochka end-to-end

## Question

Can this application discover live products in a selected Pyaterochka store and populate the same real basket the user sees in their account?

## Acceptance test

1. Start a catalogue session for Pyaterochka.
2. Resolve/select one concrete store and obtain its SAP/store id.
3. Search for a benign grocery query, e.g. `молоко`.
4. Normalize at least one available product into `StoreProduct`.
5. Start an authenticated cart client using the user's browser/session data.
6. Read the existing basket.
7. Add exactly one selected SKU.
8. Read the basket again.
9. Verify the SKU and quantity are present.
10. Record the actual basket total returned by the retailer.

## Guardrails

- Do not submit or pay for an order.
- Do not log cookies, authorization headers, payment tokens, or full address data.
- Cart writes must be serialized; an ambiguous write must be verified by rereading the cart before retrying.
- Test with a low-value normal grocery item.
- Keep retailer-specific session material outside the repository.

## Upstream references

- Open-Inflation/pyaterochka_api — catalogue and store endpoints.
- shi-kirill/pyaterochka-mcp — authenticated basket workflow.
- abracadabra50/open-supermarkets — capability/provider architecture reference.

All three were inspected before the initial project structure was created.
