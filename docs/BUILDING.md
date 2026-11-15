# Building READR

## Requirements

- JDK 17 (Temurin recommended)
- Android SDK with platform **android-35** and **build-tools 35.0.0**
- Gradle 8.10.2 (use the included wrapper — `./gradlew`)

Android Studio Koala (or newer) with the standard SDK setup will work out of
the box.

## Common commands

```bash
./gradlew :app:testDebugUnitTest     # unit tests (document engines, utilities)
./gradlew :app:assembleDebug         # debug APK
./gradlew :app:assembleRelease       # release APK
```

Output locations:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

On very memory-constrained machines (under 8 GB RAM) you can disable code
shrinking for local release builds:

```bash
./gradlew :app:assembleRelease -Preadr.release.minify=false
```

## Release signing

READR never commits signing material. Configure signing in one of two ways:

**Local machine** — add the following to `~/.gradle/gradle.properties`
(outside the repository) or pass `-P` flags:

```properties
readr.keystore.path=/absolute/path/to/readr-release.jks
readr.keystore.password=YOUR_STORE_PASSWORD
readr.keystore.alias=readr
readr.keystore.keyPassword=YOUR_KEY_PASSWORD
```

**CI** — set these repository secrets in GitHub:
`READR_KEYSTORE_B64` (the keystore file, base64-encoded),
`READR_KEYSTORE_PASSWORD`, `READR_KEY_ALIAS`.

If no signing configuration is present, release builds fall back to the debug
keystore so the project always builds.

Generate a release keystore once with:

```bash
keytool -genkeypair -v -keystore readr-release.jks -alias readr \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore and passwords safe — losing them means you cannot publish
compatible updates to the same app installation.
