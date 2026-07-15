#!/usr/bin/env bash
# Capture a build fingerprint. Usage: capture.sh <build-dir> <out.json> [build_id]
set -euo pipefail
cd "$(dirname "$0")"
exec python3 -m parity.capture "$@"
