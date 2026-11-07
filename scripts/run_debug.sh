#!/usr/bin/env bash
# Build + install debug APK on a paired device, using .env for API / credentials,
# then stream device console logs (adb logcat) into this terminal.
# Usage:
#   cp .env.example .env   # edit values
#   ./scripts/run_debug.sh
#   ENV_FILE=.env.staging ./scripts/run_debug.sh
#   ./scripts/run_debug.sh --no-launch
#   ./scripts/run_debug.sh --no-logs
#   ./scripts/run_debug.sh --logs-only   # skip build/install; attach logcat to running app
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PKG="com.durpalla.driver"
ACTIVITY="$PKG/.MainActivity"
ENV_FILE="${ENV_FILE:-.env}"
LAUNCH=1
LOGS=1
LOGS_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --no-launch) LAUNCH=0 ;;
    --no-logs) LOGS=0 ;;
    --logs-only) LOGS_ONLY=1; LAUNCH=0 ;;
    -h|--help)
      sed -n '2,11p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $arg (try --no-launch, --no-logs, --logs-only)" >&2
      exit 1
      ;;
  esac
done

if ! command -v adb >/dev/null; then
  SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  export PATH="${SDK_DIR}/platform-tools:${PATH}"
fi

ensure_device() {
  adb start-server >/dev/null
  local devices
  devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  if [[ -z "$devices" ]]; then
    echo "No paired device (adb devices). Enable USB/wireless debugging and retry." >&2
    adb devices
    exit 1
  fi
  echo "Device(s):"
  echo "$devices"
}

# Clear buffer, launch (optional), wait for process, stream app logcat to this console.
follow_logs() {
  echo
  echo "---- logcat ($PKG) — Ctrl+C to stop ----"
  adb logcat -c >/dev/null 2>&1 || true
  if [[ "$LAUNCH" -eq 1 ]]; then
    adb shell am start -n "$ACTIVITY" >/dev/null
    echo "Launched $PKG"
  fi
  local pid=""
  local i=0
  while [[ $i -lt 30 ]]; do
    pid="$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r' || true)"
    [[ -n "$pid" ]] && break
    sleep 0.3
    i=$((i + 1))
  done
  if [[ -z "$pid" ]]; then
    echo "App process not running. Open Durpalla Driver, or re-run without --no-launch." >&2
    echo "Falling back to filtered logcat (package + common tags)..."
    exec adb logcat -v time \
      "*:S" "AndroidRuntime:E" "System.err:W" "ActivityManager:I" \
      "okhttp.OkHttpClient:I" "Durpalla:V" "flutter:V"
  fi
  echo "pid=$pid"
  # App process logs + crashes (still visible if the process dies and restarts — re-run script).
  exec adb logcat -v time --pid="$pid"
}

if [[ "$LOGS_ONLY" -eq 1 ]]; then
  ensure_device
  # Attach if already running; otherwise start the app.
  if adb shell pidof -s "$PKG" >/dev/null 2>&1; then
    LAUNCH=0
  else
    LAUNCH=1
  fi
  follow_logs
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE - copy .env.example and fill API_HOST / keys." >&2
  exit 1
fi

# Load KEY=VALUE without sourcing (avoids bash syntax errors from special chars).
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
  [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]] || continue
  key="${line%%=*}"
  val="${line#*=}"
  if [[ "$val" =~ ^\".*\"$ ]]; then val="${val:1:-1}"
  elif [[ "$val" =~ ^\'.*\'$ ]]; then val="${val:1:-1}"
  fi
  export "$key=$val"
done < "$ENV_FILE"

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
if [[ -n "${PUSHER_KEY:-}" ]]; then echo "PUSHER_KEY=(set)"; else echo "PUSHER_KEY=(empty - fake realtime)"; fi
if [[ -n "${RECAPTCHA_SITE_KEY:-}" ]]; then echo "RECAPTCHA_SITE_KEY=(set)"; else echo "RECAPTCHA_SITE_KEY=(empty - no-op)"; fi

ensure_device

# Default arm64 for physical phones — universal debug APK is ~130MB (Agora+WebRTC) and
# often fails/no-ops over wireless adb when using Gradle installDebug alone.
ABI="${ABI:-arm64-v8a}"
GRADLE_ARGS=(assembleDebug "-Pabi=${ABI}")
echo "ABI split: $ABI"

./gradlew "${GRADLE_ARGS[@]}"

APK="$(ls "app/build/outputs/apk/debug/app-${ABI}-debug.apk" 2>/dev/null | head -1 || true)"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  APK="app/build/outputs/apk/debug/app-debug.apk"
fi
APK_SIZE="$(du -h "$APK" | cut -f1)"
echo "APK: $APK [$APK_SIZE]"

echo "Installing via adb (more reliable over wireless than gradle installDebug)..."
adb install -r -d "$APK"
if ! adb shell pm path "$PKG" >/dev/null 2>&1; then
  echo "Install reported success but package missing. Reconnect wireless debugging and retry." >&2
  exit 1
fi
echo "Installed: $PKG"

if [[ "$LOGS" -eq 1 ]]; then
  follow_logs
fi

if [[ "$LAUNCH" -eq 1 ]]; then
  adb shell am start -n "$ACTIVITY"
  echo "Launched $PKG — look for Durpalla Driver in the app drawer"
fi
