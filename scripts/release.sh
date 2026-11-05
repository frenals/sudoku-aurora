#!/usr/bin/env bash
# Cut a Sudoku Aurora release.
#
#   ./scripts/release.sh 2.0.1
#
# Increments versionCode, sets versionName, builds a signed release APK,
# verifies the signing certificate against the locked fingerprint, publishes
# the APK + CHANGELOG section as a GitHub Release, and pushes everything.
#
# Env overrides: JAVA_HOME (JDK 17), ANDROID_HOME / sdk.dir in local.properties,
# GRADLE (defaults to ./gradlew). `gh` must be authenticated with repo scope.
set -euo pipefail

VERSION="${1:-}"
DRY_RUN=0
[[ "${2:-}" == "--dry-run" ]] && DRY_RUN=1
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][A-Za-z0-9.]+)?$ ]]; then
  echo "Usage: ./scripts/release.sh <semver> [--dry-run]   (e.g. 2.0.1)" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/android-app"
BG="$APP/app/build.gradle"
CHANGELOG="$ROOT/CHANGELOG.md"
FINGERPRINT="$ROOT/scripts/signing-fingerprint.txt"
NOTES="$(mktemp)"
BG_BACKUP=""

# The connector exports a read-only GITHUB_TOKEN; gh must use its own stored token.
unset GITHUB_TOKEN GH_TOKEN

cleanup() {
  # A dry run must leave versionCode / versionName exactly as they were.
  if (( DRY_RUN )) && [[ -n "$BG_BACKUP" && -f "$BG_BACKUP" ]]; then
    cp "$BG_BACKUP" "$BG"
  fi
  rm -f "$BG_BACKUP" "$NOTES"
  if [[ -n "${REPO:-}" ]]; then
    git -C "$ROOT" remote set-url origin "https://github.com/${REPO}.git" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# ---- 1. release notes must exist in CHANGELOG -----------------------------
if (( DRY_RUN )); then
  # A dry run publishes nothing, so a CHANGELOG section is not required.
  printf 'Dry run - no release published.\n' > "$NOTES"
else
  python3 - "$CHANGELOG" "$VERSION" "$NOTES" <<'PY'
import sys, re
text = open(sys.argv[1], encoding='utf-8').read()
v = re.escape(sys.argv[2])
m = re.search(r'^## \[' + v + r'\][^\n]*\n(.*?)(?=^## \[|\Z)', text, re.M | re.S)
if not m:
    sys.exit("ERROR: no '## [%s]' section in CHANGELOG.md — write the release notes first." % sys.argv[2])
notes = m.group(1).strip()
if not notes:
    sys.exit("ERROR: CHANGELOG section for %s is empty." % sys.argv[2])
open(sys.argv[3], 'w', encoding='utf-8').write(notes + '\n')
print("Release notes: %d characters" % len(notes))
PY
fi

# ---- 2. bump version ------------------------------------------------------
if (( DRY_RUN )); then
  BG_BACKUP=$(mktemp)
  cp "$BG" "$BG_BACKUP"
fi
OLD_CODE=$(python3 - "$BG" <<'PY'
import sys, re
m = re.search(r'\bversionCode\s+(\d+)', open(sys.argv[1]).read())
if not m:
    sys.exit("ERROR: versionCode not found in build.gradle")
print(m.group(1))
PY
) || { echo "$OLD_CODE" >&2; exit 1; }
NEW_CODE=$((OLD_CODE + 1))
python3 - "$BG" "$NEW_CODE" "$VERSION" <<'PY'
import sys, re
p, code, name = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
s, n1 = re.subn(r'(\bversionCode\s+)\d+', lambda m: m.group(1) + code, s, count=1)
s, n2 = re.subn(r"(\bversionName\s+)'[^']*'", lambda m: m.group(1) + "'" + name + "'", s, count=1)
assert n1 == 1 and n2 == 1, "could not update version fields"
open(p, 'w', encoding='utf-8').write(s)
PY
echo "Version: versionCode $OLD_CODE -> $NEW_CODE, versionName $VERSION"

# ---- 3. resolve toolchain -------------------------------------------------
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
if [[ -z "${ANDROID_HOME:-}" && -f "$APP/local.properties" ]]; then
  ANDROID_HOME=$(grep '^sdk.dir=' "$APP/local.properties" | cut -d= -f2-)
fi
: "${ANDROID_HOME:?Set ANDROID_HOME (or sdk.dir in android-app/local.properties)}"
export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

BT=$(ls -d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | sort -V | tail -n 1)
APKSIGNER="${BT}apksigner"
AAPT="${BT}aapt"
[[ -x "$APKSIGNER" && -x "$AAPT" ]] || { echo "ERROR: apksigner/aapt not found under $BT" >&2; exit 1; }
GRADLE="${GRADLE:-$APP/gradlew}"

# ---- 4. build --------------------------------------------------------------
echo "Building release APK with $GRADLE ..."
(cd "$APP" && "$GRADLE" assembleRelease --no-daemon -q)
APK=$(find "$APP/app/build/outputs/apk/release" -name '*.apk' -print -quit)
[[ -n "$APK" && -f "$APK" ]] || { echo "ERROR: no release APK produced" >&2; exit 1; }

# ---- 5. verify signature, certificate and badging -------------------------
"$APKSIGNER" verify "$APK" >/dev/null || { echo "ERROR: apksigner verify failed" >&2; exit 1; }
EXPECTED=$(tr -d '[:space:]' < "$FINGERPRINT" | tr 'A-Z' 'a-z')
ACTUAL=$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null \
  | awk -F': ' '/Signer #1 certificate SHA-256 digest/{print $2}' \
  | tr -d ' :' | tr 'A-Z' 'a-z')
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "ERROR: signing certificate changed!" >&2
  echo "  expected: $EXPECTED" >&2
  echo "  actual  : $ACTUAL" >&2
  echo "  Refusing to publish: users could not install this over the previous version." >&2
  exit 1
fi
echo "Signature OK, certificate matches locked fingerprint"

BADGE=$("$AAPT" dump badging "$APK" 2>/dev/null | head -n 1)
echo "$BADGE"
for expect in "name='com.workbuddy.sudoku'" "versionCode='$NEW_CODE'" "versionName='$VERSION'"; do
  grep -qF "$expect" <<<"$BADGE" || { echo "ERROR: badging missing '$expect'" >&2; exit 1; }
done

# ---- 6. stage artifacts ----------------------------------------------------
if (( DRY_RUN )); then
  echo "Dry run: not staging APKs, not committing, not publishing"
else
  cp "$APK" "$ROOT/SudokuAurora.apk"
  mkdir -p "$ROOT/dist"
  cp "$APK" "$ROOT/dist/SudokuAurora-v$VERSION.apk"
  ls -lh "$ROOT/SudokuAurora.apk" "$ROOT/dist/SudokuAurora-v$VERSION.apk"
fi

if (( DRY_RUN )); then
  echo ""
  echo "=== DRY RUN OK ==="
  echo "Next release would be: versionCode $NEW_CODE, versionName $VERSION"
  echo "Verified: same package + same signing certificate + higher versionCode"
  echo "=> that APK would install in place over the current installation."
  exit 0
fi

# ---- 7. commit and push ----------------------------------------------------
cd "$ROOT"
ORIGIN=$(git remote get-url origin)
REPO=$(python3 - "$ORIGIN" <<'PY'
import sys, re
u = sys.argv[1].strip().replace('git@github.com:', 'https://github.com/')
m = re.search(r'github\.com/(.+)$', u)
if not m: sys.exit('')
p = m.group(1).rstrip('/')
if p.endswith('.git'): p = p[:-4]
bits = p.split('/')
print(bits[0] + '/' + bits[1] if len(bits) >= 2 else '')
PY
)
[[ -n "$REPO" ]] || { echo "ERROR: cannot parse GitHub repo from origin: $ORIGIN" >&2; exit 1; }

git add -A
if ! git diff --cached --quiet; then
  git commit -q -m "Release v$VERSION (versionCode $NEW_CODE)"
  echo "Committed release changes"
else
  echo "No source changes to commit"
fi

TOKEN=$(env -u GITHUB_TOKEN -u GH_TOKEN gh auth token)
git remote set-url origin "https://oauth2:${TOKEN}@github.com/${REPO}.git"
git -c credential.helper= push -u origin main
echo "Pushed to origin/main"

# ---- 8. create GitHub Release ---------------------------------------------
env -u GITHUB_TOKEN -u GH_TOKEN gh release create "v$VERSION" \
  "$ROOT/dist/SudokuAurora-v$VERSION.apk" \
  --repo "$REPO" \
  --title "Sudoku Aurora v$VERSION" \
  --notes-file "$NOTES" \
  --target main

echo ""
echo "=== RELEASE READY ==="
echo "https://github.com/$REPO/releases/tag/v$VERSION"
echo "APK: SudokuAurora-v$VERSION.apk (also copied to SudokuAurora.apk)"
echo "Installs over previous versions: same package + same certificate + higher versionCode"
