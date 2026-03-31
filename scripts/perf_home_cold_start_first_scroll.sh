#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts/perf"
DEVICE_DIR="${ROOT_DIR}/artifacts/device"

PKG="${PKG:-com.lightningstudio.watchrss}"
ACTIVITY="${ACTIVITY:-${PKG}/.MainActivity}"
SCENARIO="${SCENARIO:-home_cold_start_first_scroll}"
SERIAL="${ANDROID_SERIAL:-}"

START_TIMEOUT_SEC="${START_TIMEOUT_SEC:-30}"
PRE_SCROLL_SETTLE_SEC="${PRE_SCROLL_SETTLE_SEC:-1.5}"
POST_SCROLL_SETTLE_SEC="${POST_SCROLL_SETTLE_SEC:-1.5}"
SWIPE_DURATION_MS="${SWIPE_DURATION_MS:-260}"
SWIPE_START_Y_RATIO="${SWIPE_START_Y_RATIO:-75}"
SWIPE_END_Y_RATIO="${SWIPE_END_Y_RATIO:-25}"
CLEAR_APP_DATA="${WATCHRSS_CLEAR_APP_DATA:-false}"

TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
OUTPUT_DIR="${OUTPUT_DIR:-${ARTIFACTS_DIR}/${SCENARIO}_${TIMESTAMP}}"

resolve_serial() {
    if [[ -n "${SERIAL}" ]]; then
        return
    fi
    if [[ -f "${DEVICE_DIR}/selected-serial.txt" ]]; then
        SERIAL="$(tr -d '\r' < "${DEVICE_DIR}/selected-serial.txt")"
    fi
    if [[ -n "${SERIAL}" ]]; then
        return
    fi

    mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    if (( ${#devices[@]} == 0 )); then
        echo "No adb device is available" >&2
        exit 1
    fi
    if (( ${#devices[@]} > 1 )); then
        echo "Multiple adb devices detected; set ANDROID_SERIAL explicitly" >&2
        printf 'Devices:\n%s\n' "${devices[*]}" >&2
        exit 1
    fi
    SERIAL="${devices[0]}"
}

adb_cmd() {
    adb -s "${SERIAL}" "$@"
}

wait_for_boot_completed() {
    local deadline=$((SECONDS + START_TIMEOUT_SEC))
    while (( SECONDS < deadline )); do
        local boot_completed
        boot_completed="$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
        if [[ "${boot_completed}" == "1" ]]; then
            return
        fi
        sleep 1
    done
    echo "Timed out waiting for device boot completion on ${SERIAL}" >&2
    exit 1
}

wait_for_foreground_activity() {
    local deadline=$((SECONDS + START_TIMEOUT_SEC))
    while (( SECONDS < deadline )); do
        if adb_cmd shell dumpsys window windows 2>/dev/null | grep -q "${PKG}"; then
            return
        fi
        sleep 1
    done
    echo "Timed out waiting for ${ACTIVITY} to reach foreground on ${SERIAL}" >&2
    exit 1
}

resolve_display_geometry() {
    local size
    size="$(
        adb_cmd shell wm size |
            tr -d '\r' |
            awk -F: '/Physical size|Physical/ { print $2 }' |
            tr -d '[:space:]'
    )"
    if [[ "${size}" == *x* ]]; then
        DISPLAY_WIDTH="${size%x*}"
        DISPLAY_HEIGHT="${size#*x}"
    else
        DISPLAY_WIDTH=400
        DISPLAY_HEIGHT=400
    fi

    SWIPE_X=$((DISPLAY_WIDTH / 2))
    SWIPE_START_Y=$((DISPLAY_HEIGHT * SWIPE_START_Y_RATIO / 100))
    SWIPE_END_Y=$((DISPLAY_HEIGHT * SWIPE_END_Y_RATIO / 100))
}

write_metadata() {
    cat > "${OUTPUT_DIR}/metadata.txt" <<EOF
scenario=${SCENARIO}
serial=${SERIAL}
package=${PKG}
activity=${ACTIVITY}
timestamp=${TIMESTAMP}
display_width=${DISPLAY_WIDTH}
display_height=${DISPLAY_HEIGHT}
swipe_x=${SWIPE_X}
swipe_start_y=${SWIPE_START_Y}
swipe_end_y=${SWIPE_END_Y}
swipe_duration_ms=${SWIPE_DURATION_MS}
pre_scroll_settle_sec=${PRE_SCROLL_SETTLE_SEC}
post_scroll_settle_sec=${POST_SCROLL_SETTLE_SEC}
clear_app_data=${CLEAR_APP_DATA}
EOF
}

resolve_serial
wait_for_boot_completed
resolve_display_geometry

mkdir -p "${OUTPUT_DIR}"
write_metadata

adb_cmd logcat -c
adb_cmd shell dumpsys gfxinfo "${PKG}" reset >/dev/null 2>&1 || true
adb_cmd shell dumpsys activity provider "${PKG}.debug.DebugLogProvider" --clear >/dev/null 2>&1 || true

if [[ "${CLEAR_APP_DATA}" == "true" ]]; then
    adb_cmd shell pm clear "${PKG}" >/dev/null || true
fi

adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb_cmd shell input keyevent 82 >/dev/null 2>&1 || true

# Use -S to guarantee a force-stopped cold launch before the first scroll.
adb_cmd shell am start -S -W -n "${ACTIVITY}" > "${OUTPUT_DIR}/am-start.txt"
wait_for_foreground_activity
sleep "${PRE_SCROLL_SETTLE_SEC}"

adb_cmd shell dumpsys activity top > "${OUTPUT_DIR}/activity-top-before-scroll.txt"
adb_cmd exec-out screencap -p > "${OUTPUT_DIR}/home-before-first-scroll.png" || true

# The first swipe is the only gesture in this scenario.
adb_cmd shell input swipe \
    "${SWIPE_X}" \
    "${SWIPE_START_Y}" \
    "${SWIPE_X}" \
    "${SWIPE_END_Y}" \
    "${SWIPE_DURATION_MS}"

sleep "${POST_SCROLL_SETTLE_SEC}"

adb_cmd exec-out screencap -p > "${OUTPUT_DIR}/home-after-first-scroll.png" || true
adb_cmd shell dumpsys gfxinfo "${PKG}" > "${OUTPUT_DIR}/gfxinfo.txt"
adb_cmd shell dumpsys gfxinfo "${PKG}" framestats > "${OUTPUT_DIR}/gfxinfo_framestats.txt"
adb_cmd shell dumpsys activity top > "${OUTPUT_DIR}/activity-top-after-scroll.txt"
adb_cmd shell dumpsys activity provider "${PKG}.debug.DebugLogProvider" > "${OUTPUT_DIR}/debuglogbuffer.txt" || true
adb_cmd logcat -d -v time > "${OUTPUT_DIR}/logcat.txt"

grep 'PerfTrace' "${OUTPUT_DIR}/logcat.txt" > "${OUTPUT_DIR}/logcat-perftrace.txt" || true
grep 'PerfTrace' "${OUTPUT_DIR}/logcat.txt" |
    grep -E 'home_cold_start|cat=frame|cat=feed|cat=repo|cat=img' \
    > "${OUTPUT_DIR}/logcat-perftrace-focus.txt" || true

cat <<EOF
Cold-start first-scroll capture script is ready.
Artifacts will be written to:
  ${OUTPUT_DIR}
EOF
