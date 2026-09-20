#!/usr/bin/env bash
set -euo pipefail
# Change to the repository root (assumes script is in the repo root)
cd "$(dirname "${BASH_SOURCE[0]}")"
# Ensure gradlew is present
if [[ ! -f gradlew ]]; then
  echo "ERROR: gradlew not found in repository root"
  exit 1
fi
# Start the server with custom port (default 44444)
./gradlew :server:app:run --args="--port=44444" "$@"
