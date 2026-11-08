#!/usr/bin/env bash
# Build a signed release APK/AAB using .env for API + signing credentials.
#
# Usage:
#   cp .env.example .env          # fill API_HOST / Pusher / signing
#   ./scripts/generate_keystore.sh
#   ./scripts/run_release.sh                 # signed APK (arm64 by default)
#   ./scripts/run_release.sh --bundle        # Play Store AAB
#   ./scripts/run_release.sh --install       # adb install after APK build
#   ENV_FILE=.env.prod ./scripts/run_release.sh
#   ABI=arm64-v8a,armeabi-v7a ./scripts/run_release.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PKG="com.durpalla.driver"
ENV_FILE="${ENV_FILE:-.env}"
BUNDLE=0
INSTALL=0
for arg in "$@"; do
  case "$arg" in
    --bundle|-b) BUNDLE=1 ;;
    --install|-i) INSTALL=1 ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $arg (try --bundle, --install)" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE — copy .env.example and fill credentials." >&2
  exit 1
fi

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

# macOS default is ~/Library/Android/sdk; Linux often ~/Android/Sdk
if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
  SDK_DIR="$ANDROID_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT" ]]; then
  SDK_DIR="$ANDROID_SDK_ROOT"
elif [[ -d "$HOME/Library/Android/sdk" ]]; then
  SDK_DIR="$HOME/Library/Android/sdk"
else
  SDK_DIR="$HOME/Android/Sdk"
fi
if [[ ! -d "$SDK_DIR" ]]; then
  echo "Android SDK not found at $SDK_DIR (set ANDROID_HOME)." >&2
  exit 1
fi

STORE_FILE="${STORE_FILE:-keystore/durpalla-driver-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-durpalla-driver}"
# Resolve relative keystore path against repo root.
if [[ "$STORE_FILE" != /* ]]; then
  STORE_FILE_ABS="$ROOT/$STORE_FILE"
else
  STORE_FILE_ABS="$STORE_FILE"
fi
STORE_FILE_REL="${STORE_FILE_ABS#"$ROOT"/}"

if [[ ! -f "$STORE_FILE_ABS" ]]; then
  echo "Missing keystore: $STORE_FILE_ABS" >&2
  echo "Generate one with: ./scripts/generate_keystore.sh" >&2
  exit 1
fi
if [[ -z "${STORE_PASSWORD:-}" ]]; then
  echo "STORE_PASSWORD missing in $ENV_FILE" >&2
  exit 1
fi
KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

EXISTING_SDK=""
if [[ -f local.properties ]]; then
  EXISTING_SDK="$(grep -E '^sdk\.dir=' local.properties | head -1 | cut -d= -f2- || true)"
fi
{
  echo "sdk.dir=${EXISTING_SDK:-$SDK_DIR}"
  echo "API_HOST=${API_HOST:-https://apigw.durpalla.com}"
  echo "PUSHER_KEY=${PUSHER_KEY:-}"
  echo "PUSHER_CLUSTER=${PUSHER_CLUSTER:-ap2}"
  echo "RECAPTCHA_SITE_KEY=${RECAPTCHA_SITE_KEY:-}"
  echo "STORE_FILE=${STORE_FILE_REL}"
  echo "STORE_PASSWORD=${STORE_PASSWORD}"
  echo "KEY_ALIAS=${KEY_ALIAS}"
  echo "KEY_PASSWORD=${KEY_PASSWORD}"
} > local.properties

echo "API_HOST=${API_HOST:-https://apigw.durpalla.com}"
echo "STORE_FILE=${STORE_FILE_REL}"
echo "KEY_ALIAS=${KEY_ALIAS}"
if [[ -n "${PUSHER_KEY:-}" ]]; then echo "PUSHER_KEY=(set)"; else echo "PUSHER_KEY=(empty)"; fi

ABI="${ABI:-arm64-v8a}"
if [[ "$BUNDLE" -eq 1 ]]; then
  echo "Building signed release bundle (AAB)…"
  ./gradlew bundleRelease
  OUT="$(ls app/build/outputs/bundle/release/*.aab 2>/dev/null | head -1 || true)"
  if [[ -z "$OUT" || ! -f "$OUT" ]]; then
    echo "AAB not found under app/build/outputs/bundle/release/" >&2
    exit 1
  fi
else
  echo "Building signed release APK (ABI=$ABI)…"
  ./gradlew assembleRelease "-Pabi=${ABI}"
  OUT="$(ls "app/build/outputs/apk/release/app-${ABI}-release.apk" 2>/dev/null | head -1 || true)"
  if [[ -z "$OUT" || ! -f "$OUT" ]]; then
    OUT="$(ls app/build/outputs/apk/release/*-release.apk 2>/dev/null | head -1 || true)"
  fi
  if [[ -z "$OUT" || ! -f "$OUT" ]]; then
    echo "Release APK not found under app/build/outputs/apk/release/" >&2
    exit 1
  fi
fi

SIZE="$(du -h "$OUT" | cut -f1)"
echo "Output: $OUT [$SIZE]"
SYMS="app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip"
if [[ -f "$SYMS" ]]; then
  echo "Native symbols: $SYMS [$(du -h "$SYMS" | cut -f1)] (upload in Play Console if not embedded in AAB)"
fi

# Verify APK/AAB is signed (apksigner if available).
if [[ "$OUT" == *.apk ]] && command -v apksigner >/dev/null; then
  apksigner verify --print-certs "$OUT" | head -n 20
elif [[ "$OUT" == *.apk ]]; then
  BUILD_TOOLS="$(ls -d "$SDK_DIR"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
  if [[ -n "$BUILD_TOOLS" && -x "${BUILD_TOOLS}apksigner" ]]; then
    "${BUILD_TOOLS}apksigner" verify --print-certs "$OUT" | head -n 20
  else
    jarsigner -verify -verbose -certs "$OUT" 2>/dev/null | tail -n 15 || true
  fi
fi

if [[ "$INSTALL" -eq 1 ]]; then
  if [[ "$OUT" != *.apk ]]; then
    echo "--install only works with APK (omit --bundle)." >&2
    exit 1
  fi
  if ! command -v adb >/dev/null; then
    export PATH="${SDK_DIR}/platform-tools:${PATH}"
  fi
  adb start-server >/dev/null
  if ! adb devices | awk 'NR>1 && $2=="device" {found=1} END{exit !found}'; then
    echo "No adb device for --install." >&2
    exit 1
  fi
  echo "Installing $OUT …"
  adb install -r "$OUT"
  echo "Installed: $PKG"
fi

echo "Done."
