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
| `src/zen/spaces/ZenGradientGenerator.mjs` gradient and text-colour math | `ui/ZenGradient.kt` |
| `src/zen/library` history section (search, filter, sort, clear) | `ui/Library.kt` |
| `src/zen/folders/ZenFolder.mjs` folder icon + open animation | `ui/FolderIcon.kt` |
| `zen-icons/common/selectable/*.svg` space/folder icons | `assets/zen-icons.json` |
| `zen-icons/nucleo/*.svg` UI icons | `assets/ui-icons.json` |
| `src/zen/tokens`, `zen-theme.css`, `vertical-tabs.css` colours | `ui/Theme.kt`, `ui/SpacesDrawer.kt` |
| `configs/branding/release` logo | launcher and splash icons |

## Features

- Home: desktop-style search at launch and for each new tab. Results
  (open tabs, history, suggestions) appear only once you type.
- Sidebar: spaces (swipe between them, colours blend with the finger), pinned
  tabs, folders, the "Clear" separator, space switcher, library (history).
  Tap a space name to rename it (synced); tap its icon to fold pinned tabs.
  Swipe a tab left to close (or unpin), right to pin (or unpin). Hold an item
  for its menu; slide onto an entry and release, or move away to drag it
  (reorder, pin, or drop into Essentials).
- Essentials are local to the phone (never synced) and hold up to 12 tabs.
- Unpinned tabs opened on the phone close after a chosen idle time. Tabs
  opened on desktop are never closed from the phone.
- Sync runs on its own: at launch, on resume, after each change and every
  5 minutes.
- Address bar: shows the page title; tap it for the URL and a copy-link
  button, docked above the keyboard. Swipe it to slide to the previous or next
  tab, flick up for a new tab. Hold the sidebar button for recent tabs, slide
  onto one and release to open it.
- Private tabs: sidebar on the right, never synced, no history, dark pages
  (Google included). Launcher shortcut "New private tab".
- Glance, link menu, find in page, desktop site, downloads, file uploads,
  site permissions, full-screen video, default-browser setting.

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
