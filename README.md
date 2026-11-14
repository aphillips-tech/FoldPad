# FoldPadHID (com.example.foldpadhid)

Turns a Samsung Galaxy Z Fold 5 (or any Android 9+ device) into a wireless composite Bluetooth HID Trackpad, Keyboard, and Cover-Screen Macro Deck.

## Option 1: Build APK in the Cloud (No Android Studio Needed)
1. Push this extracted folder to a GitHub repository (public or private).
2. Open the **Actions** tab and run **Build FoldPadHID APK** (or push to `main`).
3. Download the compiled `FoldPadHID-debug-apk` artifact containing `app-debug.apk` and install it onto your Galaxy Z Fold 5.

## Option 2: Build APK Locally
1. Open this folder in Android Studio Ladybug / Meerkat (JDK 17) and let Gradle sync (`compileSdk = 35`, `compose-bom:2024.10.01`).
2. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)** (or run `gradle assembleDebug`).
3. Install via `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Galaxy Z Fold 5 Dual-Screen Setup
1. Grant Nearby Devices / Bluetooth permissions on launch.
2. Pair your phone with your macOS, Windows, or Linux host via Bluetooth settings.
3. Start **Shizuku** (API 13+) on your Fold 5, tap **Cover Dual-Screen**, and approve the Shizuku permission prompt so `DisplayControlUserService` keeps the 6.2" cover screen active while unfolded.
