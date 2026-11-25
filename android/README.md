# Zen for Android

A lean GeckoView browser that syncs Zen spaces with Zen desktop.

## How sync works

Zen desktop uploads the sidebar to the `spaces` collection of your Mozilla
account (Settings → Sync → "Sync your sidebar across devices"). The record
format is defined in `src/zen/sync/ZenSpacesSyncModel.sys.mjs`. This app reads
that collection and writes tab opens, updates and closes back to it.

## Build

Requirements: JDK 17, Android SDK platform 37.2, NDK 30.

```sh
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

The APKs are in `app/build/outputs/apk/release/`. Release signing reads
`keystore.properties` (never committed). Without it, the release falls back
to the debug key.
