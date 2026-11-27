#!/bin/bash
# Stop hook: once Claude has changed the Android app, it may not stop while a
# newer stable GeckoView sits unused; it must upgrade and ship a release first.
set -u
input=$(cat)
[ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

repo=$(cd "$(dirname "$0")/../.." && pwd)
gradle="$repo/android/app/build.gradle.kts"
cd "$repo" || exit 0

last_tag=$(git tag -l 'android-v*' --sort=-v:refname | head -1)
unreleased=$(git log --oneline "${last_tag:-HEAD}..HEAD" -- android 2>/dev/null | head -1)
dirty=$(git status --porcelain -- android 2>/dev/null | head -1)
[ -z "$unreleased$dirty" ] && exit 0

current=$(grep -oE 'org\.mozilla\.geckoview:geckoview:[0-9.]+' "$gradle" | sed 's/.*://')
latest=$(curl -fsS --max-time 8 https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml \
  | grep -oE '<release>[^<]+' | sed 's/<release>//')
[ -z "$current" ] || [ -z "$latest" ] && exit 0
[ "$current" = "$latest" ] && exit 0
[ "$(printf '%s\n%s\n' "$current" "$latest" | sort -V | tail -1)" != "$latest" ] && exit 0

jq -n --arg c "$current" --arg l "$latest" '{
  decision: "block",
  reason: ("GeckoView \($l) is out (the app uses \($c)). Before stopping: bump it in android/app/build.gradle.kts, run the tests, check the app on the emulator, then ship a new release following android/CLAUDE.md.")
}'
