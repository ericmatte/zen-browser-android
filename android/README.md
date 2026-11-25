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
| `zen-icons/nucleo/*.svg` UI icons, `common/library/library.svg` | `assets/ui-icons.json` |
| `src/zen/tokens`, `zen-theme.css`, `vertical-tabs.css` colours | `ui/Theme.kt`, `ui/SpacesDrawer.kt` |
| `configs/branding/release` logo | launcher and splash icons |

## Features

- Home: at launch nothing loads; the bottom bar morphs into the search, docked
  above the keyboard. Before typing it lists recent searches and pages; while
  typing, open tabs, history and suggestions, best match nearest the bar.
  Swipe a suggestion left to uncover a remove button; removed entries never
  come back. Back on a page with no history shows this search (without the
  keyboard); back again leaves the app. Leaving the search with nothing open
  returns to the tab used last (even after a restart), else the first
  temporary tab.
- Sidebar: spaces (swipe between them, colours blend with the finger), pinned
  tabs, folders, the "Clear" separator, temporary tabs at the bottom with
  "New Tab" last, space switcher (hold a space for its menu). History opens
  inside the sidebar, which grows full width like desktop's library.
  Tap a space name to rename it (synced); tap its icon to fold pinned tabs.
  Swipe a tab right to pin (or unpin). Swipe a temporary tab left to uncover
  its archive button (tap it to archive; touch elsewhere to put it back); a
  pinned tab swiped left is unpinned. Hold an item
  for its menu; slide onto an entry and release, or move away to drag it
  (reorder, pin, or drop into Essentials). While dragging, the rows open a
  gap where the item will land.
- Essentials are local to the phone (never synced) and hold up to 12 tabs.
- Unpinned tabs opened on the phone close after a chosen idle time. Tabs
  opened on desktop are never closed from the phone.
- Sync runs on its own: at launch, on resume, when the sidebar opens, after
  each change and every 5 minutes. Pull the sidebar list down to sync now.
  Settings → Account shows when the last sync ran and how many records it
  received and sent.
- Address bar: 90% wide, shows the favicon (green/red lock) and title on the
  left; its buttons dip when pressed, and holding "+" names it. Tap it
  for the URL and a copy-link button, docked above the keyboard. Swipe it to
  slide to the previous or next tab (the title slides along), flick up for a
  new tab. Hold the sidebar button for recent tabs. Find in page is the bar
  itself. The bar steps aside while a page's own text field uses the keyboard.
- Landscape: the bar rests as a full-height strip on the left edge, painted
  in the page's colour like the status bar. Rotating
  while a video plays (silent ones too) enters fullscreen; rotating back
  leaves it.
- "Open in <app>" in the ⋯ menu when an installed app (YouTube, Maps…)
  claims the page's address.
- Pages laid out for the phone's width never scroll sideways onto nothing.
- Updates: new versions are GitHub releases tagged `android-vX.Y.Z`. Settings →
  Updates adds Zen to [Obtainium](https://github.com/ImranR98/Obtainium) (or
  copies the repository URL for it). The app also asks GitHub twice a day and
  puts a yellow dot on the ⋯ button and its Settings entry when a newer
  version is out (Settings → Updates to turn it off). Hold the app icon for an
  "Updates" shortcut that opens Obtainium.
- Every menu works like iOS: hold, slide onto an entry, release. A plain tap
  also works. Each entry the finger reaches ticks the vibration motor
  (Settings → Haptic feedback, on by default).
- The sidebar opens from the bar's sidebar button; the screen edges keep
  Android's back gesture.
- Private tabs: near-black, sidebar on the right, never synced, no history.
  Google shows its own dark theme. Entering private mode with no tab opens the
  search; otherwise the private sidebar lets you pick. Launcher shortcut
  "New private tab".
- Pull a page down from its top to reload (only where Gecko allows overscroll,
  like Firefox for Android).
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
to the debug key. Every release must use the same key, or Android refuses
to update the installed app.

Publish a release (Obtainium and the in-app check read these):

```sh
gh release create android-vX.Y.Z ~/Documents/Projects/zen/release/zen-android-X.Y.Z-arm64.apk \
  --repo ericmatte/zen-browser-desktop --title "Zen Android X.Y.Z" --notes "…"
```
