#!/usr/bin/env bash
set -euo pipefail

PKG="com.lightningstudio.watchrss"
LIST_ACTIVITY="$PKG/.debug.PerfLargeListActivity"
ARTICLE_ACTIVITY="$PKG/.debug.PerfLargeArticleActivity"

SWIPE_COUNT="${SWIPE_COUNT:-20}"
SWIPE_DURATION_MS="${SWIPE_DURATION_MS:-260}"
SWIPE_PAUSE_SEC="${SWIPE_PAUSE_SEC:-0.25}"

size="$(adb shell wm size | tr -d '\r' | awk -F: '/Physical/{print $2}' | tr -d '[:space:]')"
if [[ "$size" == *x* ]]; then
  width="${size%x*}"
  height="${size#*x}"
else
  width=400
  height=400
fi

x=$((width / 2))
start_y=$((height * 3 / 4))
end_y=$((height / 4))

swipe_loop() {
  local count="$1"
  for ((i = 0; i < count; i++)); do
    adb shell input swipe "$x" "$start_y" "$x" "$end_y" "$SWIPE_DURATION_MS"
    sleep "$SWIPE_PAUSE_SEC"
  done
}

adb logcat -c
adb shell am start -n "$LIST_ACTIVITY"
sleep 1.5
swipe_loop "$SWIPE_COUNT"

adb shell am start -n "$ARTICLE_ACTIVITY"
sleep 1.5
swipe_loop "$SWIPE_COUNT"

adb logcat -d | grep "perf"
