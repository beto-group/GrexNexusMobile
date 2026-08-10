package group.beto.grexnexus.child;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GrexChildShell — Standalone Datacore Component Host
 *
 * This is a minimal, pure-Android WebView host that loads a single
 * Datacore component bundle injected at the assets/bundle/ path.
 *
 * The APK containing this activity is used as a template by the
 * GrexNexusMobile mothership app. The mothership clones this APK,
 * injects the component's bundle.es.js + bootloader index.html into
 * the assets/bundle/ ZIP entries, and triggers native installation.
 *
 * Template APK path in mothership: assets/child_shell_template.apk
 *
 * Package: group.beto.grexnexus.child (separate from mothership group.beto.grexnexus)
 */
public class ChildActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Truly fullscreen — no title bar, no status bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        // Core JS + Storage
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // File access — required for ES module imports from file:///android_asset/
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Viewport
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // Hardware Acceleration & High Render Priority
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        // V8 Bytecode & Disk Caching — enables instant cold starts
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Enable Mixed Content & Cookies for local HTTP/HTTPS backend connection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        }

        // Enable remote DevTools debugging (Chrome → chrome://inspect)
        WebView.setWebContentsDebuggingEnabled(true);

        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // all URLs load inside this WebView
            }

            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed(); // trust local LAN self-signed certs
            }

            /**
             * Intercept every response and strip headers that would block the page.
             * This is exactly what Capacitor does — it proxies responses and removes:
             *   X-Frame-Options, X-Content-Type-Options, Content-Security-Policy
             * Without this, X-Frame-Options:SAMEORIGIN causes ERR_BLOCKED_BY_RESPONSE
             * when loading an http:// backend from a file:// origin.
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String urlStr = request.getUrl().toString();
                // Only intercept http/https requests (not file:// assets)
                if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                    return super.shouldInterceptRequest(view, request);
                }
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestMethod(request.getMethod());
                    // Forward request headers (cookies, auth, etc.)
                    for (Map.Entry<String, String> h : request.getRequestHeaders().entrySet()) {
                        conn.setRequestProperty(h.getKey(), h.getValue());
                    }
                    conn.connect();

                    // Build cleaned response headers — drop the blocking ones
                    Map<String, String> responseHeaders = new HashMap<>();
                    for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                        String key = entry.getKey();
                        if (key == null) continue;
                        String lkey = key.toLowerCase();
                        // Drop headers that block cross-origin frame loading
                        if (lkey.equals("x-frame-options") ||
                            lkey.equals("x-content-type-options") ||
                            lkey.equals("content-security-policy") ||
                            lkey.equals("content-security-policy-report-only")) {
                            continue;
                        }
                        List<String> vals = entry.getValue();
                        if (vals != null && !vals.isEmpty()) {
                            responseHeaders.put(key, vals.get(0));
                        }
                    }

                    int statusCode = conn.getResponseCode();
                    String mimeType = conn.getContentType();
                    if (mimeType == null) mimeType = "text/plain";
                    // Strip charset from mime for WebResourceResponse
                    String charset = "utf-8";
                    if (mimeType.contains(";")) {
                        String[] parts = mimeType.split(";");
                        mimeType = parts[0].trim();
                        for (String p : parts) {
                            if (p.trim().toLowerCase().startsWith("charset=")) {
                                charset = p.trim().substring(8).trim();
                            }
                        }
                    }

                    InputStream stream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    if (stream == null) stream = new java.io.ByteArrayInputStream(new byte[0]);

                    return new WebResourceResponse(
                        mimeType, charset, statusCode,
                        statusCode == 200 ? "OK" : "Error",
                        responseHeaders, stream
                    );
                } catch (IOException e) {
                    // Network error — fall through to default WebView handling
                    return super.shouldInterceptRequest(view, request);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (pendingSharedText != null) {
                    dispatchSharedTextToJs(pendingSharedText);
                }
            }
        });

        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void startFloatingBubble() {
                runOnUiThread(() -> enableFloatingBubbleMode());
            }
        }, "grexNativeBridge");

        setContentView(webView);

        // Load the injected bootloader — this file is replaced by the mothership
        // during APK factory ZIP mutation before installation
        webView.loadUrl("file:///android_asset/bundle/index.html");

        // Handle cold-start intent
        handleSendIntent(getIntent());
    }

    public void enableFloatingBubbleMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
                return;
            }
        }
        Intent serviceIntent = new Intent(this, FloatingBubbleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        moveTaskToBack(true);
    }

    private String pendingSharedText = null;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSendIntent(intent);
    }

    private void handleSendIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type) || type.startsWith("text/")) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText == null) {
                    sharedText = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                }
                if (sharedText != null) {
                    pendingSharedText = sharedText;
                    dispatchSharedTextToJs(sharedText);
                }
            }
        }
    }

    private void dispatchSharedTextToJs(String text) {
        if (webView == null || text == null) return;
        try {
            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("text", text);
            payload.put("timestamp", System.currentTimeMillis());
            String js = "window.dispatchEvent(new CustomEvent('grexShareReceived', { detail: " + payload.toString() + " }));";
            webView.post(() -> webView.evaluateJavascript(js, null));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}
