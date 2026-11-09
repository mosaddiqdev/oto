# Oto

A private, local-first music stats app for Android. It watches what plays in any music app and turns it into stats and history, all stored on your phone.

See `docs/PLAN.md` for the plan and build order.

## Build

Every push to `main` builds a debug APK on GitHub Actions. Open the run, then download the `oto-debug-apk` artifact.

Locally: `./gradlew assembleDebug` (JDK 17, Android SDK 36).
