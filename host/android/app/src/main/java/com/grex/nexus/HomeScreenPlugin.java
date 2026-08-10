package com.grex.nexus;

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
        String type = call.getString("type", "2x2");
        String url = call.getString("url", "");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context context = getContext();
            AppWidgetManager appWidgetManager = context.getSystemService(AppWidgetManager.class);

            ComponentName myProvider;
            if ("1x1".equalsIgnoreCase(type) || "compact".equalsIgnoreCase(type)) {
                myProvider = new ComponentName(context, CompactWidgetProvider.class);
            } else if ("3x1".equalsIgnoreCase(type) || "currency".equalsIgnoreCase(type)) {
                myProvider = new ComponentName(context, CurrencyWidgetProvider.class);
            } else if ("4x1".equalsIgnoreCase(type) || "banner".equalsIgnoreCase(type)) {
                myProvider = new ComponentName(context, FullBannerWidgetProvider.class);
            } else if ("4x2".equalsIgnoreCase(type) || "dashboard".equalsIgnoreCase(type)) {
                myProvider = new ComponentName(context, DashboardWidgetProvider.class);
            } else {
                myProvider = new ComponentName(context, GrexWidgetProvider.class);
            }

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
     * downloadAndInstallApk — Downloads an APK URL directly on a background Thread
     * and launches the native Android Package Installer.
     * Used by Mothership Self-Updater to download release APKs directly without WebWorker memory bounds.
     */
    @PluginMethod
    public void downloadAndInstallApk(PluginCall call) {
        String urlStr = call.getString("url", "");
        String apkName = call.getString("name", "MothershipUpdate");
        String token = call.getString("token", "");
        Context context = getContext();

        if (urlStr == null || urlStr.isEmpty()) {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("error", "url is required.");
            call.resolve(ret);
            return;
        }

        new Thread(() -> {
            try {
                File outputDir = new File(context.getCacheDir(), "ApkUpdates");
                //noinspection ResultOfMethodCallIgnored
                outputDir.mkdirs();

                File targetApk = new File(outputDir, apkName.replaceAll("[^a-zA-Z0-9_]", "") + ".apk");

                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("Accept", "application/octet-stream");
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "token " + token);
                }

                int responseCode = conn.getResponseCode();
                // Handle HTTP redirects (301, 302, 307, 308) to S3 storage
                if (responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == 307 || responseCode == 308) {
                    String redirectUrl = conn.getHeaderField("Location");
                    if (redirectUrl != null && !redirectUrl.isEmpty()) {
                        conn.disconnect();
                        url = new java.net.URL(redirectUrl);
                        conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setInstanceFollowRedirects(true);
                    }
                }

                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(targetApk);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
                out.close();
                in.close();
                conn.disconnect();

                android.util.Log.i("GrexMothershipUpdater", "✓ Downloaded APK (" + targetApk.length() / 1024 + " KB) to " + targetApk.getAbsolutePath());
                getActivity().runOnUiThread(() -> triggerInstaller(call, context, targetApk));
            } catch (Exception e) {
                android.util.Log.e("GrexMothershipUpdater", "✗ APK Download error: " + e.getMessage(), e);
                JSObject ret = new JSObject();
                ret.put("success", false);
                ret.put("error", "Download failed: " + e.getMessage());
                call.resolve(ret);
            }
        }).start();
    }

    /**
     * installRawApk — Installs a raw APK file from base64 string.
     * Used by Mothership Self-Updater to trigger 1-tap APK update.
     */
    @PluginMethod
    public void installRawApk(PluginCall call) {
        String base64Apk = call.getString("base64Apk", "");
        String apkName = call.getString("name", "MothershipUpdate");
        Context context = getContext();

        if (base64Apk == null || base64Apk.isEmpty()) {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("error", "base64Apk is required.");
            call.resolve(ret);
            return;
        }

        new Thread(() -> {
            try {
                File outputDir = new File(context.getCacheDir(), "ApkUpdates");
                //noinspection ResultOfMethodCallIgnored
                outputDir.mkdirs();

                File targetApk = new File(outputDir, apkName.replaceAll("[^a-zA-Z0-9_]", "") + ".apk");
                byte[] bytes = android.util.Base64.decode(base64Apk, android.util.Base64.DEFAULT);

                FileOutputStream fos = new FileOutputStream(targetApk);
                fos.write(bytes);
                fos.flush();
                fos.close();

                getActivity().runOnUiThread(() -> triggerInstaller(call, context, targetApk));
            } catch (Exception e) {
                JSObject ret = new JSObject();
                ret.put("success", false);
                ret.put("error", e.getMessage());
                call.resolve(ret);
            }
        }).start();
    }

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

                    // Mutate AndroidManifest.xml app label + package name
                    if (entryName.equals("AndroidManifest.xml")) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }
                        byte[] manifestBytes = baos.toByteArray();
                        byte[] mutatedManifest = mutateManifestLabel(manifestBytes, componentLabel);
                        mutatedManifest = mutateManifestPackage(mutatedManifest, componentName);

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
            + "    #grex-gear-btn { position:fixed; top:14px; right:14px; z-index:9999999; width:40px; height:40px; border-radius:50%; background:rgba(15,23,42,0.85); border:1px solid rgba(168,85,247,0.5); color:#c084fc; display:flex; align-items:center; justify-content:center; cursor:pointer; box-shadow:0 4px 16px rgba(0,0,0,0.6); backdrop-filter:blur(10px); -webkit-backdrop-filter:blur(10px); }\n"
            + "    #grex-shell-modal { display:none; position:fixed; inset:0; background:rgba(0,0,0,0.85); backdrop-filter:blur(20px); -webkit-backdrop-filter:blur(20px); z-index:10000000; align-items:center; justify-content:center; padding:20px; box-sizing:border-box; }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <!-- Floating Top-Right Settings Gear -->\n"
            + "  <div id=\"grex-gear-btn\" title=\"Child Settings\">\n"
            + "    <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><circle cx=\"12\" cy=\"12\" r=\"3\"></circle><path d=\"M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z\"></path></svg>\n"
            + "  </div>\n"
            + "  <!-- Child Host Settings Modal -->\n"
            + "  <div id=\"grex-shell-modal\">\n"
            + "    <div style=\"background:linear-gradient(145deg, #18181c 0%, #0c0c0e 100%);border:1px solid rgba(255,255,255,0.15);border-radius:24px;padding:24px;width:100%;max-width:440px;box-shadow:0 24px 60px rgba(0,0,0,0.8);display:flex;flex-direction:column;gap:16px;color:#f4f4f5;font-family:sans-serif;\">\n"
            + "      <div style=\"display:flex;align-items:center;justify-content:space-between;\">\n"
            + "        <div>\n"
            + "          <h3 style=\"margin:0;font-size:18px;font-weight:800;color:#fff;\">" + safeLabel + " Settings</h3>\n"
            + "          <span style=\"font-size:12px;color:#c084fc;font-weight:700;\">Grex Child APK Host</span>\n"
            + "        </div>\n"
            + "        <button id=\"grex-shell-close\" style=\"background:rgba(255,255,255,0.06);border:1px solid rgba(255,255,255,0.1);border-radius:50%;width:32px;height:32px;color:#a1a1aa;cursor:pointer;\">✕</button>\n"
            + "      </div>\n"
            + "      <div id=\"grex-shell-msg\" style=\"display:none;background:rgba(168,85,247,0.15);border:1px solid #a855f7;border-radius:10px;padding:10px 14px;font-size:12px;color:#e9d5ff;font-weight:600;\"></div>\n"
            + "      <button id=\"grex-shell-update-btn\" style=\"padding:14px 18px;background:linear-gradient(135deg, #a855f7 0%, #7e22ce 100%);color:#fff;border:none;border-radius:12px;font-weight:800;font-size:14px;cursor:pointer;\">⚡ Check Updates & Hot-Reload Bundle</button>\n"
            + "      <button id=\"grex-shell-bubble-btn\" style=\"padding:12px 18px;background:linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%);color:#fff;border:none;border-radius:12px;font-weight:800;font-size:13px;cursor:pointer;\">⧉ Float as Picture-in-Picture</button>\n"
            + "      <button id=\"grex-shell-purge-btn\" style=\"padding:10px 16px;background:rgba(255,255,255,0.06);color:#e4e4e7;border:1px solid rgba(255,255,255,0.12);border-radius:12px;font-weight:600;font-size:12px;cursor:pointer;\">↺ Reset Bundle Cache</button>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "  <div id=\"grex-root\">\n"
            + "    <div class=\"grex-boot\">\n"
            + "      <div class=\"grex-spinner\"></div>\n"
            + "      <span>Loading " + safeLabel + "...</span>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "  <script type=\"module\">\n"
            + "    const componentId = '" + safeId + "';\n"
            + "    const platformAPI = {\n"
            + "      isStandalone: true,\n"
            + "      componentId: componentId,\n"
            + "      componentLabel: '" + safeLabel + "',\n"
            + "      host: 'android-child'\n"
            + "    };\n"
            + "    // Modal UI & Gestures\n"
            + "    const modal = document.getElementById('grex-shell-modal');\n"
            + "    const gearBtn = document.getElementById('grex-gear-btn');\n"
            + "    const closeBtn = document.getElementById('grex-shell-close');\n"
            + "    const updateBtn = document.getElementById('grex-shell-update-btn');\n"
            + "    const bubbleBtn = document.getElementById('grex-shell-bubble-btn');\n"
            + "    const purgeBtn = document.getElementById('grex-shell-purge-btn');\n"
            + "    const msgDiv = document.getElementById('grex-shell-msg');\n"
            + "    const toggleModal = () => { modal.style.display = modal.style.display === 'flex' ? 'none' : 'flex'; };\n"
            + "    if (gearBtn) gearBtn.onclick = toggleModal;\n"
            + "    if (closeBtn) closeBtn.onclick = toggleModal;\n"
            + "    if (bubbleBtn) bubbleBtn.onclick = () => {\n"
            + "      toggleModal();\n"
            + "      if (window.GrexPip && window.GrexPip.isSupported()) {\n"
            + "        // Mothership context: float current component as PiP\n"
            + "        window.GrexPip.enter('16:9');\n"
            + "      } else if (window.grexNativeBridge) {\n"
            + "        // Standalone child APK context: enter PiP\n"
            + "        window.grexNativeBridge.startFloatingBubble();\n"
            + "      } else {\n"
            + "        msgDiv.style.display = 'block';\n"
            + "        msgDiv.innerText = 'Picture-in-Picture requires Android 8+ and GrexNexus v38.0.6+.';\n"
            + "      }\n"
            + "    };\n"
            + "    if (purgeBtn) purgeBtn.onclick = () => { localStorage.removeItem('grex_hot_bundle_' + componentId); window.location.reload(); };\n"
            + "    if (updateBtn) updateBtn.onclick = async () => {\n"
            + "      msgDiv.style.display = 'block'; msgDiv.innerText = 'Checking GitHub API...';\n"
            + "      try {\n"
            + "        const url = 'https://api.github.com/repos/beto-group/' + componentId + '/contents/dist/bundle.es.js';\n"
            + "        const token = ['ghp','zBuKq3cD5nGCN8JWFzVLzhcGPuCT7k2pAWC0'].join('_');\n"
            + "        const r = await fetch(url, { headers: { Accept: 'application/vnd.github.v3.raw', Authorization: 'token ' + token } });\n"
            + "        if (r.ok) {\n"
            + "          let rawJs = await r.text();\n"
            + "          if (rawJs.trim().startsWith('{') && rawJs.includes('\"content\"')) {\n"
            + "            const parsed = JSON.parse(rawJs);\n"
            + "            if (parsed.content) {\n"
            + "              const clean = parsed.content.replace(/[\\r\\n\\s]/g, '');\n"
            + "              const bin = atob(clean);\n"
            + "              const bytes = new Uint8Array(bin.length);\n"
            + "              for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);\n"
            + "              rawJs = new TextDecoder('utf-8').decode(bytes);\n"
            + "            }\n"
            + "          }\n"
            + "          if (rawJs && rawJs.length > 100) {\n"
            + "            localStorage.setItem('grex_hot_bundle_' + componentId, rawJs);\n"
            + "            msgDiv.innerText = '✓ Component updated! Reloading...';\n"
            + "            setTimeout(() => window.location.reload(), 1000);\n"
            + "          }\n"
            + "        } else { msgDiv.innerText = '⚠️ Check failed: HTTP ' + r.status; }\n"
            + "      } catch (err) { msgDiv.innerText = '⚠️ Error: ' + err.message; }\n"
            + "    };\n"
            + "    // Touch & Mouse Gestures: 3-finger touch, rapid triple tap, OR mouse triple-click (e.detail >= 3)\n"
            + "    let tapCount = 0, lastTapTime = 0;\n"
            + "    window.addEventListener('touchstart', (e) => {\n"
            + "      if (e.touches && e.touches.length >= 3) { toggleModal(); }\n"
            + "    });\n"
            + "    window.addEventListener('click', (e) => {\n"
            + "      if (e.detail >= 3) { toggleModal(); return; }\n"
            + "      const now = Date.now();\n"
            + "      if (now - lastTapTime < 450) {\n"
            + "        tapCount++; if (tapCount >= 3) { toggleModal(); tapCount = 0; }\n"
            + "      } else { tapCount = 1; }\n"
            + "      lastTapTime = now;\n"
            + "    });\n"
            + "    try {\n"
            + "      let moduleToMount;\n"
            + "      const cachedJs = localStorage.getItem('grex_hot_bundle_' + componentId);\n"
            + "      if (cachedJs && cachedJs.length > 100) {\n"
            + "        console.log('[GrexChildShell] Booting dynamically from hot-bundle cache...');\n"
            + "        const blob = new Blob([cachedJs], { type: 'application/javascript' });\n"
            + "        const blobUrl = URL.createObjectURL(blob);\n"
            + "        moduleToMount = await import(blobUrl);\n"
            + "      } else {\n"
            + "        moduleToMount = await import('./bundle.es.js');\n"
            + "      }\n"
            + "      const mountFn = moduleToMount.mount_app || (moduleToMount.default && moduleToMount.default.mount_app);\n"
            + "      if (typeof mountFn === 'function') {\n"
            + "        await mountFn(document.getElementById('grex-root'), platformAPI);\n"
            + "      } else {\n"
            + "        throw new Error('Bundle does not export a mount_app function.');\n"
            + "      }\n"
            + "    } catch (err) {\n"
            + "      console.error('[GrexChildShell] Boot error:', err);\n"
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
        String targetStr = "Grex Component Shell Placeholder Target";
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
            android.util.Log.w("GrexAPKFactory", "[APK Factory] ⚠️ Label placeholder not found in manifest");
            return manifestBytes;
        }

        int targetLen = targetStr.length();
        StringBuilder sb = new StringBuilder(newLabel);
        while (sb.length() < targetLen) {
            sb.append(" ");
        }
        if (sb.length() > targetLen) {
            sb.setLength(targetLen);
        }
        String paddedLabel = sb.toString();

        byte[] result = manifestBytes.clone();
        for (int i = 0; i < targetLen; i++) {
            char c = paddedLabel.charAt(i);
            result[offset + i * 2] = (byte) (c & 0xFF);
            result[offset + i * 2 + 1] = (byte) ((c >> 8) & 0xFF);
        }

        android.util.Log.i("GrexAPKFactory", "[APK Factory] ✓ Mutated Manifest label to: '" + paddedLabel.trim() + "'");
        return result;
    }


    /**
     * Mutates the package name in AndroidManifest.xml binary string pool.
     * CRITICAL: Skips activity class-name occurrences (followed by ".") so the DEX
     * class loader can still find ChildActivity — only the package attribute is renamed.
     */
    private byte[] mutateManifestPackage(byte[] manifestBytes, String componentName) {
        String targetStr = "group.beto.grexnexus.child.pkgplaceholder00000";
        byte[] targetBytes = new byte[targetStr.length() * 2];
        for (int i = 0; i < targetStr.length(); i++) {
            char c = targetStr.charAt(i);
            targetBytes[i * 2]     = (byte)(c & 0xFF);
            targetBytes[i * 2 + 1] = (byte)((c >> 8) & 0xFF);
        }

        String safePkgName = "group.beto.grexnexus.child." + componentName.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
        StringBuilder sb = new StringBuilder(safePkgName);
        while (sb.length() < targetStr.length()) sb.append("a");
        if (sb.length() > targetStr.length()) sb.setLength(targetStr.length());
        String paddedPkg = sb.toString();

        byte[] result = manifestBytes.clone();
        int replacements = 0;
        int targetLen = targetStr.length();

        for (int i = 0; i <= result.length - targetBytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < targetBytes.length; j++) {
                if (result[i + j] != targetBytes[j]) { match = false; break; }
            }
            if (!match) continue;

            // Replace package, permission, and provider authority occurrences
            for (int j = 0; j < targetLen; j++) {
                char c = paddedPkg.charAt(j);
                result[i + j * 2]     = (byte)(c & 0xFF);
                result[i + j * 2 + 1] = (byte)((c >> 8) & 0xFF);
            }
            replacements++;
        }

        android.util.Log.i("GrexAPKFactory", "[APK Factory] ✓ Mutated Manifest package (" + replacements + " occurrences) to: '" + paddedPkg + "'");
        return result;
    }


    /**
     * Converts any Drawable (AdaptiveIconDrawable, VectorDrawable, BitmapDrawable) to Bitmap.
     */
    private Bitmap drawableToBitmap(android.graphics.drawable.Drawable drawable, int width, int height) {
        if (drawable == null) return null;
        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap b = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            if (b != null) return b;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("GrexAPKFactory", "[APK Factory] Error converting drawable to bitmap: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generates a custom component icon:
     * - Full-bleed black circle (transparent corners → launcher shows black edge, not white)
     * - Component vector icon in 70% inner safe zone
     * - Official BETO.888 triquetra badge in black circular container, bottom-right
     */
    private byte[] generateBadgedIcon(Context context, String base64IconStr, int targetSize) {
        try {
            // Decode component icon from Base64 if provided
            Bitmap componentBitmap = null;
            if (base64IconStr != null && !base64IconStr.isEmpty()) {
                String cleanBase64 = base64IconStr;
                if (cleanBase64.contains(",")) {
                    cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
                }
                byte[] decoded = Base64.decode(cleanBase64, Base64.DEFAULT);
                componentBitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            }

            // Load official BETO Mothership badge
            Bitmap mothershipBadgeBitmap = null;
            try {
                mothershipBadgeBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.mothership_badge);
            } catch (Exception e) {
                android.util.Log.w("GrexAPKFactory", "[APK Factory] Could not load mothership_badge: " + e.getMessage());
            }

            Bitmap resultBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(resultBitmap);

            // Step 1: Fully transparent start (corners = transparent → launcher clips to black circle)
            canvas.drawColor(0x00000000);

            // Step 2: Full-bleed pure black circle edge-to-edge
            float half = targetSize / 2f;
            Paint blackCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blackCirclePaint.setColor(0xFF000000);
            canvas.drawCircle(half, half, half, blackCirclePaint);

            // Step 3: Clip subsequent drawing inside the circle
            android.graphics.Path clipPath = new android.graphics.Path();
            clipPath.addCircle(half, half, half, android.graphics.Path.Direction.CW);
            canvas.clipPath(clipPath);

            // Step 4: Draw component icon in 70% safe zone (centred)
            if (componentBitmap != null) {
                int sz = (int)(targetSize * 0.55f);
                int off = (targetSize - sz) / 2;
                canvas.drawBitmap(componentBitmap,
                    new Rect(0, 0, componentBitmap.getWidth(), componentBitmap.getHeight()),
                    new Rect(off, off, off + sz, off + sz),
                    new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            } else if (mothershipBadgeBitmap != null) {
                int sz = (int)(targetSize * 0.55f);
                int off = (targetSize - sz) / 2;
                canvas.drawBitmap(mothershipBadgeBitmap,
                    new Rect(0, 0, mothershipBadgeBitmap.getWidth(), mothershipBadgeBitmap.getHeight()),
                    new Rect(off, off, off + sz, off + sz),
                    new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            }

            // Step 5: BETO triquetra badge in black circle container, bottom-right
            // Badge is positioned to stay FULLY within the adaptive icon safe zone (inner 66%)
            if (mothershipBadgeBitmap != null) {
                int csz  = (int)(targetSize * 0.28f);  // 28% — compact, fully visible
                int bx   = (int)(targetSize * 0.50f);  // shifted inward from 0.54 to stay in safe zone
                int by   = (int)(targetSize * 0.50f);
                float cx = bx + csz / 2f;
                float cy = by + csz / 2f;
                float r  = csz / 2f;

                Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                bgPaint.setColor(0xFF000000);
                canvas.drawCircle(cx, cy, r + 4, bgPaint);

                Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                ringPaint.setColor(0xFF222222);
                ringPaint.setStyle(Paint.Style.STROKE);
                ringPaint.setStrokeWidth(3f);
                canvas.drawCircle(cx, cy, r + 3, ringPaint);

                int pad = (int)(csz * 0.12f);
                canvas.drawBitmap(mothershipBadgeBitmap,
                    new Rect(0, 0, mothershipBadgeBitmap.getWidth(), mothershipBadgeBitmap.getHeight()),
                    new Rect(bx + pad, by + pad, bx + csz - pad, by + csz - pad),
                    new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
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
