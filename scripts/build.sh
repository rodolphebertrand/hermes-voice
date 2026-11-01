#!/usr/bin/env bash
# build.sh — build Hermes Voice (fork hermes-relay). tasks: assembleSideloadDebug (default),
# assembleGooglePlayDebug, bundleGooglePlayRelease. Requiert JDK 17+ et Android SDK.
set -euo pipefail
cd "$(dirname "$0")/.."
TASK="${1:-assembleSideloadDebug}"
if ! command -v java >/dev/null 2>&1 && [ -x "$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java" ]; then
  export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d /usr/lib/android-sdk ]; then export ANDROID_HOME=/usr/lib/android-sdk; fi
echo "JAVA_HOME=${JAVA_HOME:-unset} ANDROID_HOME=${ANDROID_HOME:-unset}"
./gradlew --no-daemon "$TASK"
