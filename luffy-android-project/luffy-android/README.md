# Luffy – Android app (made by Manjit)

## Build the APK
1. Install Android Studio (https://developer.android.com/studio).
2. File > Open > choose this folder. Wait for Gradle sync (needs internet the first time; accept any "upgrade" prompt).
3. Build > Build Bundle(s) / APK(s) > Build APK(s). Click "locate" when it finishes.
   The file is app/build/outputs/apk/debug/app-debug.apk. Copy it to your phone and install it (allow "install unknown apps").

## First run
- Allow microphone, phone and notifications.
- Tap "API key" and paste a key from https://console.anthropic.com (needed only for answering questions; each answer costs a little).
- Turn on the wake word and allow "Display over other apps" so Luffy can open apps and make calls from the background.
- On some phones (Xiaomi, Oppo, Vivo, Samsung) also set Battery > Luffy > "Unrestricted" so the system doesn't stop the listener.
