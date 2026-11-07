#!/usr/bin/env bash
# Build + install debug APK on a paired device, using .env for API / credentials.
# Usage:
#   cp .env.example .env   # edit values
#   ./scripts/run_debug.sh
#   ENV_FILE=.env.staging ./scripts/run_debug.sh
#   ./scripts/run_debug.sh --no-launch
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ENV_FILE="${ENV_FILE:-.env}"
LAUNCH=1
for arg in "$@"; do
  case "$arg" in
    --no-launch) LAUNCH=0 ;;
    -h|--help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $arg (try --no-launch)" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE — copy .env.example and fill API_HOST / keys." >&2
  exit 1
fi

# Load KEY=VALUE lines (skip blank/#). Values may be quoted.
set -a
# shellcheck disable=SC1090
source <(
  sed -e 's/\r$//' -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$ENV_FILE" |
    grep -E '^[A-Za-z_][A-Za-z0-9_]*='
)
set +a

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [[ ! -d "$SDK_DIR" ]]; then
  echo "Android SDK not found at $SDK_DIR (set ANDROID_HOME)." >&2
  exit 1
fi

# Gradle prefers local.properties over env — sync .env keys there, keep sdk.dir.
EXISTING_SDK=""
if [[ -f local.properties ]]; then
  EXISTING_SDK="$(grep -E '^sdk\.dir=' local.properties | head -1 | cut -d= -f2- || true)"
fi
{
  echo "sdk.dir=${EXISTING_SDK:-$SDK_DIR}"
  echo "API_HOST=${API_HOST:-https://dev-apigw.durpalla.com}"
  echo "PUSHER_KEY=${PUSHER_KEY:-}"
  echo "PUSHER_CLUSTER=${PUSHER_CLUSTER:-ap2}"
  echo "RECAPTCHA_SITE_KEY=${RECAPTCHA_SITE_KEY:-}"
} > local.properties

echo "API_HOST=${API_HOST:-https://dev-apigw.durpalla.com}"
echo "PUSHER_KEY=${PUSHER_KEY:+(set)}${PUSHER_KEY:-(empty → fake realtime)}"
echo "RECAPTCHA_SITE_KEY=${RECAPTCHA_SITE_KEY:+(set)}${RECAPTCHA_SITE_KEY:-(empty → no-op)}"

if ! command -v adb >/dev/null; then
  export PATH="${SDK_DIR}/platform-tools:${PATH}"
fi
adb start-server >/dev/null
DEVICES="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
if [[ -z "$DEVICES" ]]; then
  echo "No paired device (adb devices). Enable USB/wireless debugging and retry." >&2
  adb devices
  exit 1
fi
echo "Device(s):"
echo "$DEVICES"

GRADLE_ARGS=(installDebug)
if [[ -n "${ABI:-}" ]]; then
  GRADLE_ARGS+=("-Pabi=${ABI}")
  echo "ABI split: $ABI"
fi

./gradlew "${GRADLE_ARGS[@]}"

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ -n "${ABI:-}" ]]; then
  CANDIDATE="$(ls app/build/outputs/apk/debug/app-"${ABI}"-debug.apk 2>/dev/null | head -1 || true)"
  [[ -n "$CANDIDATE" ]] && APK="$CANDIDATE"
fi
echo "APK: $APK ($(du -h "$APK" | cut -f1))"

if [[ "$LAUNCH" -eq 1 ]]; then
  adb shell am start -n com.durpalla.driver/.MainActivity
  echo "Launched com.durpalla.driver"
fi
