#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts"
DEVICE_DIR="${ARTIFACTS_DIR}/device"
mkdir -p "${DEVICE_DIR}"

SERIAL="${ANDROID_SERIAL:-}"

start_adb_server() {
    adb start-server > "${DEVICE_DIR}/adb-start-server.txt" 2>&1
}

capture_device_listing() {
    adb devices -l > "${DEVICE_DIR}/adb-devices.txt"
}

restart_adb_server_once() {
    adb kill-server > "${DEVICE_DIR}/adb-kill-server.txt" 2>&1 || true
    start_adb_server
    capture_device_listing
}

ensure_no_unauthorized_or_offline() {
    if awk 'NR > 1 && ($2 == "unauthorized" || $2 == "offline") { found = 1 } END { exit found ? 0 : 1 }' "${DEVICE_DIR}/adb-devices.txt"; then
        echo "adb reported an unauthorized or offline device. Check artifacts/device/adb-devices.txt." >&2
        exit 1
    fi
}

resolve_serial() {
    if [[ -n "${SERIAL}" ]]; then
        if ! grep -Eq "^${SERIAL}[[:space:]]+device" "${DEVICE_DIR}/adb-devices.txt"; then
            echo "Configured ANDROID_SERIAL=${SERIAL} is not in device state" >&2
            exit 1
        fi
        return
    fi

    mapfile -t DEVICE_LINES < <(awk 'NR>1 && $2=="device" {print $1}' "${DEVICE_DIR}/adb-devices.txt")
    if [[ "${#DEVICE_LINES[@]}" -eq 0 ]]; then
        echo "No connected device in adb device state" >&2
        exit 1
    fi
    if [[ "${#DEVICE_LINES[@]}" -gt 1 ]]; then
        echo "Multiple connected devices found. Set ANDROID_SERIAL." >&2
        exit 1
    fi
    SERIAL="${DEVICE_LINES[0]}"
}

start_adb_server
capture_device_listing
if ! awk 'NR > 1 && $2 == "device" { found = 1 } END { exit found ? 0 : 1 }' "${DEVICE_DIR}/adb-devices.txt"; then
    restart_adb_server_once
fi
ensure_no_unauthorized_or_offline
resolve_serial

echo "${SERIAL}" > "${DEVICE_DIR}/selected-serial.txt"
if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf 'ANDROID_SERIAL=%s\n' "${SERIAL}" >> "${GITHUB_ENV}"
fi

adb -s "${SERIAL}" wait-for-device
STATE="$(adb -s "${SERIAL}" get-state)"
if [[ "${STATE}" != "device" ]]; then
    echo "Device ${SERIAL} is not ready: ${STATE}" >&2
    exit 1
fi

adb -s "${SERIAL}" shell getprop > "${DEVICE_DIR}/getprop.txt"
adb -s "${SERIAL}" shell dumpsys battery > "${DEVICE_DIR}/battery.txt"
adb -s "${SERIAL}" shell wm size > "${DEVICE_DIR}/wm-size.txt"
adb -s "${SERIAL}" shell wm density > "${DEVICE_DIR}/wm-density.txt"
adb -s "${SERIAL}" shell dumpsys power > "${DEVICE_DIR}/power.txt"
adb -s "${SERIAL}" shell dumpsys activity activities > "${DEVICE_DIR}/activities-preflight.txt"
adb -s "${SERIAL}" shell dumpsys window windows > "${DEVICE_DIR}/windows-preflight.txt"
adb -s "${SERIAL}" shell dumpsys activity top > "${DEVICE_DIR}/top-activity-preflight.txt"

{
    echo "SERIAL=${SERIAL}"
    echo "MANUFACTURER=$(adb -s "${SERIAL}" shell getprop ro.product.manufacturer | tr -d '\r')"
    echo "MODEL=$(adb -s "${SERIAL}" shell getprop ro.product.model | tr -d '\r')"
    echo "ANDROID_RELEASE=$(adb -s "${SERIAL}" shell getprop ro.build.version.release | tr -d '\r')"
    echo "ANDROID_SDK=$(adb -s "${SERIAL}" shell getprop ro.build.version.sdk | tr -d '\r')"
} > "${DEVICE_DIR}/device-summary.txt"

adb -s "${SERIAL}" shell input keyevent KEYCODE_WAKEUP || true
adb -s "${SERIAL}" shell wm dismiss-keyguard || true
adb -s "${SERIAL}" shell input keyevent 82 || true
