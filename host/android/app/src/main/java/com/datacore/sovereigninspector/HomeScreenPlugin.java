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
import android.graphics.Rect;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * HomeScreenPlugin — Native Android capabilities for GrexNexusMobile
 *
 * Exposes the following Capacitor plugin methods to JS:
 *   - pinShortcut(name, url, icon)     → Android 8+ ShortcutManager pin dialog
 *   - pinWidget(name, url)             → Android 8+ AppWidgetManager pin dialog
 *   - installChildApk(name, label, bundleJs) → APK Factory: clone child_shell_template.apk,
 *                                              inject component bundle, trigger native installer
 *
 * APK FACTORY ARCHITECTURE (installChildApk):
 * ─────────────────────────────────────────────────────────────────────────────
 * The mothership bundles `assets/child_shell_template.apk` — a pre-built minimal
 * Android WebView app (package: group.beto.grexnexus.child). The factory method:
 *
 *  1. Reads child_shell_template.apk from mothership assets (ZipInputStream)
 *  2. Copies all entries to a new output APK (ZipOutputStream)
 *  3. REPLACES assets/bundle/index.html with a component-specific bootloader
 *  4. REPLACES assets/bundle/bundle.es.js with the live component bundle
 *  5. Writes the mutated APK to getCacheDir()/ChildApks/<ComponentName>.apk
 *  6. Triggers Android native package installer via FileProvider URI intent
 *
 * No Gradle, no apksigner, no build tools needed on device.
 * The mutated APK retains the original debug signature from the template build.
 * Android allows installation if "Install Unknown Apps" is enabled for the app.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * SKILL Reference: _RESOURCES/SKILL/protocols/MOBILE_DEBUGGING.md § R
 */
@CapacitorPlugin(name = "HomeScreenShortcut")
public class HomeScreenPlugin extends Plugin {

    // ─── Icon Factory ───────────────────────────────────────────────────────

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

    // ─── Home Screen Shortcut Pin ────────────────────────────────────────────

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

    // ─── App Widget Pin ──────────────────────────────────────────────────────

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

    // ─── APK Factory: Clone + Inject + Install ───────────────────────────────

    /**
     * installChildApk — Generates and installs a standalone component APK.
     *
     * Called from JS via:
     *   window.Capacitor.Plugins.HomeScreenShortcut.installChildApk({
     *     name: 'SocialBotEngine',          // safe identifier (no spaces)
     *     label: 'Social Bot Engine',       // human-readable app name
     *     bundleJs: '<raw JS text>'         // full bundle.es.js content
     *   });
     *
     * Requires: assets/child_shell_template.apk bundled in mothership.
     * Build it with: bash _RESOURCES/SKILL/scripts/build-child-shell.sh
     */
    @PluginMethod
    public void installChildApk(PluginCall call) {
        String componentName = call.getString("name", "GrexComponent");
        String componentLabel = call.getString("label", call.getString("name", "Grex Component"));
        String iconStr = call.getString("icon", "");
        String bundleJs = call.getString("bundleJs", "");

        // Validate required fields
        if (bundleJs == null || bundleJs.isEmpty()) {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("error", "bundleJs is required and must be the raw component bundle text.");
            call.resolve(ret);
            return;
        }

        Context context = getContext();

        // Run ZIP mutation on background thread — never block the UI thread
        new Thread(() -> {
            File outputApk = null;
            try {
                // 1. Prepare output directory
                File outputDir = new File(context.getCacheDir(), "ChildApks");
                //noinspection ResultOfMethodCallIgnored
                outputDir.mkdirs();

                String safeName = componentName.replaceAll("[^a-zA-Z0-9_]", "");
                File tempUnsignedApk = new File(outputDir, safeName + "_unsigned.apk");

                // 2. Open template APK from mothership assets
                InputStream templateStream;
                try {
                    templateStream = context.getAssets().open("child_shell_template.apk");
                } catch (IOException e) {
                    throw new IOException(
                        "child_shell_template.apk not found in assets. " +
                        "Build it with: bash _RESOURCES/SKILL/scripts/build-child-shell.sh", e
                    );
                }

                // 3. Build component bootloader HTML + badged icon PNG
                String bootloaderHtml = buildBootloaderHtml(componentLabel, componentName);
                byte[] badgedIconPng = generateBadgedIcon(context, iconStr, 432);

                // 4. ZIP Mutation: copy template entries, replace bundle/index.html + bundle/bundle.es.js + icon + manifest
                ZipInputStream zis = new ZipInputStream(templateStream);
                FileOutputStream fos = new FileOutputStream(tempUnsignedApk);
                ZipOutputStream zos = new ZipOutputStream(fos);

                zos.setLevel(Deflater.DEFAULT_COMPRESSION);

                ZipEntry entry;
                byte[] buffer = new byte[16384];
                java.util.Set<String> writtenEntries = new java.util.HashSet<>();

                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();

                    // Skip placeholder entries and duplicate entry names
                    if (entryName.equals("assets/bundle/index.html") ||
                        entryName.equals("assets/bundle/bundle.es.js") ||
                        entryName.endsWith("/bundle/index.html") ||
                        entryName.endsWith("/bundle/bundle.es.js") ||
                        writtenEntries.contains(entryName)) {
                        zis.closeEntry();
                        continue;
                    }

                    // Mutate AndroidManifest.xml app label
                    if (entryName.equals("AndroidManifest.xml")) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }
                        byte[] manifestBytes = baos.toByteArray();
                        byte[] mutatedManifest = mutateManifestLabel(manifestBytes, componentLabel);

                        ZipEntry newEntry = new ZipEntry(entryName);
                        zos.putNextEntry(newEntry);
                        zos.write(mutatedManifest);
                        zos.closeEntry();
                        zis.closeEntry();
                        writtenEntries.add(entryName);
                        continue;
                    }

                    // Mutate mipmap icons with custom component icon + mothership badge overlay
                    if (badgedIconPng != null && entryName.startsWith("res/mipmap-") && entryName.endsWith(".png")) {
                        ZipEntry newEntry = new ZipEntry(entryName);
                        zos.putNextEntry(newEntry);
                        zos.write(badgedIconPng);
                        zos.closeEntry();
                        zis.closeEntry();
                        writtenEntries.add(entryName);
                        continue;
                    }

                    writtenEntries.add(entryName);

                    ZipEntry newEntry = new ZipEntry(entryName);
                    newEntry.setMethod(entry.getMethod());
                    if (entry.getMethod() == ZipEntry.STORED) {
                        newEntry.setSize(entry.getSize());
                        newEntry.setCompressedSize(entry.getCompressedSize());
                        newEntry.setCrc(entry.getCrc());
                    }
                    zos.putNextEntry(newEntry);

                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }

                    zos.closeEntry();
                    zis.closeEntry();
                }

                // 5. Inject real bootloader HTML (replaces template placeholder)
                if (!writtenEntries.contains("assets/bundle/index.html")) {
                    byte[] htmlBytes = bootloaderHtml.getBytes("UTF-8");
                    ZipEntry htmlEntry = new ZipEntry("assets/bundle/index.html");
                    zos.putNextEntry(htmlEntry);
                    zos.write(htmlBytes);
                    zos.closeEntry();
                    writtenEntries.add("assets/bundle/index.html");
                }

                // 6. Inject real component bundle.es.js
                if (!writtenEntries.contains("assets/bundle/bundle.es.js")) {
                    byte[] jsBytes = bundleJs.getBytes("UTF-8");
                    ZipEntry jsEntry = new ZipEntry("assets/bundle/bundle.es.js");
                    zos.putNextEntry(jsEntry);
                    zos.write(jsBytes);
                    zos.closeEntry();
                    writtenEntries.add("assets/bundle/bundle.es.js");
                }

                zis.close();
                zos.close();
                fos.close();
                templateStream.close();

                // 7. Sign mutated APK with v1 + v2 + v3 schemes using ApkSigner + debug.keystore
                File signedApk = new File(outputDir, safeName + ".apk");
                File unsignedApk = tempUnsignedApk;

                try {
                    KeyStore ks = KeyStore.getInstance("PKCS12");
                    try (InputStream ksIs = context.getAssets().open("debug.keystore")) {
                        ks.load(ksIs, "android".toCharArray());
                    }

                    PrivateKey privateKey = (PrivateKey) ks.getKey("androiddebugkey", "android".toCharArray());
                    java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) ks.getCertificate("androiddebugkey");

                    com.android.apksig.ApkSigner.SignerConfig signerConfig = new com.android.apksig.ApkSigner.SignerConfig.Builder(
                        "CERT",
                        privateKey,
                        java.util.Collections.singletonList(cert)
                    ).build();

                    new com.android.apksig.ApkSigner.Builder(java.util.Collections.singletonList(signerConfig))
                        .setInputApk(unsignedApk)
                        .setOutputApk(signedApk)
                        .setV1SigningEnabled(true)
                        .setV2SigningEnabled(true)
                        .setV3SigningEnabled(true)
                        .build()
                        .sign();

                    //noinspection ResultOfMethodCallIgnored
                    unsignedApk.delete();

                    android.util.Log.i("GrexAPKFactory",
                        "[APK Factory] ✓ APK signed with v2+v3 scheme: " + signedApk.getAbsolutePath() +
                        " (" + (signedApk.length() / 1024) + " KB)");

                } catch (Exception se) {
                    android.util.Log.e("GrexAPKFactory", "[APK Factory] ⚠️ Signing warning: " + se.getMessage(), se);
                    signedApk = unsignedApk; // Fallback
                }

                // 8. Trigger native Android package installer on UI thread
                final File finalApk = signedApk;
                getActivity().runOnUiThread(() -> triggerInstaller(call, context, finalApk));

            } catch (Exception e) {
                android.util.Log.e("GrexAPKFactory", "[APK Factory] ✗ Error: " + e.getMessage(), e);
                JSObject ret = new JSObject();
                ret.put("success", false);
                ret.put("error", e.getMessage());
                call.resolve(ret);
            }
        }).start();
    }

    /**
     * Triggers the Android native package installer for the mutated child APK.
     * Uses FileProvider for Android 7+ (API 24+) URI permissions.
     */
    private void triggerInstaller(PluginCall call, Context context, File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7+ requires FileProvider URI (direct file:// URIs are blocked)
                apkUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        apkFile
                );
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            android.util.Log.i("GrexAPKFactory",
                "[APK Factory] ✓ Installer triggered for: " + apkFile.getName());

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("path", apkFile.getAbsolutePath());
            ret.put("sizeKb", apkFile.length() / 1024);
            call.resolve(ret);

        } catch (Exception e) {
            android.util.Log.e("GrexAPKFactory", "[APK Factory] ✗ Installer error: " + e.getMessage(), e);
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("error", "Installer launch failed: " + e.getMessage());
            call.resolve(ret);
        }
    }

    /**
     * Builds the real component bootloader HTML injected into the child APK.
     *
     * This HTML replaces the template placeholder at assets/bundle/index.html.
     * It imports mount_app from the co-injected bundle.es.js via ES module.
     *
     * IMPORTANT: setAllowFileAccessFromFileURLs(true) is set in ChildActivity,
     * enabling file:// ES module imports to work correctly on Android WebView.
     */
    private String buildBootloaderHtml(String label, String componentId) {
        // Sanitize label for safe HTML injection
        String safeLabel = label
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace("\"", "&quot;");

        // Sanitize componentId for safe JS string injection
        String safeId = componentId.replaceAll("[^a-zA-Z0-9_\\-]", "");

        return "<!DOCTYPE html>\n"
            + "<html>\n"
            + "<head>\n"
            + "  <meta charset=\"UTF-8\">\n"
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">\n"
            + "  <title>" + safeLabel + "</title>\n"
            + "  <style>\n"
            + "    * { margin: 0; padding: 0; box-sizing: border-box; }\n"
            + "    html, body { background: #090d16; width: 100%; height: 100%; overflow: hidden; }\n"
            + "    #grex-root { width: 100%; height: 100vh; overflow-y: auto; }\n"
            + "    .grex-boot { display:flex; flex-direction:column; align-items:center; justify-content:center;\n"
            + "      height:100vh; gap:16px; color:rgba(255,255,255,0.4); font-family:sans-serif; font-size:13px; }\n"
            + "    .grex-spinner { width:36px; height:36px; border:2px solid #a855f7;\n"
            + "      border-top-color:transparent; border-radius:50%;\n"
            + "      animation:spin 0.8s linear infinite; }\n"
            + "    @keyframes spin { to { transform:rotate(360deg); } }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <div id=\"grex-root\">\n"
            + "    <div class=\"grex-boot\">\n"
            + "      <div class=\"grex-spinner\"></div>\n"
            + "      <span>Loading " + safeLabel + "...</span>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "  <script type=\"module\">\n"
            + "    import { mount_app } from './bundle.es.js';\n"
            + "    const platformAPI = {\n"
            + "      isStandalone: true,\n"
            + "      componentId: '" + safeId + "',\n"
            + "      componentLabel: '" + safeLabel + "',\n"
            + "      host: 'android-child'\n"
            + "    };\n"
            + "    try {\n"
            + "      await mount_app(document.getElementById('grex-root'), platformAPI);\n"
            + "    } catch (err) {\n"
            + "      document.getElementById('grex-root').innerHTML =\n"
            + "        '<div style=\"color:#ef4444;padding:24px;font-family:monospace;font-size:12px;\">' +\n"
            + "        '<strong>[GrexChildShell] mount_app error:</strong><br>' + err.message + '</div>';\n"
            + "    }\n"
            + "  </script>\n"
            + "</body>\n"
            + "</html>\n";
    }

    /**
     * Mutates the app_name string inside AndroidManifest.xml binary string pool.
     */
    private byte[] mutateManifestLabel(byte[] manifestBytes, String newLabel) {
        String targetStr = "Grex Component";
        byte[] targetBytes = new byte[targetStr.length() * 2];
        for (int i = 0; i < targetStr.length(); i++) {
            char c = targetStr.charAt(i);
            targetBytes[i * 2] = (byte) (c & 0xFF);
            targetBytes[i * 2 + 1] = (byte) ((c >> 8) & 0xFF);
        }

        int offset = -1;
        for (int i = 0; i <= manifestBytes.length - targetBytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < targetBytes.length; j++) {
                if (manifestBytes[i + j] != targetBytes[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                offset = i;
                break;
            }
        }

        if (offset == -1) {
            return manifestBytes;
        }

        StringBuilder sb = new StringBuilder(newLabel);
        while (sb.length() < 14) {
            sb.append(" ");
        }
        if (sb.length() > 14) {
            sb.setLength(14);
        }
        String paddedLabel = sb.toString();

        byte[] result = manifestBytes.clone();
        for (int i = 0; i < 14; i++) {
            char c = paddedLabel.charAt(i);
            result[offset + i * 2] = (byte) (c & 0xFF);
            result[offset + i * 2 + 1] = (byte) ((c >> 8) & 0xFF);
        }

        return result;
    }

    /**
     * Generates a custom component icon with the Mothership logo badge in bottom-right corner.
     */
    private byte[] generateBadgedIcon(Context context, String base64IconStr, int targetSize) {
        try {
            Bitmap componentBitmap = null;
            if (base64IconStr != null && !base64IconStr.isEmpty()) {
                String cleanBase64 = base64IconStr;
                if (cleanBase64.contains(",")) {
                    cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
                }
                byte[] decoded = Base64.decode(cleanBase64, Base64.DEFAULT);
                componentBitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            }

            // Get Mothership icon badge from context
            Bitmap mothershipBadgeBitmap = null;
            try {
                android.graphics.drawable.Drawable drawable = context.getPackageManager().getApplicationIcon(context.getPackageName());
                if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                    mothershipBadgeBitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                }
            } catch (Exception ignored) {}

            Bitmap resultBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(resultBitmap);

            // 1. Pure Black Background Circle (#000000)
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(0xFF000000);
            canvas.drawCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, bgPaint);

            // 2. Draw Component Icon centered (65% inner safe zone)
            if (componentBitmap != null) {
                int iconSize = (int) (targetSize * 0.65f);
                int offset = (targetSize - iconSize) / 2;
                Rect src = new Rect(0, 0, componentBitmap.getWidth(), componentBitmap.getHeight());
                Rect dst = new Rect(offset, offset, offset + iconSize, offset + iconSize);
                canvas.drawBitmap(componentBitmap, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            } else if (mothershipBadgeBitmap != null) {
                // If no component icon provided, use mothership icon as component icon
                int iconSize = (int) (targetSize * 0.65f);
                int offset = (targetSize - iconSize) / 2;
                Rect src = new Rect(0, 0, mothershipBadgeBitmap.getWidth(), mothershipBadgeBitmap.getHeight());
                Rect dst = new Rect(offset, offset, offset + iconSize, offset + iconSize);
                canvas.drawBitmap(mothershipBadgeBitmap, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            }

            // 3. Draw Mothership Logo Badge in Bottom-Right Corner (32% size)
            if (mothershipBadgeBitmap != null) {
                int badgeSize = (int) (targetSize * 0.32f);
                int badgeX = targetSize - badgeSize - (int) (targetSize * 0.04f);
                int badgeY = targetSize - badgeSize - (int) (targetSize * 0.04f);

                // Black outer ring for badge clarity
                Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                borderPaint.setColor(0xFF000000);
                canvas.drawCircle(badgeX + badgeSize / 2f, badgeY + badgeSize / 2f, badgeSize / 2f + 4, borderPaint);

                Rect badgeSrc = new Rect(0, 0, mothershipBadgeBitmap.getWidth(), mothershipBadgeBitmap.getHeight());
                Rect badgeDst = new Rect(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize);
                canvas.drawBitmap(mothershipBadgeBitmap, badgeSrc, badgeDst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            android.util.Log.e("GrexAPKFactory", "[APK Factory] Icon badge generation error: " + e.getMessage(), e);
            return null;
        }
    }
}
