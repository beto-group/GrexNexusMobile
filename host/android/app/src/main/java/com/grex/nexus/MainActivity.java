package com.grex.nexus;

import android.app.PictureInPictureParams;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(HomeScreenPlugin.class);
        registerPlugin(PipPlugin.class);
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(true);
        try {
            if (this.bridge != null && this.bridge.getWebView() != null) {
                this.bridge.getWebView().clearCache(true);
            }
        } catch (Exception e) {}

        // Expose window.GrexPip.enter() directly to JS so any component
        // can trigger PiP without importing Capacitor plugins
        exposeGrexPipBridge();
    }

    /**
     * Inject window.GrexPip = { enter(ratio?) } into every page load.
     * Components call: window.GrexPip?.enter('9:16')
     * ratio defaults to '16:9' (landscape). Use '9:16' for portrait chat.
     */
    private void exposeGrexPipBridge() {
        if (this.bridge == null || this.bridge.getWebView() == null) return;
        this.bridge.getWebView().addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void enter(String ratio) {
                runOnUiThread(() -> enterPipModeWithRatio(ratio));
            }
            @android.webkit.JavascriptInterface
            public void enter() {
                runOnUiThread(() -> enterPipModeWithRatio("16:9"));
            }
            @android.webkit.JavascriptInterface
            public boolean isSupported() {
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
            }
        }, "GrexPip");
    }

    void enterPipModeWithRatio(String ratioStr) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            moveTaskToBack(true);
            return;
        }
        try {
            int num = 16, den = 9;
            if (ratioStr != null && ratioStr.contains(":")) {
                String[] p = ratioStr.split(":");
                num = Integer.parseInt(p[0].trim());
                den = Integer.parseInt(p[1].trim());
            }
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(num, den))
                .build();
            enterPictureInPictureMode(params);
        } catch (Exception e) {
            moveTaskToBack(true);
        }
    }

    /**
     * Auto-enter PiP when user presses Home — floats the current component.
     * Same behavior as YouTube: press Home → video keeps playing in a small window.
     * DISABLED by default so it doesn't surprise the user.
     * To enable: set window.GrexPipAutoEnter = true from JS.
     */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (this.bridge == null || this.bridge.getWebView() == null) return;
        // Check JS flag before auto-entering PiP
        this.bridge.getWebView().evaluateJavascript(
            "(function(){ return window.GrexPipAutoEnter === true; })()",
            result -> {
                if ("true".equals(result)) {
                    runOnUiThread(() -> enterPipModeWithRatio("16:9"));
                }
            }
        );
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (this.bridge == null || this.bridge.getWebView() == null) return;
        // Notify JS of PiP state change
        String js = "window.dispatchEvent(new CustomEvent('grexPipModeChanged', { detail: { inPip: " + isInPictureInPictureMode + " } }));";
        this.bridge.getWebView().post(() ->
            this.bridge.getWebView().evaluateJavascript(js, null)
        );
        if (!isInPictureInPictureMode) {
            // Restore fullscreen immersive when returning from PiP
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    /**
     * Intercept Android 3-button navigation bar Back button press completely.
     * Prevents Android OS from closing/minimizing the Mothership application
     * when navigating single page component views.
     */
    @Override
    public void onBackPressed() {
        if (this.bridge != null && this.bridge.getWebView() != null) {
            this.bridge.getWebView().evaluateJavascript(
                "javascript:(function(){" +
                "  var evt = new CustomEvent('nativeAndroidBackButton');" +
                "  window.dispatchEvent(evt);" +
                "  document.dispatchEvent(evt);" +
                "})();",
                null
            );
        } else {
            super.onBackPressed();
        }
    }
}
