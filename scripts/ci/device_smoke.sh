#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts"
DEVICE_DIR="${ARTIFACTS_DIR}/device"
LOG_DIR="${ARTIFACTS_DIR}/logs"
mkdir -p "${DEVICE_DIR}" "${LOG_DIR}"

SERIAL="${ANDROID_SERIAL:-}"
if [[ -z "${SERIAL}" && -f "${DEVICE_DIR}/selected-serial.txt" ]]; then
    SERIAL="$(cat "${DEVICE_DIR}/selected-serial.txt")"
fi
if [[ -z "${SERIAL}" ]]; then
    echo "No device serial resolved for smoke run" >&2
    exit 1
fi

APP_PACKAGE="com.lightningstudio.watchrss"
TEST_PACKAGE="${APP_PACKAGE}.test"
APP_APK="$(find "${ROOT_DIR}/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*-debug.apk' | sort | head -n 1)"
TEST_APK="$(find "${ROOT_DIR}/app/build/outputs/apk/androidTest/debug" -maxdepth 1 -type f -name '*androidTest*.apk' | sort | head -n 1)"
if [[ -z "${APP_APK}" || -z "${TEST_APK}" ]]; then
    echo "Required debug APKs were not found" >&2
    exit 1
fi

log_contains_app_fatal() {
    local log_file="$1"
    if grep -Eq "ANR in ${APP_PACKAGE}|Process ${APP_PACKAGE} has died|am_crash.*${APP_PACKAGE}|Fatal signal.*${APP_PACKAGE}" "${log_file}"; then
        return 0
    fi
    if grep -q 'FATAL EXCEPTION' "${log_file}" && grep -q "${APP_PACKAGE}" "${log_file}"; then
        return 0
    fi
    if grep -q 'AndroidRuntime' "${log_file}" && grep -q "${APP_PACKAGE}" "${log_file}"; then
        return 0
    fi
    return 1
}

adb -s "${SERIAL}" logcat -c
adb -s "${SERIAL}" shell pm clear "${APP_PACKAGE}" || true
adb -s "${SERIAL}" shell pm clear "${TEST_PACKAGE}" || true
adb -s "${SERIAL}" install -r -t "${APP_APK}"
adb -s "${SERIAL}" install -r -t "${TEST_APK}"
adb -s "${SERIAL}" shell input keyevent KEYCODE_WAKEUP || true
adb -s "${SERIAL}" shell wm dismiss-keyguard || true
adb -s "${SERIAL}" shell input keyevent 82 || true
adb -s "${SERIAL}" shell am start -W -n "${APP_PACKAGE}/.MainActivity" > "${DEVICE_DIR}/am-start.txt"
sleep 6

adb -s "${SERIAL}" shell pidof "${APP_PACKAGE}" > "${DEVICE_DIR}/pidof.txt" || true
adb -s "${SERIAL}" shell dumpsys activity activities > "${DEVICE_DIR}/activities-smoke.txt"
adb -s "${SERIAL}" shell dumpsys activity top > "${DEVICE_DIR}/top-activity-smoke.txt"
adb -s "${SERIAL}" shell dumpsys window windows > "${DEVICE_DIR}/windows-smoke.txt"
adb -s "${SERIAL}" exec-out screencap -p > "${DEVICE_DIR}/smoke.png" || true
adb -s "${SERIAL}" logcat -d -v time > "${LOG_DIR}/logcat-smoke.txt"

if [[ ! -s "${DEVICE_DIR}/pidof.txt" ]]; then
    echo "App process is not running after smoke launch" >&2
    exit 1
fi

if ! grep -q "${APP_PACKAGE}" "${DEVICE_DIR}/top-activity-smoke.txt" && \
    ! grep -q "${APP_PACKAGE}" "${DEVICE_DIR}/windows-smoke.txt"; then
    echo "App is not present in activity stack after smoke launch" >&2
    exit 1
fi

if log_contains_app_fatal "${LOG_DIR}/logcat-smoke.txt"; then
    echo "Smoke logcat contains fatal app errors" >&2
    exit 1
fi
