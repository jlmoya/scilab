#!/usr/bin/env bash
# Diff two build fingerprints. Usage: diff.sh <baseline.json> <candidate.json>
set -euo pipefail
cd "$(dirname "$0")"
exec python3 -m parity.diff "$@"
