# 📱 GrexNexusMobile — Datacore Sovereign Inspector & Mobile Runtime (Android & iOS)

> **Version**: `v38.0.0`  
> **Bundle Identifier / Package Name**: `com.grex.nexus`  
> **Architecture**: Capacitor Android & iOS Native Shell + Child-Shell Standalone Subsystem  

`GrexNexusMobile` is the official native mobile runtime and host container for the **Datacore Grex Sovereign Ecosystem**. It provides a high-performance, zero-latency mobile environment to dynamically load, execute, and monitor Sovereign Component Bundles (`.grex` / React micro-apps) natively on Android and iOS devices.

---

## 🌟 Key Architectural Features

### 1. 🚀 Native Background Self-Updater (`HomeScreenPlugin.java`)
- **Zero-RAM Memory Overhead**: Binary `.apk` downloads stream directly to disk using a native 8KB byte buffer (`HttpURLConnection`), bypassing JavaScript WebWorker Base64 memory serialization freezes.
- **Redirect Following**: Follows HTTP 302/301 location redirects across GitHub Releases and S3 buckets.
- **Native PackageInstaller Execution**: Automatically triggers Android's native `PackageInstaller` intent once download completes.

### 2. 🛡️ Native Single-Page Hardware Back Navigation
- Intercepts Android hardware 3-button navigation bar taps and edge swipe-back gestures via `OnBackPressedCallback` in `MainActivity.java`.
- Prevents Android OS from accidentally closing or minimizing the app when viewing micro-component tabs (`mobileTab === 'active'`), smoothly routing back to the **Apps Store**.

### 3. 🍎 iOS & Apple Simulator Support
- Native Capacitor iOS platform (`host/ios/App` & `ios/App`).
- Integrated with CocoaPods 1.17+ and Xcode 16+ for compiling `.app` bundles directly to Apple Simulators (e.g. `iPhone 16 Plus`, `iPhone 16 Pro`).

### 4. 🤖 iOS Simulator MCP Server Integration (`xcodebuildmcp` / `ios-simulator-mcp`)
Allows AI coding agents to control, build, test, and inspect the app live on Apple Simulators via Model Context Protocol (MCP):
- Boot and shut down iOS simulators (`boot_simulator`).
- Build and install `.app` binaries (`xcodebuild` / `install_app`).
- Capture screenshots and inspect UI element trees (`take_screenshot`).
- Perform tap, swipe, and text input gestures automatically.

---

## 📁 Repository Structure

```
GrexNexusMobile/
├── host/
│   ├── android/                     # Mothership Host Android Project (com.grex.nexus)
│   │   ├── app/src/main/java/       # Native Java Plugins & Activity Overrides
│   │   └── app/src/main/assets/     # Embedded Web Dist & Child-Shell Template
│   └── ios/                         # Mothership Host iOS Xcode Workspace
│       └── App/                     # App.xcworkspace & Podfile (iOS 15.0+)
├── child-shell/                     # Standalone Micro-App Launcher Template
└── README.md                        # Project Documentation
```

---

## 🛠️ Build & Installation Setup

### Prerequisites
- **macOS**: macOS 14+ (Sonoma) or 15+ (Sequoia)
- **Xcode**: Xcode 16+ & Xcode Command Line Tools
- **CocoaPods**: `pod` 1.17.0+ (`brew install cocoapods`)
- **JDK**: JDK 17 / JDK 21 (for Android builds)
- **Android SDK**: API Level 34+

### Android Build Workflow

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

3. **Build Mothership APK**:
   ```bash
   cd host/android
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

### 🍎 iOS & Apple Simulator Build Workflow

1. **Build Web Dist & Sync to iOS Container**:
   ```bash
   cd ../GrexNexusWeb
   npm run build
   npx cap sync ios
   ```

2. **Install CocoaPods Dependencies**:
   ```bash
   cd ios/App
   export PATH="/opt/homebrew/bin:$PATH"
   export DEVELOPER_DIR="/Applications/Xcode.app/Contents/Developer"
   pod install
   ```

3. **Compile & Run on Booted Apple Simulator**:
   ```bash
   xcodebuild -workspace App.xcworkspace -scheme App -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16 Plus' -derivedDataPath build build
   xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/App.app
   xcrun simctl launch booted com.grex.nexus
   ```

---

## 🔌 iOS Simulator MCP Server Configuration

To connect an AI assistant to the Apple Simulator via MCP, add the following to your agent's MCP configuration (`mcp.json` or equivalent):

```json
{
  "mcpServers": {
    "xcodebuild": {
      "command": "npx",
      "args": [
        "-y",
        "xcodebuildmcp"
      ],
      "env": {
        "DEVELOPER_DIR": "/Applications/Xcode.app/Contents/Developer",
        "PATH": "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
      }
    }
  }
}
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
