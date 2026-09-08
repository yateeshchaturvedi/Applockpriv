# PrivGuard (AppLock Priv) 🛡️

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--36)-brightgreen.svg?logo=android)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Security](https://img.shields.io/badge/Security-AES--256%20%7C%20SHA--256%20Salted-orange.svg)](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
[![Offline](https://img.shields.io/badge/Privacy-100%25%20Offline%20%7C%20Zero%20Tracking-purple.svg)](#privacy--security-first)

**PrivGuard** is a modern, privacy-first Android application locker built from the ground up with **Jetpack Compose**, **Material 3**, and **Kotlin Coroutines**. It combines multi-modal biometric authentication with an instant **zero-latency accessibility engine**, **granular notification interception**, and **stealth intruder selfie capture**.

---

## ✨ Key Features

### ⚡ 1. Zero-Latency Detection Engine
* **Instant Lock (0ms Window)**: Employs an optional `AccessibilityService` (`AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED`) to capture target app launches the exact millisecond the window switches, completely eliminating the visual flicker of locked apps.
* **Smart Polling Fallback**: Automatically falls back to a battery-efficient `UsageStatsManager` background loop if accessibility is disabled.

### 🔐 2. Multi-Modal Authentication
* **Biometric Authentication**: Seamless auto-prompt for Fingerprint and Face Unlock powered by `androidx.biometric:biometric`.
* **4-Digit Secure PIN**: Clean, animated keypad with randomized feedback and haptics.
* **Canvas Pattern Lock**: Interactive 3×3 grid pattern view custom-drawn via Compose `Canvas` with gesture tracking and dynamic error highlights.

### 🔔 3. Granular Notification Privacy
* Intercepts and shields notifications using `NotificationListenerService`.
* **Selective Masking**: Hides message previews and sender information **only** for apps marked as locked (e.g., WhatsApp, Telegram, Banking apps), while preserving system alarms, phone calls, and unrestricted apps.

### 📸 4. Stealth Intruder Detection
* **Silent Front-Camera Capture**: Powered by CameraX, secretly snaps a high-resolution photo from the front camera whenever someone fails the PIN or Pattern unlock after a configurable threshold (e.g., 2 or 3 failed attempts).
* **Intruder Gallery**: An in-app photo gallery with timestamped capture cards, full-screen zoomable preview, and per-app attempt logs (identifying exactly which app the intruder tried to open).

### ⏱️ 5. Smart Re-lock & Schedule Management
* **Flexible Re-lock Policies**:
  * *Immediately* on exit.
  * *After Screen Off*.
  * *Grace Period* (custom unlock duration between 1 to 15 minutes).
* **Custom Time Schedules**: Configure auto-lock windows (e.g., Work hours 09:00–17:00, Study sessions, or Night privacy).

### 🎨 6. Modern Material 3 Interface
* Built 100% in **Jetpack Compose** adhering to Material Design 3 guidelines.
* **Search & Filters**: Quick-search installed apps with categorized filter chips (`All`, `Locked`, `User`, `System`) and sorting (`A–Z`, `Z–A`, `Locked First`).
* **Dynamic Themes**: Seamless switching between System Default, Dark Mode, and Light Mode.
* **Guided Onboarding**: A 5-step setup wizard assisting users with permissions, PIN creation, and security questions.

---

## 🔒 Privacy & Security Model

PrivGuard is built with an uncompromising commitment to privacy:
* **100% Offline**: PrivGuard does **not** declare or request the `android.permission.INTERNET` permission. Zero analytics, zero ad SDKs, and zero telemetry.
* **Encrypted Storage**: Sensitive states and locked package lists are stored via AndroidX `EncryptedSharedPreferences` backed by hardware-backed **AES-256 GCM** and the Android Keystore.
* **Salted SHA-256 Hashing**: PINs and security answers are never stored in plaintext. They are cryptographically hashed with a unique per-device salt.
* **App Self-Protection**: Prevents unauthorized uninstallation and blocks intruders from killing the lock service.

---

## 🛠️ Tech Stack & Architecture

PrivGuard follows standard Android architecture guidelines with unidirectional data flow (UDF):

* **Architecture**: MVVM (Model-View-ViewModel) + StateFlow + Repository pattern
* **UI**: Jetpack Compose, Material 3, Compose Icons Extended, Accompanist
* **Concurrency**: Kotlin Coroutines & `StateFlow`
* **Camera**: AndroidX CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
* **Security**: AndroidX Security Crypto (`MasterKeys`, `EncryptedSharedPreferences`), Java Cryptography Architecture (JCA)
* **Background Services**:
  * `AppLockService`: Foreground monitoring service with battery-optimized wake locks and notification channels.
  * `AppLockAccessibilityService`: High-speed window transition monitor.
  * `AppLockNotificationListenerService`: Granular notification content filter.
* **Testing**: JUnit 4, AndroidX Test Runner, Robolectric / Espresso ready.

---

## 📁 Codebase Structure

```
app/src/main/java/com/yateeshpriv/applockpriv/
├── MainActivity.kt                       # Compose navigation host & entry point
├── onboarding/
│   └── OnboardingScreen.kt               # 5-step initial setup wizard
├── service/
│   ├── AppLockService.kt                 # Background monitoring & lock trigger
│   ├── AppLockAccessibilityService.kt    # Zero-latency instant detection
│   ├── AppLockNotificationListenerService.kt # Notification privacy filter
│   ├── LockScreenActivity.kt             # Overlay lock screen host
│   ├── BootReceiver.kt                   # Auto-start on device reboot
│   └── DeviceAdminManager.kt             # Anti-uninstall & self-protection
├── ui/
│   ├── AppListScreen.kt                  # Main dashboard with filters & search
│   ├── SettingsScreen.kt                 # Security, grace period & schedule settings
│   ├── LockScreen.kt                     # PIN, Biometric & Pattern unlock UI
│   ├── IntruderGalleryActivity.kt        # Intruder photos & activity log viewer
│   ├── PatternLockView.kt                # Custom 3x3 Canvas pattern view
│   └── theme/                            # Material 3 colors, typography, theme
├── util/
│   ├── CryptoUtils.kt                    # Salt generation & SHA-256 hashing
│   ├── PrefsHelper.kt                    # EncryptedSharedPreferences wrapper
│   ├── NotificationHelper.kt             # Foreground service & privacy channels
│   └── ScheduleHelper.kt                 # Time-range calculation & scheduling
└── viewmodel/
    └── AppListViewModel.kt               # Installed app state, sorting & filtering
```

---

## 📋 Required Android Permissions

| Permission | Purpose |
| :--- | :--- |
| `SYSTEM_ALERT_WINDOW` | Displays the secure lock overlay on top of protected applications. |
| `PACKAGE_USAGE_STATS` | Detects foreground app switching (polling engine fallback). |
| `BIND_ACCESSIBILITY_SERVICE` | Powers instant zero-latency detection without polling. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Enables per-app notification preview masking. |
| `CAMERA` | Captures silent intruder selfies upon failed unlock attempts. |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Provides fingerprint and face unlock capabilities. |
| `RECEIVE_BOOT_COMPLETED` | Automatically restarts the protection service when the phone reboots. |
| `FOREGROUND_SERVICE` | Keeps the protection service persistently active in the background. |

---

## 🚀 Getting Started & Building

### Prerequisites
* **Android Studio**: Android Studio Ladybug (2024.2+) or newer
* **JDK**: Version 17 or Version 21
* **Android SDK**: Compile SDK `36`, Minimum SDK `26` (Android 8.0 Oreo+)

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/yateeshchaturvedi/Applockpriv.git
   cd Applockpriv
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run the unit test suite:
   ```bash
   ./gradlew test
   ```
4. Install to an attached device or emulator:
   ```bash
   ./gradlew installDebug
   ```

---

## 🧪 Unit Testing

PrivGuard includes dedicated unit tests covering security algorithms, encryption, scheduling math, and intruder logging:

```bash
./gradlew test
```
* `CryptoUtilsTest`: Validates SHA-256 hashing, unique salt generation, and verification.
* `ScheduleHelperTest`: Tests time-window parsing, overnight spans (e.g. 22:00 to 06:00), and edge-case schedules.
* `IntruderLogTest`: Validates JSON serialization and formatting of intruder attempt logs.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — see the LICENSE file for details.
