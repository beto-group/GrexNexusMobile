package com.grex.nexus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grex Floating Chat Bubble Service (Mothership)
 * Enables floating circular orb overlay mode for GrexNexusMobile.
 * The chat bubble stays floating on top of any app on Android,
 * and expands into a floating web window when tapped.
 */
public class FloatingBubbleService extends Service {

    private WindowManager windowManager;
    private View bubbleView;
    private View expandedView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams expandedParams;

    private boolean isExpanded = false;
    private WebView overlayWebView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "grex_floating_bubble_mothership";
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Grex Floating Chat Bubble",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }

            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("Grex Nexus Chat Bubble Active")
                    .setContentText("Tap floating bubble to access your app from anywhere")
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .build();

            startForeground(8882, notification);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            createBubbleView();
            createExpandedView();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createBubbleView() {
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int bubbleSize = (int) (56 * getResources().getDisplayMetrics().density);

        bubbleParams = new WindowManager.LayoutParams(
                bubbleSize,
                bubbleSize,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 20;
        bubbleParams.y = 300;

        FrameLayout frameLayout = new FrameLayout(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColors(new int[]{0xFFa855f7, 0xFF6b21a8});
        shape.setStroke((int) (2 * getResources().getDisplayMetrics().density), 0xFFe9d5ff);
        frameLayout.setBackground(shape);

        TextView iconText = new TextView(this);
        iconText.setText("⚡");
        iconText.setTextSize(22);
        iconText.setGravity(Gravity.CENTER);
        frameLayout.addView(iconText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        bubbleView = frameLayout;

        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartTime = System.currentTimeMillis();
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);

                        if (duration < 250 && diffX < 10 && diffY < 10) {
                            toggleExpand();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(bubbleView, bubbleParams);
    }

    private void createExpandedView() {
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int width = (int) (360 * getResources().getDisplayMetrics().density);
        int height = (int) (560 * getResources().getDisplayMetrics().density);

        expandedParams = new WindowManager.LayoutParams(
                width,
                height,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        expandedParams.gravity = Gravity.CENTER;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24 * getResources().getDisplayMetrics().density);
        bg.setColor(0xEE090D16);
        bg.setStroke((int) (1.5 * getResources().getDisplayMetrics().density), 0x44a855f7);
        container.setBackground(bg);
        container.setClipToOutline(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(16, 12, 16, 12);
        header.setBackgroundColor(0x3318181c);

        TextView title = new TextView(this);
        title.setText("Grex Nexus Floating App");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, titleParams);

        TextView fullBtn = new TextView(this);
        fullBtn.setText(" ⛶ ");
        fullBtn.setTextColor(0xFFc084fc);
        fullBtn.setTextSize(16);
        fullBtn.setPadding(12, 4, 12, 4);
        fullBtn.setOnClickListener(v -> {
            toggleExpand();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        header.addView(fullBtn);

        TextView minBtn = new TextView(this);
        minBtn.setText(" 🗕 ");
        minBtn.setTextColor(0xFFa1a1aa);
        minBtn.setTextSize(16);
        minBtn.setPadding(12, 4, 12, 4);
        minBtn.setOnClickListener(v -> toggleExpand());
        header.addView(minBtn);

        TextView closeBtn = new TextView(this);
        closeBtn.setText(" ✕ ");
        closeBtn.setTextColor(0xFFf87171);
        closeBtn.setTextSize(16);
        closeBtn.setPadding(12, 4, 12, 4);
        closeBtn.setOnClickListener(v -> stopSelf());
        header.addView(closeBtn);

        container.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        overlayWebView = new WebView(this);
        WebSettings settings = overlayWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(overlayWebView, true);
        }

        overlayWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        overlayWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }

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
        });

        overlayWebView.loadUrl("file:///android_asset/public/index.html");

        container.addView(overlayWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        expandedView = container;
    }

    private void toggleExpand() {
        if (isExpanded) {
            try {
                windowManager.removeView(expandedView);
            } catch (Exception ignored) {}
            windowManager.addView(bubbleView, bubbleParams);
            isExpanded = false;
        } else {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
            windowManager.addView(expandedView, expandedParams);
            isExpanded = true;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (isExpanded) {
                windowManager.removeView(expandedView);
            } else {
                windowManager.removeView(bubbleView);
            }
        } catch (Exception ignored) {}
    }
}
