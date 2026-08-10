package com.grex.nexus;

import android.app.PictureInPictureParams;
import android.os.Build;
import android.util.Rational;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * PipPlugin — Capacitor Plugin for Picture-in-Picture mode
 *
 * Exposes enterPip() to the JS layer so any Datacore component can
 * float the current GrexNexus Mothership view as a PiP window.
 *
 * Usage from JS:
 *   window.GrexPip?.enter()
 *   // or via Capacitor:
 *   import { Plugins } from '@capacitor/core';
 *   Plugins.PipPlugin.enterPip();
 *
 * No SYSTEM_ALERT_WINDOW permission required.
 */
@CapacitorPlugin(name = "PipPlugin")
public class PipPlugin extends Plugin {

    /**
     * Enter PiP mode — floats the current component view.
     * Aspect ratio defaults to 16:9 landscape for general content.
     * Call with { ratio: "9:16" } for portrait (chat/mobile-style).
     */
    @PluginMethod
    public void enterPip(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            call.resolve(); // silently succeed on old devices
            return;
        }

        String ratioStr = call.getString("ratio", "16:9");
        int num = 16, den = 9;
        try {
            String[] parts = ratioStr.split(":");
            num = Integer.parseInt(parts[0].trim());
            den = Integer.parseInt(parts[1].trim());
        } catch (Exception ignored) {}

        final int finalNum = num;
        final int finalDen = den;

        getActivity().runOnUiThread(() -> {
            try {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(finalNum, finalDen))
                    .build();
                getActivity().enterPictureInPictureMode(params);
                call.resolve();
            } catch (Exception e) {
                call.reject("PiP failed: " + e.getMessage());
            }
        });
    }

    /**
     * Check if PiP is supported and currently available on this device.
     */
    @PluginMethod
    public void isSupported(PluginCall call) {
        com.getcapacitor.JSObject result = new com.getcapacitor.JSObject();
        result.put("supported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        result.put("inPip", getActivity().isInPictureInPictureMode());
        call.resolve(result);
    }
}
