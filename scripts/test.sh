#!/usr/bin/env bash
# test.sh — lance les tests unitaires JVM du module app (tâche : testSideloadDebugUnitTest).
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v java >/dev/null 2>&1 && [ -x "$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java" ]; then
  export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d /usr/lib/android-sdk ]; then export ANDROID_HOME=/usr/lib/android-sdk; fi
./gradlew --no-daemon testSideloadDebugUnitTest
