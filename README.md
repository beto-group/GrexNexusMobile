# 📱 GrexNexusMobile — Datacore Sovereign Inspector & Mobile Runtime

> **Version**: `v37.0.0`  
> **Package Name**: `com.datacore.sovereigninspector`  
> **Architecture**: Capacitor Android Native Shell + Child-Shell Standalone Subsystem  

`GrexNexusMobile` is the official native Android runtime and host container for the **Datacore Grex Sovereign Ecosystem**. It provides a high-performance, zero-latency mobile environment to dynamically load, execute, and monitor Sovereign Component Bundles (`.grex` / React micro-apps) natively on Android devices.

---

## 🌟 Key Architectural Features

### 1. 🚀 Native Background Self-Updater (`HomeScreenPlugin.java`)
- **Zero-RAM Memory Overhead**: Binary `.apk` downloads stream directly to disk using a native 8KB byte buffer (`HttpURLConnection`), bypassing JavaScript WebWorker Base64 memory serialization freezes.
- **Redirect Following**: Follows HTTP 302/301 location redirects across GitHub Releases and S3 buckets.
- **Native PackageInstaller Execution**: Automatically triggers Android's native `PackageInstaller` intent once download completes.

### 2. 🛡️ Native Single-Page Hardware Back Navigation
- Intercepts Android hardware 3-button navigation bar taps and edge swipe-back gestures via `OnBackPressedCallback` in `MainActivity.java`.
- Prevents Android OS from accidentally closing or minimizing the app when viewing micro-component tabs (`mobileTab === 'active'`), smoothly routing back to the **Apps Store**.

### 3. 🧩 Child-Shell Dynamic Packaging Subsystem (`child-shell/`)
- Contains a standalone, lightweight Android launcher template.
- Used to wrap individual Datacore micro-components into fully independent standalone `.apk` packages on demand.

### 4. 📊 Native Android App Widgets
Built-in native Android Home Screen Widgets for real-time monitoring and quick actions:
- `DashboardWidgetProvider.java` — Multi-metric cluster overview.
- `CompactWidgetProvider.java` — Minimalist quick-launch launcher widget.
- `FullBannerWidgetProvider.java` — Wide status banner display.
- `CurrencyWidgetProvider.java` — Live exchange rate ticker widget.

---

## 📁 Repository Structure

```
GrexNexusMobile/
├── host/
│   └── android/                     # Mothership Host Android Project (com.datacore.sovereigninspector)
│       ├── app/src/main/java/       # Native Java Plugins & Activity Overrides
│       │   └── com/datacore/sovereigninspector/
│       │       ├── MainActivity.java
│       │       ├── HomeScreenPlugin.java
│       │       ├── DashboardWidgetProvider.java
│       │       └── ...
│       └── app/src/main/assets/     # Embedded Web Dist & Child-Shell Template
├── child-shell/                     # Standalone Micro-App Launcher Template
└── README.md                        # Project Documentation
```

---

## 🛠️ Build & Installation Setup

### Prerequisites
- **Java Development Kit (JDK)**: JDK 17 or JDK 21 (Android Studio JBR)
- **Android SDK**: API Level 34+
- **Gradle**: Gradle 8.9

### Step-by-Step Build Workflow

1. **Rebuild Host Web Assets**:
   ```bash
   cd ../GrexNexusWeb
   npm run build
   ```

2. **Sync Assets to Host Android Shell**:
   ```bash
   rm -rf host/android/app/src/main/assets/public
   mkdir -p host/android/app/src/main/assets/public
   cp -R ../GrexNexusWeb/dist/* host/android/app/src/main/assets/public/
   ```

3. **Build Child-Shell Template**:
   ```bash
   cd child-shell
   ./gradlew assembleDebug
   cp app/build/outputs/apk/debug/app-debug.apk ../host/android/app/src/main/assets/child_shell_template.apk
   ```

4. **Build Mothership APK**:
   ```bash
   cd ../host/android
   ./gradlew assembleDebug
   ```

5. **Deploy & Launch on Android Device / Emulator**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell monkey -p com.datacore.sovereigninspector -c android.intent.category.LAUNCHER 1
   ```

---

## 🔌 Native Plugin Bridge API (`HomeScreenPlugin.java`)

| Method Name | Description | Parameters |
| :--- | :--- | :--- |
| `downloadAndInstallApk` | Downloads binary `.apk` in native background thread and triggers installer | `{ url: "https://..." }` |
| `hotReloadBundle` | Dynamic hot-reloading of sovereign component JS bundle | `{ bundlePath: "..." }` |
| `updateWidgetData` | Updates native Android launcher home screen widget state | `{ widgetData: {...} }` |

---

## 📜 License

**Datacore Sovereign Infrastructure** — All Rights Reserved.  
Maintained by **BETO Group**.
