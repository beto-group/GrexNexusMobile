package group.beto.grexnexus.child;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

        // Enable remote DevTools debugging (Chrome → chrome://inspect)
        WebView.setWebContentsDebuggingEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (pendingSharedText != null) {
                    dispatchSharedTextToJs(pendingSharedText);
                }
            }
        });

        setContentView(webView);

        // Load the injected bootloader — this file is replaced by the mothership
        // during APK factory ZIP mutation before installation
        webView.loadUrl("file:///android_asset/bundle/index.html");

        // Handle cold-start intent
        handleSendIntent(getIntent());
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
