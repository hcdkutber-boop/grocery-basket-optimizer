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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://5ka.ru/";
    private static final String API_ORIGIN = "https://5d.5ka.ru";
    private static final String SEARCH_BASE =
            API_ORIGIN + "/api/catalog/v3/stores/";

    private final Map<String, String> capturedHeaders = new ConcurrentHashMap<>();

    private WebView webView;
    private TextView statusView;
    private TextView logView;
    private EditText queryView;
    private String storeId;

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

        setContentView(root);
        appendLog("APK 0.2-mobile-spike. Нативный поиск; корзина и checkout отключены.");
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

        appendLog("Нативный поиск: «" + query + "»…");
        new Thread(() -> runNativeSearch(endpoint)).start();
    }

    private void runNativeSearch(String endpoint) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !"5d.5ka.ru".equalsIgnoreCase(url.getHost())) {
                throw new IOException("Blocked API host");
            }

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("Referer", HOME_URL);
            connection.setRequestProperty("Origin", "https://5ka.ru");
            connection.setRequestProperty(
                    "User-Agent",
                    webView.getSettings().getUserAgentString()
            );

            String cookie = CookieManager.getInstance().getCookie(API_ORIGIN + "/");
            if (cookie != null && !cookie.isBlank()) {
                connection.setRequestProperty("Cookie", cookie);
            }

            for (Map.Entry<String, String> entry : capturedHeaders.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String body = readStream(stream);

            int finalStatus = status;
            String finalBody = body;
            runOnUiThread(() -> handleNativeSearchResult(finalStatus, finalBody));
        } catch (Exception error) {
            String message = error.getClass().getSimpleName() + ": " + error.getMessage();
            runOnUiThread(() -> appendLog("Нативный запрос не выполнен: " + message));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                if (builder.length() > 2_000_000) {
                    throw new IOException("Response too large");
                }
            }
        }
        return builder.toString();
    }

    private void handleNativeSearchResult(int status, String body) {
        appendLog("HTTP " + status);

        if (status < 200 || status >= 300) {
            String preview = body == null ? "" : body.trim();
            if (preview.length() > 300) {
                preview = preview.substring(0, 300);
            }
            if (!preview.isBlank()) {
                appendLog("Ответ: " + preview);
            }
            return;
        }

        try {
            JSONObject payload = new JSONObject(body == null ? "{}" : body);
            JSONArray products = payload.optJSONArray("products");
            if (products == null) {
                appendLog("HTTP " + status + ", но поле products не найдено.");
                return;
            }

            appendLog("Найдено товаров: " + products.length());
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
            appendLog("Не удалось разобрать ответ API: " + error.getMessage());
        }
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
        super.onDestroy();
    }
}
