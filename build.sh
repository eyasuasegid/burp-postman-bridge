#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v java >/dev/null 2>&1; then
  echo "Error: Java was not found in PATH. Install JDK 21 and try again."
  exit 1
fi

if [ ! -x ./gradlew ]; then
  chmod +x ./gradlew
fi

./gradlew clean build

echo
echo "Build complete. Extension JAR:"
echo "  build/libs/BurpPostmanBridge-1.0.0.jar"
