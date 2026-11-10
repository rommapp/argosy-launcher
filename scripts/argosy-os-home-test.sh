#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Stage the debug build as a persistent privileged home app via Magisk.

Usage: scripts/argosy-os-home-test.sh <command>
  build          package app-debug.apk into build/argosy-os/argosy-home-test.zip
  guard          fail if the APK requests a privileged permission the allowlist lacks
  install        guard, push and install the module (reboot afterwards)
  check          read-only proof dump: placement, flags, grants, oom level, home role
  kill           kill -9 the debug process and report whether it comes back
  update         adb install -r the same APK over the system copy
  disable-home   pm disable-user the stock home app (STOCK_HOME overrides)
  enable-home    pm enable the stock home app
  remove         flag the module for removal (reboot afterwards)

Device selection: ARGOSY_DEVICE=<serial>, else the single connected device.
EOF
}

PKG="com.nendo.argosy.debug"
APK="app/build/outputs/apk/debug/app-debug.apk"
MODULE_SRC="tools/argosy-os/home-test/module"
MODULE_ID="argosy_home_test"
APP_DIR="system/priv-app/ArgosyDebug"
ALLOWLIST="${ALLOWLIST:-$MODULE_SRC/system/etc/permissions/privapp-permissions-argosy-debug.xml}"
OUT_DIR="build/argosy-os"
ZIP="$OUT_DIR/argosy-home-test.zip"
STOCK_HOME="${STOCK_HOME:-com.sec.android.app.launcher}"
SDK_DIR=$(grep '^sdk.dir=' local.properties | cut -d= -f2)
AAPT2=$(ls -d "$SDK_DIR"/build-tools/*/aapt2 | sort -V | tail -n1)

resolve_serial() {
  if [ -n "${ARGOSY_DEVICE:-}" ]; then
    echo "$ARGOSY_DEVICE"; return 0
  fi
  local serials count
  serials=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  count=$(printf '%s\n' "$serials" | grep -c . || true)
  if [ "$count" -eq 1 ]; then
    echo "$serials"; return 0
  fi
  echo "home-test: set ARGOSY_DEVICE to one of:" >&2
  printf '%s\n' "$serials" >&2
  return 1
}

requested_permissions() {
  "$AAPT2" dump permissions "$APK" | awk '/^uses-permission:/ {gsub(/name=|'"'"'/, "", $2); print $2}' | sort -u
}

allowlisted_permissions() {
  { grep -o 'permission name="[^"]*"' "$ALLOWLIST" || true; } | cut -d'"' -f2 | sort -u
}

build_module() {
  [ -f "$APK" ] || { echo "home-test: $APK missing, run assembleDebug" >&2; exit 1; }
  local stage
  stage=$(mktemp -d)
  cp -R "$MODULE_SRC/." "$stage/"
  mkdir -p "$stage/$APP_DIR/lib/arm64"
  cp "$APK" "$stage/$APP_DIR/ArgosyDebug.apk"
  unzip -q -j "$APK" 'lib/arm64-v8a/*.so' -d "$stage/$APP_DIR/lib/arm64"
  mkdir -p "$OUT_DIR"
  rm -f "$ZIP"
  (cd "$stage" && zip -q -r -X "$OLDPWD/$ZIP" .)
  rm -rf "$stage"
  echo "home-test: built $ZIP ($(unzip -l "$ZIP" | grep -c '\.so$') native libs)"
}

guard() {
  local missing=0 perm level
  local -a dump
  dump=("${adb[@]}" shell pm list permissions -g -f)
  local table allowed
  allowed=$(allowlisted_permissions)
  table=$("${dump[@]}" | tr -d '\r' | awk '/permission:/ {sub(/.*permission:/, ""); p=$0} /protectionLevel:/ {sub(/.*protectionLevel:/, ""); print p" "$0}')
  while read -r perm; do
    level=$(awk -v p="$perm" '$1==p && !seen {print $2; seen=1}' <<< "$table")
    case "$level" in
      *privileged*)
        if ! grep -x "$perm" <<< "$allowed" >/dev/null; then
          echo "home-test: $perm is privileged ($level) and not in $ALLOWLIST" >&2
          missing=1
        fi ;;
    esac
  done < <(requested_permissions)
  [ "$missing" -eq 0 ] || { echo "home-test: refusing, enforce mode would bootloop" >&2; exit 1; }
  echo "home-test: allowlist covers every privileged permission ($(getprop_dev ro.control_privapp_permissions) mode)"
}

getprop_dev() {
  "${adb[@]}" shell getprop "$1" | tr -d '\r'
}

su_dev() {
  "${adb[@]}" shell "su -c '$1'"
}

check() {
  local dump pid
  dump=$("${adb[@]}" shell dumpsys package "$PKG" | tr -d '\r')
  echo "== placement"
  printf '%s\n' "$dump" | grep -E '^\s+(codePath|versionCode|pkgFlags|privateFlags|legacyNativeLibraryDir)=' | sed 's/^ *//'
  echo "== hidden system copy"
  printf '%s\n' "$dump" | awk '/Hidden system packages:/ {f=1} f' | grep -E 'codePath|versionCode' | sed 's/^ *//' || echo "none"
  echo "== privileged grant"
  printf '%s\n' "$dump" | grep -E 'PACKAGE_USAGE_STATS' | sed 's/^ *//' | sort -u
  echo "== home role"
  "${adb[@]}" shell cmd role get-role-holders android.app.role.HOME | tr -d '\r'
  echo "== process"
  pid=$("${adb[@]}" shell pidof "$PKG" | tr -d '\r' || true)
  if [ -n "$pid" ]; then
    echo "pid=$pid oom_score_adj=$(su_dev "cat /proc/$pid/oom_score_adj" | tr -d '\r')"
    "${adb[@]}" shell dumpsys activity processes "$PKG" | tr -d '\r' | grep -m3 -E 'persistent=|oom: max=|curAdj=' | sed 's/^ *//'
  else
    echo "not running"
  fi
  echo "== native lib errors since boot"
  "${adb[@]}" logcat -d | grep -E 'UnsatisfiedLinkError|dlopen failed' | grep -i argosy | tail -5 || echo "none"
}

kill_test() {
  local before after i
  before=$("${adb[@]}" shell pidof "$PKG" | tr -d '\r' || true)
  [ -n "$before" ] || { echo "home-test: $PKG not running" >&2; exit 1; }
  su_dev "kill -9 $before"
  for i in $(seq 1 15); do
    sleep 1
    after=$("${adb[@]}" shell pidof "$PKG" | tr -d '\r' || true)
    if [ -n "$after" ] && [ "$after" != "$before" ]; then
      echo "home-test: killed $before, restarted as $after after ${i}s"
      return 0
    fi
  done
  echo "home-test: killed $before, not restarted within 15s"
}

main() {
  local cmd="${1:-help}"
  case "$cmd" in
    build) build_module; return ;;
    guard|install|check|kill|update|disable-home|enable-home|remove) ;;
    *) usage; return ;;
  esac
  local serial; serial=$(resolve_serial) || exit 1
  adb=(adb -s "$serial")
  case "$cmd" in
    guard) guard ;;
    install)
      guard
      [ -f "$ZIP" ] || build_module
      "${adb[@]}" push "$ZIP" /data/local/tmp/argosy-home-test.zip >/dev/null
      su_dev "magisk --install-module /data/local/tmp/argosy-home-test.zip"
      echo "home-test: installed, reboot to apply" ;;
    check) check ;;
    kill) kill_test ;;
    update) "${adb[@]}" install -r "$APK" ;;
    disable-home) "${adb[@]}" shell pm disable-user --user 0 "$STOCK_HOME" ;;
    enable-home) "${adb[@]}" shell pm enable --user 0 "$STOCK_HOME" ;;
    remove)
      su_dev "touch /data/adb/modules/$MODULE_ID/remove"
      echo "home-test: flagged for removal, reboot to apply" ;;
    *) usage ;;
  esac
}

main "$@"
