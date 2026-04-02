#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VARIANT="profileableRelease"
GRADLEW="${ROOT_DIR}/gradlew"
APK_DIR="${ROOT_DIR}/app/build/outputs/apk/${VARIANT}"
MAPPING_DIR="${ROOT_DIR}/app/build/outputs/mapping/${VARIANT}"
DIST_DIR_DEFAULT="${ROOT_DIR}/app/build/outputs/dist/${VARIANT}"
DIST_DIR="${DIST_DIR:-${DIST_DIR_DEFAULT}}"
MANIFEST_REPORT="${ROOT_DIR}/app/build/outputs/logs/manifest-merger-${VARIANT}-report.txt"

usage() {
    cat <<'EOF'
Usage:
  scripts/build_profileable_release.sh [--dist-dir <path>] [--clean]

Options:
  --dist-dir <path>  Override the output directory used to collect artifacts.
  --clean            Run `./gradlew clean` before assembling.
  -h, --help         Show this help message.

Artifacts collected to the dist directory:
  - app-profileableRelease.apk
  - mapping.txt
  - output-metadata.json
  - baselineProfiles/ (if present)
EOF
}

clean_first=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dist-dir)
            if [[ $# -lt 2 ]]; then
                echo "--dist-dir requires a path argument" >&2
                exit 1
            fi
            DIST_DIR="$2"
            shift 2
            ;;
        --clean)
            clean_first=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

require_file() {
    local path="$1"
    if [[ ! -f "${path}" ]]; then
        echo "Required file not found: ${path}" >&2
        exit 1
    fi
}

copy_if_exists() {
    local src="$1"
    local dest="$2"
    if [[ -e "${src}" ]]; then
        mkdir -p "$(dirname "${dest}")"
        cp -R "${src}" "${dest}"
    fi
}

if [[ "${clean_first}" == "true" ]]; then
    "${GRADLEW}" clean
fi

"${GRADLEW}" :app:assembleProfileableRelease

APK_PATH="${APK_DIR}/app-profileableRelease.apk"
MAPPING_PATH="${MAPPING_DIR}/mapping.txt"
OUTPUT_METADATA_PATH="${APK_DIR}/output-metadata.json"
BASELINE_PROFILES_DIR="${APK_DIR}/baselineProfiles"

require_file "${APK_PATH}"
require_file "${MAPPING_PATH}"
require_file "${OUTPUT_METADATA_PATH}"
require_file "${MANIFEST_REPORT}"

if ! grep -q '^profileable$' "${MANIFEST_REPORT}"; then
    echo "Manifest merge report does not contain a profileable node" >&2
    exit 1
fi

if ! grep -q 'android:shell' "${MANIFEST_REPORT}"; then
    echo "Manifest merge report does not contain android:shell for profileable" >&2
    exit 1
fi

mkdir -p "${DIST_DIR}"

cp "${APK_PATH}" "${DIST_DIR}/"
cp "${MAPPING_PATH}" "${DIST_DIR}/"
cp "${OUTPUT_METADATA_PATH}" "${DIST_DIR}/"
copy_if_exists "${BASELINE_PROFILES_DIR}" "${DIST_DIR}/baselineProfiles"

cat <<EOF
Profileable release artifacts are ready.
Variant: ${VARIANT}
Dist: ${DIST_DIR}
APK: ${DIST_DIR}/$(basename "${APK_PATH}")
Mapping: ${DIST_DIR}/$(basename "${MAPPING_PATH}")
Metadata: ${DIST_DIR}/$(basename "${OUTPUT_METADATA_PATH}")
EOF
