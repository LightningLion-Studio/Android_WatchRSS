#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v node >/dev/null 2>&1; then
    echo "Required command not found: node" >&2
    exit 1
fi

if [[ $# -eq 0 && -t 0 ]]; then
    read -r -p "请输入六位取件码: " pickup_code
    set -- "${pickup_code}"
fi

exec node "${SCRIPT_DIR}/fetch_user_log.mjs" "$@"
