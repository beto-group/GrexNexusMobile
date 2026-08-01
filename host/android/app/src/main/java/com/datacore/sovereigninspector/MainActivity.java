package com.datacore.sovereigninspector;

import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(HomeScreenPlugin.class);
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(true);
        try {
            if (this.bridge != null && this.bridge.getWebView() != null) {
                this.bridge.getWebView().clearCache(true);
            }
        } catch (Exception e) {}
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
