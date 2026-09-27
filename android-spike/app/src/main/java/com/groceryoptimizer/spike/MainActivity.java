package com.groceryoptimizer.spike;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://5ka.ru/";
    private static final String API_ORIGIN = "https://5d.5ka.ru";
    private static final String SEARCH_BASE =
            API_ORIGIN + "/api/catalog/v3/stores/";
    private static final String CART_LIST_URL =
            API_ORIGIN + "/api/orders/v3/orders/?in_action=true";
    private static final String CART_GET_BASE =
            API_ORIGIN + "/api/orders/v10/orders/";

    private final Map<String, String> capturedHeaders = new ConcurrentHashMap<>();

    private WebView webView;
    private WebView apiWebView;
    private TextView statusView;
    private TextView logView;
    private EditText queryView;
    private String storeId;
    private String pendingApiAction = "";
    private String currentCartId = "";
    private int currentCartItemCount = -1;
    private String selectedProductId = "";
    private String selectedProductUom = "";
    private String selectedProductName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10);
        root.setPadding(pad, pad, pad, pad);

        statusView = new TextView(this);
        statusView.setText("Статус: откройте Пятёрочку и войдите в X5 ID");
        statusView.setTextSize(16f);
        root.addView(statusView, matchWrap());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button openButton = new Button(this);
        openButton.setText("Открыть Пятёрочку");
        openButton.setOnClickListener(v -> webView.loadUrl(HOME_URL));
        actionRow.addView(openButton, weightWrap());

        Button storeButton = new Button(this);
        storeButton.setText("Определить магазин");
        storeButton.setOnClickListener(v -> readSelectedStore());
        actionRow.addView(storeButton, weightWrap());

        root.addView(actionRow, matchWrap());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);

        queryView = new EditText(this);
        queryView.setHint("например: молоко");
        queryView.setText("молоко");
        queryView.setSingleLine(true);
        queryView.setInputType(InputType.TYPE_CLASS_TEXT);
        searchRow.addView(queryView, weightWrap());

        Button searchButton = new Button(this);
        searchButton.setText("Найти");
        searchButton.setOnClickListener(v -> searchProducts());
        searchRow.addView(searchButton, wrapWrap());

        root.addView(searchRow, matchWrap());

        Button cartButton = new Button(this);
        cartButton.setText("Прочитать корзину");
        cartButton.setOnClickListener(v -> readCart());
        root.addView(cartButton, matchWrap());

        Button addButton = new Button(this);
        addButton.setText("Добавить первый найденный товар — 1 шт.");
        addButton.setOnClickListener(v -> addSelectedProduct());
        root.addView(addButton, matchWrap());

        logView = new TextView(this);
        logView.setTextSize(13f);
        logView.setTextColor(Color.DKGRAY);
        logView.setTextIsSelectable(true);

        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(logView, matchWrap());
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
        root.addView(logScroll, logParams);

        webView = new WebView(this);
        configureWebView();
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(webView, webParams);

        apiWebView = new WebView(this);
        configureApiWebView();

        setContentView(root);
        appendLog("APK 0.6-mobile-spike. Тест добавления 1 SKU; checkout отключён.");
        webView.loadUrl(HOME_URL);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host == null) {
                    return true;
                }
                String normalized = host.toLowerCase();
                boolean allowed = normalized.equals("5ka.ru")
                        || normalized.endsWith(".5ka.ru")
                        || normalized.equals("x5.ru")
                        || normalized.endsWith(".x5.ru");
                if (!allowed) {
                    appendLog("Заблокирована внешняя навигация: " + host);
                }
                return !allowed;
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                captureUsefulHeaders(request);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                statusView.setText("Страница: " + (host == null ? url : host));
                if (host != null && host.endsWith("5ka.ru")) {
                    appendLog("5ka.ru загружена. После входа выберите адрес/магазин на сайте.");
                }
            }
        });
    }

    private void configureApiWebView() {
        WebSettings settings = apiWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(apiWebView, true);

        apiWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                String host = request.getUrl().getHost();
                return host == null || !host.endsWith("5ka.ru");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host == null || !host.equalsIgnoreCase("5d.5ka.ru")) {
                    return;
                }

                String js = "(() => document.body ? document.body.innerText : " +
                        "document.documentElement.innerText)()";
                view.evaluateJavascript(js, value -> {
                    String decoded = decodeJavascriptString(value);
                    if (decoded == null || decoded.isBlank()) {
                        appendLog("Браузерный API-транспорт вернул пустой ответ.");
                        return;
                    }
                    handleApiBody(decoded);
                });
            }
        });
    }

    private void captureUsefulHeaders(WebResourceRequest request) {
        String host = request.getUrl().getHost();
        if (host == null || !host.endsWith("5ka.ru")) {
            return;
        }

        for (Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (isUsefulHeader(key) && entry.getValue() != null) {
                capturedHeaders.put(key, entry.getValue());
            }
        }
    }

    private boolean isUsefulHeader(String key) {
        return key.equals("authorization")
                || key.equals("x-authorization")
                || key.equals("x-session-id")
                || key.equals("x-tenant-id")
                || key.equals("x-capabilities")
                || key.equals("x-attributes")
                || key.equals("x-attributes-package")
                || key.equals("x-app-version")
                || key.equals("x-device-id")
                || key.equals("x-platform")
                || key.equals("x-user-segments");
    }

    private void readSelectedStore() {
        if (!isOnFiveka()) {
            appendLog("Сначала вернитесь на 5ka.ru после авторизации.");
            return;
        }

        String js = "(() => localStorage.getItem('DeliveryPanelStore'))()";
        webView.evaluateJavascript(js, value -> {
            String decoded = decodeJavascriptString(value);
            if (decoded == null || decoded.isBlank() || "null".equals(decoded)) {
                appendLog("DeliveryPanelStore не найден. Выберите адрес/магазин на сайте.");
                return;
            }

            try {
                JSONObject root = new JSONObject(decoded);
                JSONObject selected = root.optJSONObject("selectedStore");
                if (selected == null) {
                    appendLog("В localStorage нет selectedStore. Выберите магазин.");
                    return;
                }

                String id = selected.optString("sapCode", "");
                if (id.isBlank()) {
                    id = selected.optString("sap_code", "");
                }
                if (id.isBlank()) {
                    id = selected.optString("id", "");
                }
                if (id.isBlank()) {
                    appendLog("Не удалось определить SAP/store ID.");
                    return;
                }

                storeId = id;
                String name = selected.optString("name", "");
                statusView.setText("Магазин: " + storeId + (name.isBlank() ? "" : " — " + name));
                appendLog("Магазин определён: " + storeId);
                appendLog("Захвачено служебных заголовков X5: " + capturedHeaders.size());

                String cookie = CookieManager.getInstance().getCookie(API_ORIGIN + "/");
                appendLog("Cookies для API: " + (cookie == null || cookie.isBlank() ? "нет" : "есть"));
            } catch (JSONException error) {
                appendLog("Не удалось разобрать DeliveryPanelStore: " + error.getMessage());
            }
        });
    }

    private void searchProducts() {
        if (storeId == null || storeId.isBlank()) {
            appendLog("Сначала нажмите «Определить магазин».");
            return;
        }

        String query = queryView.getText().toString().trim();
        if (query.isBlank()) {
            appendLog("Введите запрос.");
            return;
        }

        String endpoint = SEARCH_BASE + Uri.encode(storeId)
                + "/search?mode=delivery&include_restrict=true&q="
                + Uri.encode(query) + "&limit=10";

        Map<String, String> requestHeaders = new HashMap<>(capturedHeaders);
        requestHeaders.put("Accept", "application/json, text/plain, */*");
        requestHeaders.put("Referer", HOME_URL);

        pendingApiAction = "search";
        appendLog("Браузерный поиск: «" + query + "»…");
        apiWebView.loadUrl(endpoint, requestHeaders);
    }

    private void handleApiBody(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")) {
            appendLog("Браузерный API-транспорт получил HTML вместо JSON.");
            appendLog(trimmed.substring(0, Math.min(trimmed.length(), 240)));
            return;
        }

        if ("cart_list".equals(pendingApiAction)) {
            handleCartListBody(trimmed);
            return;
        }
        if ("cart_get".equals(pendingApiAction)) {
            handleCartGetBody(trimmed);
            return;
        }
        if ("cart_add".equals(pendingApiAction)) {
            handleCartAddBody(trimmed);
            return;
        }

        try {
            JSONObject payload = new JSONObject(trimmed);
            JSONArray products = payload.optJSONArray("products");
            if (products == null) {
                appendLog("JSON получен, но поле products не найдено.");
                String preview = trimmed.substring(0, Math.min(trimmed.length(), 300));
                appendLog("Ответ: " + preview);
                return;
            }

            appendLog("JSON получен. Найдено товаров: " + products.length());

            if (products.length() > 0) {
                JSONObject first = products.optJSONObject(0);
                if (first != null) {
                    selectedProductId = firstNonBlank(
                            first.optString("plu", ""),
                            first.optString("id", "")
                    );
                    selectedProductUom = firstNonBlank(
                            first.optString("uom", ""),
                            first.optString("unit", "")
                    );
                    selectedProductName = first.optString("name", "Без названия");
                    if (!selectedProductId.isBlank()) {
                        appendLog(
                                "Выбран для теста первый SKU: ["
                                        + selectedProductId + "] " + selectedProductName
                        );
                    }
                }
            }

            int shown = Math.min(products.length(), 10);
            for (int i = 0; i < shown; i++) {
                JSONObject product = products.optJSONObject(i);
                if (product == null) {
                    continue;
                }
                String id = product.optString("plu", product.optString("id", "?"));
                String name = product.optString("name", "Без названия");
                JSONObject prices = product.optJSONObject("prices");
                String price = priceFrom(prices);
                String size = product.optString("property_clarification", "");
                boolean available = product.optBoolean("is_available", true);
                appendLog(
                        "• [" + id + "] " + name
                                + (size.isBlank() ? "" : " — " + size)
                                + " — " + price + " ₽"
                                + (available ? "" : " [нет в наличии]")
                );
            }
        } catch (JSONException error) {
            appendLog("Ответ браузерного API не удалось разобрать: " + error.getMessage());
            String preview = trimmed.substring(0, Math.min(trimmed.length(), 300));
            appendLog("Ответ: " + preview);
        }
    }

    private void readCart() {
        Map<String, String> requestHeaders = new HashMap<>(capturedHeaders);
        requestHeaders.put("Accept", "application/json, text/plain, */*");
        requestHeaders.put("Referer", HOME_URL);

        pendingApiAction = "cart_list";
        appendLog("Чтение текущей корзины…");
        apiWebView.loadUrl(CART_LIST_URL, requestHeaders);
    }

    private void handleCartListBody(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                data = root;
            }

            JSONArray items = data.optJSONArray("items");
            String cartId = "";

            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }

                    Object status = item.opt("status");
                    if (status == null || status == JSONObject.NULL) {
                        status = item.opt("status_code");
                    }

                    String statusText = status == null || status == JSONObject.NULL
                            ? ""
                            : String.valueOf(status);
                    if ("0".equals(statusText)
                            || "CART".equalsIgnoreCase(statusText)) {
                        cartId = firstNonBlank(
                                item.optString("id", ""),
                                item.optString("order_id", ""),
                                item.optString("cart_id", "")
                        );
                        if (!cartId.isBlank()) {
                            break;
                        }
                    }
                }
            }

            if (cartId.isBlank()) {
                cartId = firstNonBlank(
                        data.optString("id", ""),
                        data.optString("order_id", ""),
                        data.optString("cart_id", "")
                );
            }

            if (cartId.isBlank()) {
                appendLog("Открытая корзина не найдена.");
                return;
            }

            currentCartId = cartId;
            appendLog("Найдена корзина: " + cartId);

            Map<String, String> requestHeaders = new HashMap<>(capturedHeaders);
            requestHeaders.put("Accept", "application/json, text/plain, */*");
            requestHeaders.put("Referer", HOME_URL);

            pendingApiAction = "cart_get";
            apiWebView.loadUrl(
                    CART_GET_BASE + Uri.encode(cartId) + "/",
                    requestHeaders
            );
        } catch (JSONException error) {
            appendLog("Не удалось разобрать список корзин: " + error.getMessage());
            appendLog("Ответ: " + body.substring(0, Math.min(body.length(), 300)));
        }
    }

    private void handleCartGetBody(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                data = root;
            }

            JSONObject cart = data.optJSONObject("cart");
            if (cart == null) {
                cart = data.optJSONObject("basket");
            }
            if (cart == null) {
                cart = data;
            }

            JSONArray items = firstArray(
                    cart.optJSONArray("items"),
                    cart.optJSONArray("products"),
                    cart.optJSONArray("positions"),
                    cart.optJSONArray("lines")
            );

            double total = firstNumber(
                    root,
                    "total", "total_price", "totalPrice", "totalPriceValue",
                    "amount", "final_sum", "total_sum"
            );
            if (Double.isNaN(total)) {
                total = firstNumber(
                        data,
                        "total", "total_price", "totalPrice", "totalPriceValue",
                        "amount", "final_sum", "total_sum"
                );
            }
            if (Double.isNaN(total)) {
                JSONObject summary = data.optJSONObject("full_summary");
                if (summary != null) {
                    total = firstNumber(
                            summary,
                            "total", "final_sum", "total_sum", "amount"
                    );
                }
            }

            int itemCount = items == null ? 0 : items.length();
            currentCartItemCount = itemCount;
            appendLog(
                    "Корзина прочитана. Позиций: " + itemCount
                            + (Double.isNaN(total) ? "" : "; итог: " + total + " ₽")
            );

            if (items == null) {
                return;
            }

            int shown = Math.min(items.length(), 20);
            for (int i = 0; i < shown; i++) {
                JSONObject line = items.optJSONObject(i);
                if (line == null) {
                    continue;
                }
                JSONObject product = line.optJSONObject("product");
                if (product == null) {
                    product = line;
                }

                String id = firstNonBlank(
                        product.optString("plu", ""),
                        product.optString("product_plu", ""),
                        product.optString("product_id", ""),
                        product.optString("sku", ""),
                        product.optString("id", "?")
                );
                String title = firstNonBlank(
                        product.optString("name", ""),
                        product.optString("title", ""),
                        "Без названия"
                );

                Object qty = line.opt("quantity");
                if (qty == null || qty == JSONObject.NULL) {
                    qty = line.opt("count");
                }
                if (qty == null || qty == JSONObject.NULL) {
                    qty = line.opt("qty");
                }
                String quantity = qty == null || qty == JSONObject.NULL
                        ? "1"
                        : String.valueOf(qty);

                appendLog("• [" + id + "] " + title + " × " + quantity);
            }
        } catch (JSONException error) {
            appendLog("Не удалось разобрать корзину: " + error.getMessage());
            appendLog("Ответ: " + body.substring(0, Math.min(body.length(), 300)));
        }
    }

    private void addSelectedProduct() {
        if (selectedProductId.isBlank()) {
            appendLog("Сначала выполните поиск товара.");
            return;
        }
        if (currentCartId.isBlank() || currentCartItemCount < 0) {
            appendLog("Сначала нажмите «Прочитать корзину».");
            return;
        }
        if (currentCartItemCount != 0) {
            appendLog(
                    "Тестовая запись разрешена только для пустой корзины, "
                            + "чтобы случайно не изменить существующий заказ."
            );
            return;
        }
        if (selectedProductUom.isBlank()) {
            appendLog("У выбранного SKU не найден uom; тест записи отменён.");
            return;
        }

        String endpoint = API_ORIGIN + "/api/orders/v8/orders/"
                + Uri.encode(currentCartId) + "/item/";

        JSONObject body = new JSONObject();
        try {
            body.put("plu", selectedProductId);
            body.put("qty", 1);
            body.put("uom", selectedProductUom);
        } catch (JSONException error) {
            appendLog("Не удалось собрать тело запроса: " + error.getMessage());
            return;
        }

        JSONObject headers = new JSONObject();
        try {
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json, text/plain, */*");
            for (Map.Entry<String, String> entry : capturedHeaders.entrySet()) {
                headers.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException ignored) {
        }

        String js = "(async()=>{try{"
                + "const r=await fetch(" + JSONObject.quote(endpoint) + ",{"
                + "method:'POST',credentials:'include',"
                + "headers:" + headers + ","
                + "body:" + JSONObject.quote(body.toString())
                + "});"
                + "const t=await r.text();"
                + "return JSON.stringify({status:r.status,ok:r.ok,body:t});"
                + "}catch(e){return JSON.stringify({status:0,ok:false,error:String(e)});}})()";

        pendingApiAction = "cart_add";
        appendLog(
                "Добавление 1 шт.: [" + selectedProductId + "] "
                        + selectedProductName + "…"
        );

        // apiWebView is already on 5d.5ka.ru after catalogue/cart reads,
        // so this fetch is same-origin and uses the accepted browser network stack.
        apiWebView.evaluateJavascript(js, value -> {
            String decoded = decodeJavascriptString(value);
            if (decoded == null) {
                appendLog("Пустой ответ операции добавления. Запись не повторяем.");
                readCart();
                return;
            }
            handleCartAddEnvelope(decoded);
        });
    }

    private void handleCartAddEnvelope(String decoded) {
        try {
            JSONObject envelope = new JSONObject(decoded);
            int status = envelope.optInt("status", 0);
            boolean ok = envelope.optBoolean("ok", false);
            appendLog("POST cart_add: HTTP " + status);

            if (!ok) {
                String error = envelope.optString("error", "");
                if (!error.isBlank()) {
                    appendLog("Ошибка: " + error);
                }
                String responseBody = envelope.optString("body", "");
                if (!responseBody.isBlank()) {
                    appendLog(
                            "Ответ: "
                                    + responseBody.substring(
                                            0, Math.min(responseBody.length(), 240)
                                    )
                    );
                }
                appendLog("Запись не повторяем. Проверяем фактическую корзину.");
                readCart();
                return;
            }

            String responseBody = envelope.optString("body", "");
            handleCartAddBody(responseBody);
        } catch (JSONException error) {
            appendLog("Не удалось разобрать ответ POST: " + error.getMessage());
            appendLog("Запись не повторяем. Проверяем корзину.");
            readCart();
        }
    }

    private void handleCartAddBody(String body) {
        appendLog("X5 принял запрос добавления. Перечитываем корзину для проверки.");
        readCart();
    }

    private JSONArray firstArray(JSONArray... values) {
        for (JSONArray value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private double firstNumber(JSONObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) {
                continue;
            }
            Object value = object.opt(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value).replace(",", "."));
            } catch (NumberFormatException ignored) {
            }
        }
        return Double.NaN;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String priceFrom(JSONObject prices) {
        if (prices == null) {
            return "?";
        }
        Object discount = prices.opt("discount");
        if (discount != null && discount != JSONObject.NULL) {
            return String.valueOf(discount);
        }
        Object promo = prices.opt("cpd_promo_price");
        if (promo != null && promo != JSONObject.NULL) {
            return String.valueOf(promo);
        }
        Object regular = prices.opt("regular");
        return regular == null || regular == JSONObject.NULL ? "?" : String.valueOf(regular);
    }

    private boolean isOnFiveka() {
        String url = webView.getUrl();
        if (url == null) {
            return false;
        }
        String host = Uri.parse(url).getHost();
        return host != null && host.endsWith("5ka.ru");
    }

    private String decodeJavascriptString(String value) {
        if (value == null || "null".equals(value)) {
            return null;
        }
        try {
            JSONArray wrapper = new JSONArray("[" + value + "]");
            return wrapper.isNull(0) ? null : wrapper.getString(0);
        } catch (JSONException error) {
            return value;
        }
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            if (logView.length() > 0) {
                logView.append("\n");
            }
            logView.append(message);
        });
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightWrap() {
        return new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        if (apiWebView != null) {
            apiWebView.stopLoading();
            apiWebView.destroy();
        }
        super.onDestroy();
    }
}
