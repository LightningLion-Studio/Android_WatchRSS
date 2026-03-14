#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts"
DEVICE_DIR="${ARTIFACTS_DIR}/device"
LOG_DIR="${ARTIFACTS_DIR}/logs"
GRADLE_DIR="${ARTIFACTS_DIR}/gradle"
mkdir -p "${DEVICE_DIR}" "${LOG_DIR}" "${GRADLE_DIR}"

SERIAL="${ANDROID_SERIAL:-}"
if [[ -z "${SERIAL}" && -f "${DEVICE_DIR}/selected-serial.txt" ]]; then
    SERIAL="$(cat "${DEVICE_DIR}/selected-serial.txt")"
fi

copy_if_exists() {
    local src="$1"
    local dest="$2"
    if [[ -e "${src}" ]]; then
        mkdir -p "$(dirname "${dest}")"
        cp -R "${src}" "${dest}"
    fi
}

copy_first_existing() {
    local dest="$1"
    shift
    local candidate
    for candidate in "$@"; do
        if [[ -e "${candidate}" ]]; then
            copy_if_exists "${candidate}" "${dest}"
            return 0
        fi
    done
    return 1
}

log_contains_app_fatal() {
    local log_file="$1"
    if grep -Eq 'ANR in com\.lightningstudio\.watchrss|Process com\.lightningstudio\.watchrss has died|am_crash.*com\.lightningstudio\.watchrss|Fatal signal.*com\.lightningstudio\.watchrss' "${log_file}"; then
        return 0
    fi
    if grep -q 'FATAL EXCEPTION' "${log_file}" && grep -q 'com.lightningstudio.watchrss' "${log_file}"; then
        return 0
    fi
    if grep -q 'AndroidRuntime' "${log_file}" && grep -q 'com.lightningstudio.watchrss' "${log_file}"; then
        return 0
    fi
    return 1
}

adb devices -l > "${DEVICE_DIR}/adb-devices-final.txt" || true

if [[ -n "${SERIAL}" ]]; then
    adb -s "${SERIAL}" shell getprop > "${DEVICE_DIR}/getprop-final.txt" || true
    adb -s "${SERIAL}" shell dumpsys battery > "${DEVICE_DIR}/battery-final.txt" || true
    adb -s "${SERIAL}" shell dumpsys activity activities > "${DEVICE_DIR}/activities-final.txt" || true
    adb -s "${SERIAL}" shell dumpsys activity top > "${DEVICE_DIR}/top-activity.txt" || true
    adb -s "${SERIAL}" shell dumpsys window windows > "${DEVICE_DIR}/windows-final.txt" || true
    adb -s "${SERIAL}" logcat -d -v time > "${LOG_DIR}/logcat-instrumentation.txt" || true
fi

copy_if_exists "${ROOT_DIR}/app/build/reports/tests/testDebugUnitTest" "${GRADLE_DIR}/unit-test-report"
copy_if_exists "${ROOT_DIR}/app/build/test-results/testDebugUnitTest" "${GRADLE_DIR}/unit-test-results"
copy_if_exists "${ROOT_DIR}/app/build/reports/lint-results-debug.html" "${GRADLE_DIR}/lint/lint-results-debug.html"
copy_if_exists "${ROOT_DIR}/app/build/reports/lint-results-debug.xml" "${GRADLE_DIR}/lint/lint-results-debug.xml"
copy_first_existing "${GRADLE_DIR}/android-tests-report" \
    "${ROOT_DIR}/app/build/reports/androidTests/connected/debug" \
    "${ROOT_DIR}/app/build/reports/androidTests/connected"
copy_first_existing "${GRADLE_DIR}/android-tests-results" \
    "${ROOT_DIR}/app/build/outputs/androidTest-results/connected/debug" \
    "${ROOT_DIR}/app/build/outputs/androidTest-results/connected"

if [[ -f "${LOG_DIR}/logcat-instrumentation.txt" ]] && \
    log_contains_app_fatal "${LOG_DIR}/logcat-instrumentation.txt"; then
    echo "Collected instrumentation logcat contains fatal app errors" >&2
    exit 1
fi
