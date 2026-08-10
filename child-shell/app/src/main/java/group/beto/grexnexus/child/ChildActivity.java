package group.beto.grexnexus.child;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
 * Minimal pure-Android WebView host for a single Datacore component bundle.
 * Injected at assets/bundle/ path by GrexNexusMobile mothership during install.
 *
 * Key features:
 *  - Native shouldInterceptRequest strips X-Frame-Options / CSP (mirrors Capacitor)
 *  - Native triple-tap overlay above the iframe → settings dialog
 *  - SharedPreferences backed host/port → exposed via grexNativeBridge JS interface
 *  - Floating bubble service integration
 */
public class ChildActivity extends Activity {

    private static final String PREFS = "grex_child_prefs";
    private static final String PREF_HOST = "hermes_webui_host";
    private static final String PREF_PORT = "hermes_webui_port";
    private static final String DEFAULT_HOST = "192.168.1.160";
    private static final String DEFAULT_PORT = "8787";

    private WebView webView;
    private SharedPreferences prefs;

    // Triple-tap detection state
    private int tapCount = 0;
    private long lastTapTime = 0;
    private static final int TRIPLE_TAP_TIMEOUT_MS = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // Truly fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // Root layout: WebView + transparent touch overlay stacked
        FrameLayout root = new FrameLayout(this);

        // ── WebView ──────────────────────────────────────────────────────────
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        }

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
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }

            /**
             * Strip X-Frame-Options / CSP / X-Content-Type-Options from ALL http responses.
             * This is the same approach Capacitor uses internally — makes iframes work
             * from file:// origin exactly as they do in the mothership Capacitor WebView.
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String urlStr = request.getUrl().toString();
                if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                    return super.shouldInterceptRequest(view, request);
                }
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestMethod(request.getMethod());
                    for (Map.Entry<String, String> h : request.getRequestHeaders().entrySet()) {
                        conn.setRequestProperty(h.getKey(), h.getValue());
                    }
                    conn.connect();

                    // Build cleaned headers — strip anything that blocks cross-origin frames
                    Map<String, String> responseHeaders = new HashMap<>();
                    for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                        String key = entry.getKey();
                        if (key == null) continue;
                        String lkey = key.toLowerCase();
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
                    return super.shouldInterceptRequest(view, request);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Push native SharedPreferences values into WebView localStorage
                // so the JS component picks up the correct host/port immediately
                syncConfigToLocalStorage();
                if (pendingSharedText != null) {
                    dispatchSharedTextToJs(pendingSharedText);
                }
            }
        });

        // ── JavaScript Bridge ────────────────────────────────────────────────
        webView.addJavascriptInterface(new Object() {

            /** Read the saved host (set from native settings dialog) */
            @android.webkit.JavascriptInterface
            public String getHost() {
                return prefs.getString(PREF_HOST, DEFAULT_HOST);
            }

            /** Read the saved port */
            @android.webkit.JavascriptInterface
            public String getPort() {
                return prefs.getString(PREF_PORT, DEFAULT_PORT);
            }

            /** Save host from JS side */
            @android.webkit.JavascriptInterface
            public void setHost(String host) {
                prefs.edit().putString(PREF_HOST, host).apply();
            }

            /** Save port from JS side */
            @android.webkit.JavascriptInterface
            public void setPort(String port) {
                prefs.edit().putString(PREF_PORT, port).apply();
            }

            /** Show native settings dialog */
            @android.webkit.JavascriptInterface
            public void showSettings() {
                runOnUiThread(() -> showNativeSettingsDialog());
            }

            /** Trigger floating chat bubble overlay (Orb) */
            @android.webkit.JavascriptInterface
            public void startFloatingBubble() {
                runOnUiThread(() -> enableFloatingBubbleMode());
            }

            /** Enter Picture-in-Picture mode */
            @android.webkit.JavascriptInterface
            public void startPip() {
                runOnUiThread(() -> enterPipMode());
            }

        }, "grexNativeBridge");

        root.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // ── Transparent Touch Overlay (above iframe, detects triple-tap) ────
        View touchOverlay = new View(this) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    long now = System.currentTimeMillis();
                    if (now - lastTapTime < TRIPLE_TAP_TIMEOUT_MS) {
                        tapCount++;
                    } else {
                        tapCount = 1;
                    }
                    lastTapTime = now;
                    if (tapCount >= 3) {
                        tapCount = 0;
                        showNativeSettingsDialog();
                        return true;
                    }
                }
                return false; // pass all other touches through to WebView
            }
        };
        touchOverlay.setBackgroundColor(Color.TRANSPARENT);
        // Only catch touches in the top-left corner (80x80 dp) to avoid blocking the iframe
        int cornerPx = (int)(80 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(cornerPx, cornerPx);
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        root.addView(touchOverlay, overlayParams);

        setContentView(root);

        webView.loadUrl("file:///android_asset/bundle/index.html");
        handleSendIntent(getIntent());
    }

    /**
     * Push the native-stored host/port into the WebView's localStorage.
     * The JS component (HermesWebUIEngine) reads from localStorage keys:
     *   hermes_webui_host, hermes_webui_port
     */
    private void syncConfigToLocalStorage() {
        String host = prefs.getString(PREF_HOST, DEFAULT_HOST);
        String port = prefs.getString(PREF_PORT, DEFAULT_PORT);
        String js = "localStorage.setItem('hermes_webui_host', '" + host.replace("'", "\\'") + "');" +
                    "localStorage.setItem('hermes_webui_port', '" + port.replace("'", "\\'") + "');";
        webView.evaluateJavascript(js, null);
    }

    /**
     * Native settings dialog — shown on triple-tap of the top-left corner.
     * Works even when iframe covers the whole screen.
     */
    private void showNativeSettingsDialog() {
        String currentHost = prefs.getString(PREF_HOST, DEFAULT_HOST);
        String currentPort = prefs.getString(PREF_PORT, DEFAULT_PORT);

        // Build dialog layout programmatically (no XML needed)
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int dp16 = (int)(16 * getResources().getDisplayMetrics().density);
        int dp8  = (int)(8  * getResources().getDisplayMetrics().density);
        container.setPadding(dp16, dp16, dp16, dp8);
        container.setBackgroundColor(Color.parseColor("#1a1a2e"));

        // Title
        TextView title = new TextView(this);
        title.setText("⚙  Grex Child Settings");
        title.setTextColor(Color.parseColor("#a855f7"));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp16);
        container.addView(title);

        // Host label + input
        TextView hostLabel = new TextView(this);
        hostLabel.setText("Hermes Server IP");
        hostLabel.setTextColor(Color.parseColor("#94a3b8"));
        hostLabel.setTextSize(12);
        container.addView(hostLabel);

        EditText hostInput = new EditText(this);
        hostInput.setText(currentHost);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostInput.setTextColor(Color.WHITE);
        hostInput.setHintTextColor(Color.parseColor("#64748b"));
        hostInput.setHint("e.g. 192.168.1.160");
        hostInput.setBackgroundColor(Color.parseColor("#0f172a"));
        hostInput.setPadding(dp8, dp8, dp8, dp8);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(0, dp8, 0, dp16);
        container.addView(hostInput, inputParams);

        // Port label + input
        TextView portLabel = new TextView(this);
        portLabel.setText("Port");
        portLabel.setTextColor(Color.parseColor("#94a3b8"));
        portLabel.setTextSize(12);
        container.addView(portLabel);

        EditText portInput = new EditText(this);
        portInput.setText(currentPort);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setTextColor(Color.WHITE);
        portInput.setHintTextColor(Color.parseColor("#64748b"));
        portInput.setHint("e.g. 8787");
        portInput.setBackgroundColor(Color.parseColor("#0f172a"));
        portInput.setPadding(dp8, dp8, dp8, dp8);
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        portParams.setMargins(0, dp8, 0, dp16);
        container.addView(portInput, portParams);

        // Info
        TextView info = new TextView(this);
        info.setText("Triple-tap top-left corner to open this dialog");
        info.setTextColor(Color.parseColor("#475569"));
        info.setTextSize(11);
        container.addView(info);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setView(container);
        builder.setPositiveButton("Save & Reload", (dialog, which) -> {
            String newHost = hostInput.getText().toString().trim();
            String newPort = portInput.getText().toString().trim();
            if (newHost.isEmpty()) newHost = DEFAULT_HOST;
            if (newPort.isEmpty()) newPort = DEFAULT_PORT;
            prefs.edit()
                .putString(PREF_HOST, newHost)
                .putString(PREF_PORT, newPort)
                .apply();
            // Write to localStorage then reload the bundle
            String finalHost = newHost;
            String finalPort = newPort;
            String js = "localStorage.setItem('hermes_webui_host', '" + finalHost.replace("'", "\\'") + "');" +
                        "localStorage.setItem('hermes_webui_port', '" + finalPort.replace("'", "\\'") + "');" +
                        "window.location.reload();";
            webView.evaluateJavascript(js, null);
            Toast.makeText(this, "Saved: " + newHost + ":" + newPort, Toast.LENGTH_SHORT).show();
        });
        builder.setNeutralButton("Floating Bubble 💬", (dialog, which) -> enableFloatingBubbleMode());
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public void enableFloatingBubbleMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                    );
                    startActivity(intent);
                    Toast.makeText(this, "Please enable 'Display over other apps' permission", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Permission settings failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
        try {
            Intent serviceIntent = new Intent(this, FloatingBubbleService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            moveTaskToBack(true);
        } catch (Exception e) {
            Toast.makeText(this, "Floating Bubble error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Enter Android Picture-in-Picture mode.
     * No SYSTEM_ALERT_WINDOW permission needed — works on all sideloaded APKs.
     * Minimum API 26 (our minSdk).
     */
    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            // 9:16 portrait ratio for a chat-like floating window
            builder.setAspectRatio(new Rational(9, 16));
            enterPictureInPictureMode(builder.build());
        } else {
            // Pre-API 26 fallback: just minimize
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        // Auto-enter PiP when user presses Home while in the app
        // (same behavior as YouTube floating video)
        enterPipMode();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        // Hide/show system UI based on PiP state
        if (!isInPictureInPictureMode) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
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
                if (sharedText == null) sharedText = intent.getStringExtra(Intent.EXTRA_SUBJECT);
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
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                android.webkit.CookieManager.getInstance().flush();
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) webView.destroy();
    }
}
