#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts"
BOOTSTRAP_DIR="${ARTIFACTS_DIR}/bootstrap"
mkdir -p "${BOOTSTRAP_DIR}" "${ARTIFACTS_DIR}/device" "${ARTIFACTS_DIR}/logs"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${SDK_ROOT}" ]]; then
    echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set" >&2
    exit 1
fi
if [[ ! -d "${SDK_ROOT}" ]]; then
    echo "Android SDK directory does not exist: ${SDK_ROOT}" >&2
    exit 1
fi

printf 'sdk.dir=%s\n' "${SDK_ROOT}" > "${ROOT_DIR}/local.properties"

{
    echo "UTC_NOW=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "ROOT_DIR=${ROOT_DIR}"
    echo "SDK_ROOT=${SDK_ROOT}"
    echo "ANDROID_SERIAL=${ANDROID_SERIAL:-}"
    echo "INSTRUMENTATION_CLASS=${INSTRUMENTATION_CLASS:-}"
} > "${BOOTSTRAP_DIR}/environment.txt"

uname -a > "${BOOTSTRAP_DIR}/uname.txt" 2>&1
java -version > "${BOOTSTRAP_DIR}/java-version.txt" 2>&1
adb version > "${BOOTSTRAP_DIR}/adb-version.txt" 2>&1
./gradlew --version > "${BOOTSTRAP_DIR}/gradle-version.txt" 2>&1
