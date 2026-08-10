package group.beto.grexnexus.child;

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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Grex Floating Chat Bubble Service
 * Enables floating overlay mode for standalone child APKs.
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

        // Android 8.0+ Foreground Service requirement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "grex_floating_bubble";
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
                    .setContentTitle("Grex Chat Bubble Active")
                    .setContentText("Tap floating bubble to access your app from anywhere")
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .build();

            startForeground(8881, notification);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createBubbleView();
        createExpandedView();
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

        // Custom Glass Orb Circle View
        FrameLayout frameLayout = new FrameLayout(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColors(new int[]{0xFFa855f7, 0xFF6b21a8});
        shape.setStroke((int) (2 * getResources().getDisplayMetrics().density), 0xFFe9d5ff);
        frameLayout.setBackground(shape);

        // Icon inside orb
        TextView iconText = new TextView(this);
        iconText.setText("⚡");
        iconText.setTextSize(22);
        iconText.setGravity(Gravity.CENTER);
        frameLayout.addView(iconText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        bubbleView = frameLayout;

        // Touch Drag & Tap Listener
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

        // Container Layout
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24 * getResources().getDisplayMetrics().density);
        bg.setColor(0xEE090D16);
        bg.setStroke((int) (1.5 * getResources().getDisplayMetrics().density), 0x44a855f7);
        container.setBackground(bg);
        container.setClipToOutline(true);

        // Header Control Bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(16, 12, 16, 12);
        header.setBackgroundColor(0x3318181c);

        TextView title = new TextView(this);
        title.setText("Grex Floating App");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, titleParams);

        // Fullscreen launch button
        TextView fullBtn = new TextView(this);
        fullBtn.setText(" ⛶ ");
        fullBtn.setTextColor(0xFFc084fc);
        fullBtn.setTextSize(16);
        fullBtn.setPadding(12, 4, 12, 4);
        fullBtn.setOnClickListener(v -> {
            toggleExpand();
            Intent intent = new Intent(this, ChildActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        header.addView(fullBtn);

        // Minimize Button
        TextView minBtn = new TextView(this);
        minBtn.setText(" 🗕 ");
        minBtn.setTextColor(0xFFa1a1aa);
        minBtn.setTextSize(16);
        minBtn.setPadding(12, 4, 12, 4);
        minBtn.setOnClickListener(v -> toggleExpand());
        header.addView(minBtn);

        // Close Button
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

        // Floating WebView Surface
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
        });

        overlayWebView.loadUrl("file:///android_asset/bundle/index.html");

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
