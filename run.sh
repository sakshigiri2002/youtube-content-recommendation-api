#!/usr/bin/env bash
set -euo pipefail

if [[ -x "./gradlew" ]]; then
  GRADLE_CMD="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  echo "Gradle 8+ is required. Install it and Java 17+, then run this script again."
  exit 1
fi

if [[ "${1:-api}" == "prompt" ]]; then
  exec "$GRADLE_CMD" bootRun --args='--app.cli-enabled=true'
else
  exec "$GRADLE_CMD" bootRun
fi
