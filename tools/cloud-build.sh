#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -x "${JAVA_HOME:-/nonexistent}/bin/jlink" ]]; then
  export JAVA_HOME=/workspace/toolchains/jdk-21.0.13+9
fi
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-/workspace/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/.android-atmos}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.gradle-atmos}"
mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
# Java does not automatically honor HTTPS_PROXY. Preserve TLS verification.
if [[ -n "${HTTPS_PROXY:-}" ]]; then
  proxy_options=$(python3 - <<'PY'
import os,urllib.parse
p=urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
if p.hostname and p.port:
 print(f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port} -Dhttp.nonProxyHosts=localhost|127.* -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
PY
)
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $proxy_options"
fi
if [[ $# -eq 0 ]]; then set -- assembleDebug testDebugUnitTest lintDebug; fi
exec ./gradlew "$@" --no-daemon --max-workers=2
