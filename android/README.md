# Zen for Android

A lean GeckoView browser that syncs Zen spaces with Zen desktop.

## How sync works

Zen desktop uploads the sidebar to the `spaces` collection of your Mozilla
account (Settings → Sync → "Sync your sidebar across devices"). The record
format is defined in `src/zen/sync/ZenSpacesSyncModel.sys.mjs`. This app reads
that collection and writes tab opens, updates and closes back to it.

## What is shared with desktop

The app reuses desktop code or ports it closely:

| Desktop source | Android |
| --- | --- |
| `src/zen/sync/ZenSpacesSyncModel.sys.mjs` record format | `sync/SpacesModel.kt` |
| `src/zen/spaces/ZenGradientGenerator.mjs` gradient math | `ui/ZenGradient.kt` |
| `src/zen/folders/ZenFolder.mjs` folder icon + open animation | `ui/FolderIcon.kt` |
| `zen-icons/common/selectable/*.svg` space/folder icons | `assets/zen-icons.json` |
| `zen-icons/nucleo/*.svg` UI icons | `assets/ui-icons.json` |
| `src/zen/tokens`, `zen-theme.css`, `vertical-tabs.css` colours | `ui/Theme.kt`, `ui/SpacesDrawer.kt` |
| `configs/branding/release` logo | launcher and splash icons |

## Features

- Sidebar: essentials, spaces (swipe between them), pinned tabs, folders, the
  "Clear" separator, space switcher. Swipe a tab left to close it. Long-press
  and drag to reorder or to pin/unpin.
- Glance: long-press a link, then "Glance".
- Private tabs: never synced, kept only in memory.
- Address bar: copy link, search suggestions, "switch to tab", swipe left or
  right to change tab, swipe up for a new tab.
- Find in page, desktop site, share, downloads, file uploads, site
  permissions, full-screen video, links from other apps.

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
