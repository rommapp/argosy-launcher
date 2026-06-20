#!/usr/bin/env bash
# argosy-dev.sh - debug-device automation for Argosy (install, logs, input, screenshots).
# One entry point so the whole adb workflow is a single approved command.
#
# Usage: scripts/argosy-dev.sh <command> [args]
#   devices                 list connected adb devices
#   pid                     print the running debug-app pid (empty if not running)
#   install [--build]       install the debug APK (--build runs assembleDebug first, blocks)
#   launch                  wake the screen and foreground the debug app
#   logs [-c] [pattern]     dump debug-process logcat; -c clears buffer first; pattern greps (-iE)
#   key <name|code> ...     send key events: up down left right a b confirm back enter menu home wake
#   text <string>           type text into the focused field
#   shot [path]             screenshot to path (default /tmp/argosy-shot.png); prints the path
#
# Device selection: set ARGOSY_DEVICE=<serial> to target a specific device. Otherwise the
# single connected device is used; if several are attached the script lists them and exits.
set -euo pipefail

PKG="com.nendo.argosy.debug"
APK="app/build/outputs/apk/debug/app-debug.apk"
ACTIVITY="$PKG/com.nendo.argosy.MainActivity"

resolve_serial() {
  if [ -n "${ARGOSY_DEVICE:-}" ]; then
    echo "$ARGOSY_DEVICE"; return 0
  fi
  local serials
  serials=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  local count
  count=$(printf '%s\n' "$serials" | grep -c . || true)
  if [ "$count" -eq 1 ]; then
    echo "$serials"; return 0
  fi
  if [ "$count" -eq 0 ]; then
    echo "argosy-dev: no device connected" >&2; return 1
  fi
  echo "argosy-dev: multiple devices; set ARGOSY_DEVICE to one of:" >&2
  printf '%s\n' "$serials" >&2
  return 1
}

keycode() {
  case "$1" in
    up) echo 19 ;; down) echo 20 ;; left) echo 21 ;; right) echo 22 ;;
    a|confirm) echo 96 ;; b) echo 97 ;; enter) echo 66 ;;
    back) echo 4 ;; menu) echo 82 ;; home) echo 3 ;; wake) echo 224 ;;
    *) echo "$1" ;;
  esac
}

main() {
  local cmd="${1:-help}"; shift || true
  local serial; serial=$(resolve_serial) || exit 1
  local adb=(adb -s "$serial")

  case "$cmd" in
    devices)
      adb devices -l ;;
    pid)
      "${adb[@]}" shell pidof "$PKG" 2>/dev/null | tr -d '\r' ;;
    install)
      if [ "${1:-}" = "--build" ]; then
        echo "building :app:assembleDebug ..." >&2
        ./gradlew :app:assembleDebug
      fi
      "${adb[@]}" install -r "$APK" ;;
    launch)
      "${adb[@]}" shell input keyevent 224 >/dev/null 2>&1 || true
      "${adb[@]}" shell am start -n "$ACTIVITY" >/dev/null 2>&1
      echo "launched $PKG" ;;
    logs)
      if [ "${1:-}" = "-c" ]; then "${adb[@]}" logcat -c 2>/dev/null; shift; fi
      local p; p=$("${adb[@]}" shell pidof "$PKG" 2>/dev/null | tr -d '\r')
      local pattern="${1:-}"
      if [ -n "$p" ]; then
        if [ -n "$pattern" ]; then
          "${adb[@]}" logcat -d 2>/dev/null | grep -E "\b$p\b" | grep -iE "$pattern"
        else
          "${adb[@]}" logcat -d --pid="$p" 2>/dev/null
        fi
      else
        echo "argosy-dev: $PKG not running (buffer cleared if -c)" >&2
        [ -n "$pattern" ] && "${adb[@]}" logcat -d 2>/dev/null | grep -iE "$pattern" || true
      fi ;;
    key)
      for name in "$@"; do
        "${adb[@]}" shell input keyevent "$(keycode "$name")" >/dev/null 2>&1
      done
      echo "sent: $*" ;;
    text)
      "${adb[@]}" shell input text "$1" >/dev/null 2>&1
      echo "typed: $1" ;;
    shot)
      local path="${1:-/tmp/argosy-shot.png}"
      "${adb[@]}" exec-out screencap -p > "$path" 2>/dev/null
      echo "$path" ;;
    help|*)
      sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//' ;;
  esac
}

main "$@"
