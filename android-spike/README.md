# Android Spike 0.1

Purpose: verify that an authenticated Android WebView can access the Pyaterochka catalogue
from the same browser/session context that the X5 site uses.

## Scope

The APK currently supports:

1. opening 5ka.ru inside an isolated WebView;
2. X5 ID login performed by the user;
3. reading the selected store from 5ka.ru localStorage;
4. observing selected X5 request headers in memory without printing their values;
5. executing a read-only catalogue search from the authenticated 5ka.ru page;
6. showing returned product ids, names and prices.

The APK does **not** modify the basket, perform checkout, select payment methods or place orders.

## Test flow

1. Install the debug APK.
2. Open it and sign in to X5 ID in the embedded page.
3. On 5ka.ru select your normal delivery address/store.
4. Tap **Определить магазин**.
5. Enter a product query such as **молоко**.
6. Tap **Найти**.
7. Record whether the API returns products or an HTTP/CORS error.

No session secrets should be copied into chat or committed to GitHub.
