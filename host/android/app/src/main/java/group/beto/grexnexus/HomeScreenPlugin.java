package com.datacore.sovereigninspector;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "HomeScreenShortcut")
public class HomeScreenPlugin extends Plugin {

    private Icon createComponentIcon(Context context, String base64OrUrl, String name) {
        if (base64OrUrl != null && !base64OrUrl.isEmpty()) {
            try {
                String cleanBase64 = base64OrUrl.contains(",") ? base64OrUrl.split(",")[1] : base64OrUrl;
                byte[] decoded = Base64.decode(cleanBase64, Base64.DEFAULT);
                Bitmap b = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                if (b != null) {
                    return Icon.createWithBitmap(b);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Fallback: Dynamic high-dpi component initial badge icon
        int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF7E22CE); // Custom Component Purple Accent
        canvas.drawRoundRect(0, 0, size, size, 44, 44, bgPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(96);
        textPaint.setTextAlign(Paint.Align.CENTER);

        String initial = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "G";
        float yPos = (canvas.getHeight() / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
        canvas.drawText(initial, canvas.getWidth() / 2f, yPos, textPaint);

        return Icon.createWithBitmap(bitmap);
    }

    @PluginMethod
    public void pinShortcut(PluginCall call) {
        String name = call.getString("name", "Grex Component");
        String url = call.getString("url", "");
        String iconStr = call.getString("icon", "");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context context = getContext();
            ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);

            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
                Intent intent = new Intent(context, MainActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                if (url != null && !url.isEmpty()) {
                    intent.setData(Uri.parse(url));
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                Icon shortcutIcon = createComponentIcon(context, iconStr, name);

                ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(context, "shortcut_" + System.currentTimeMillis())
                        .setShortLabel(name)
                        .setLongLabel("Launch " + name)
                        .setIcon(shortcutIcon)
                        .setIntent(intent)
                        .build();

                shortcutManager.requestPinShortcut(pinShortcutInfo, null);

                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
                return;
            }
        }

        JSObject ret = new JSObject();
        ret.put("success", false);
        ret.put("reason", "Shortcut pinning not supported on this Android OS version.");
        call.resolve(ret);
    }

    @PluginMethod
    public void pinWidget(PluginCall call) {
        String name = call.getString("name", "Grex Component");
        String url = call.getString("url", "");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context context = getContext();
            AppWidgetManager appWidgetManager = context.getSystemService(AppWidgetManager.class);
            ComponentName myProvider = new ComponentName(context, GrexWidgetProvider.class);

            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported()) {
                Bundle extras = new Bundle();
                extras.putString("component_name", name);
                extras.putString("component_url", url);

                appWidgetManager.requestPinAppWidget(myProvider, extras, null);

                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
                return;
            }
        }

        JSObject ret = new JSObject();
        ret.put("success", false);
        ret.put("reason", "Widget pinning not supported on this Android OS version.");
        call.resolve(ret);
    }
}
