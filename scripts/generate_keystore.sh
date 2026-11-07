#!/usr/bin/env bash
# Generate a release signing keystore for Durpalla Driver.
# Writes keystore/durpalla-driver-release.jks and appends STORE_* keys to .env if missing.
#
# Usage:
#   ./scripts/generate_keystore.sh
#   STORE_PASSWORD=secret KEY_PASSWORD=secret ./scripts/generate_keystore.sh
#
# Manual equivalent:
#   keytool -genkeypair -v \
#     -keystore keystore/durpalla-driver-release.jks \
#     -alias durpalla-driver \
#     -keyalg RSA -keysize 2048 -validity 10000 \
#     -storepass '<STORE_PASSWORD>' -keypass '<KEY_PASSWORD>' \
#     -dname 'CN=Durpalla Driver, OU=Mobile, O=Durpalla, L=Dhaka, ST=Dhaka, C=BD'
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ENV_FILE="${ENV_FILE:-.env}"
KEYSTORE_DIR="${KEYSTORE_DIR:-keystore}"
KEYSTORE_FILE="${KEYSTORE_FILE:-$KEYSTORE_DIR/durpalla-driver-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-durpalla-driver}"
VALIDITY_DAYS="${VALIDITY_DAYS:-10000}"
DNAME="${DNAME:-CN=Durpalla Driver, OU=Mobile, O=Durpalla, L=Dhaka, ST=Dhaka, C=BD}"

if ! command -v keytool >/dev/null; then
  echo "keytool not found (install a JDK)." >&2
  exit 1
fi

if [[ -f "$KEYSTORE_FILE" ]]; then
  echo "Keystore already exists: $KEYSTORE_FILE"
  echo "Delete it first if you want to regenerate (Play uploads must keep the same key)."
  exit 0
fi

rand() { openssl rand -base64 24 | tr -d '/+=' | head -c 24; }

# Prefer passwords already in .env / environment.
load_env_val() {
  local key="$1"
  if [[ -n "${!key:-}" ]]; then echo "${!key}"; return; fi
  if [[ -f "$ENV_FILE" ]]; then
    local line
    line="$(grep -E "^${key}=" "$ENV_FILE" | tail -1 || true)"
    if [[ -n "$line" ]]; then
      local val="${line#*=}"
      if [[ "$val" =~ ^\".*\"$ ]]; then val="${val:1:-1}"
      elif [[ "$val" =~ ^\'.*\'$ ]]; then val="${val:1:-1}"
      fi
      echo "$val"
      return
    fi
  fi
  echo ""
}

STORE_PASSWORD="${STORE_PASSWORD:-$(load_env_val STORE_PASSWORD)}"
KEY_PASSWORD="${KEY_PASSWORD:-$(load_env_val KEY_PASSWORD)}"
[[ -n "$STORE_PASSWORD" ]] || STORE_PASSWORD="$(rand)"
[[ -n "$KEY_PASSWORD" ]] || KEY_PASSWORD="$STORE_PASSWORD"

mkdir -p "$KEYSTORE_DIR"
keytool -genkeypair -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "$DNAME"

echo "Created: $KEYSTORE_FILE"
echo "Alias:   $KEY_ALIAS"

# Ensure .env exists and has signing keys (do not overwrite existing values).
touch "$ENV_FILE"
upsert_env() {
  local key="$1" val="$2"
  if grep -qE "^${key}=" "$ENV_FILE"; then
    return
  fi
  printf '%s=%s\n' "$key" "$val" >> "$ENV_FILE"
}

upsert_env STORE_FILE "$KEYSTORE_FILE"
upsert_env STORE_PASSWORD "$STORE_PASSWORD"
upsert_env KEY_ALIAS "$KEY_ALIAS"
upsert_env KEY_PASSWORD "$KEY_PASSWORD"

echo
echo "Signing keys written to $ENV_FILE (gitignored)."
echo "Back up $KEYSTORE_FILE + passwords — losing them blocks Play Store updates."
echo
echo "Next: ./scripts/run_release.sh"
