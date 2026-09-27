# Running the live Pyaterochka spike

The live spike runs on the user's machine because retailer session/authentication data must stay local.

## 1. Install

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e ".[pyaterochka,dev]"
python -m camoufox fetch
```

On Windows PowerShell use `.venv\Scripts\Activate.ps1`.

## 2. Read-only catalogue test

If a delivery store is already selected in the browser session used by the catalogue driver:

```bash
python scripts/spike_pyaterochka_live.py --query "молоко"
```

Or pass the SAP/store id explicitly:

```bash
python scripts/spike_pyaterochka_live.py --store-id STORE_ID --query "молоко"
```

Expected output: product id, name, current price, regular price where available, pack size and stock state.

## 3. Configure authenticated cart access

The cart layer uses the local configuration/session mechanism from `pyaterochka-mcp`.
Capture/import the browser session using that project's local tools. Never paste cookies,
authorization headers or payment/session tokens into GitHub issues or commits.

The configuration stays in the user's local config directory and is ignored by this repository.

## 4. Explicit cart write

First run read-only and choose a low-value ordinary grocery SKU. Then:

```bash
python scripts/spike_pyaterochka_live.py \
  --store-id STORE_ID \
  --query "молоко" \
  --add-product-id PRODUCT_ID \
  --quantity 1
```

The script:

1. reads the current cart;
2. adds exactly the explicitly supplied product;
3. reads the resulting cart state;
4. verifies that the product is present;
5. prints the actual total.

It does **not** run checkout and does **not** pay for or submit an order.

## Spike 0.1 success

The spike succeeds when the same product added by the script is visible in the user's normal
Pyaterochka cart with matching quantity and the returned total matches the retailer cart.
