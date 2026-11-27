# Zen Android

## Shipping a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. With `JAVA_HOME=/opt/homebrew/opt/openjdk@17`, run `./gradlew :app:testDebugUnitTest :app:assembleRelease`.
3. Delete the old APKs in `~/Documents/Projects/zen/release/`, then copy the new one there as `zen-android-X.Y.Z-arm64.apk`.
   Only the newest APK is kept locally. Old GitHub Releases stay: they cost nothing and allow a rollback.
4. Update `README.md`, commit and push (never force).
5. `gh release create android-vX.Y.Z <apk> --repo ericmatte/zen-browser-android --target <sha> --title "Zen Android X.Y.Z" --notes "…"`.

## GeckoView upgrades

A Stop hook (`scripts/check-geckoview.sh`) blocks the end of a turn when the app has unreleased changes
and Mozilla has a newer stable GeckoView. When it fires: bump the version in `app/build.gradle.kts`,
run the tests, check the app on the emulator (a new engine can change behaviour), then ship a release.
