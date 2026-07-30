package com.datacore.sovereigninspector;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "HomeScreenShortcut")
public class HomeScreenPlugin extends Plugin {

    @PluginMethod
    public void pinShortcut(PluginCall call) {
        String name = call.getString("name", "Grex Component");
        String url = call.getString("url", "");

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

                ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(context, "shortcut_" + System.currentTimeMillis())
                        .setShortLabel(name)
                        .setLongLabel("Launch " + name)
                        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
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
}
