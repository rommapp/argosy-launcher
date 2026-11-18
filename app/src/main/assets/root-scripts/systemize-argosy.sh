#!/system/bin/sh
# Make Argosy a system app so it survives memory pressure from heavy emulators.
#
# Run this through your device's "run script as root" setting (it executes as
# root and makes the script executable for you). Do NOT run it over adb.
#
# Argosy declares android:persistent="true", which Android only honors for apps
# on /system. Placing it there keeps the launcher process from being OOM-killed
# while a heavy emulator runs.
#
# Prefers a root-manager module (systemless, reversible, works on read-only
# dynamic partitions); falls back to a direct /system remount only if writable.
#
# Lands in /system/app, not priv-app, so it cannot trip privapp-permissions and
# bootloop the device.
#
# Every run appends to the first writable of /data/local/tmp/argosy-systemize.log,
# /sdcard/argosy-systemize.log, /data/ or /cache/. If the script appears to do
# nothing, read that file: the device's script runner often discards stdout, and
# on some devices its mount namespace has no /sdcard at all.
#
# Re-run this after every Argosy update. The systemized copy does not follow
# updates installed normally, and uninstalling an update reverts the app to the
# version this script captured.

PKG="${1:-com.nendo.argosy}"
MODULE_ID="argosy_systemize"

LOG=""
for candidate in \
    /data/local/tmp/argosy-systemize.log \
    /sdcard/argosy-systemize.log \
    /data/argosy-systemize.log \
    /cache/argosy-systemize.log
do
    if echo "" >> "$candidate" 2>/dev/null; then
        LOG="$candidate"
        chmod 666 "$LOG" 2>/dev/null
        break
    fi
done

if [ -n "$LOG" ]; then
    exec 3>>"$LOG"
else
    exec 3>&1
fi

log() {
    echo "[systemize-argosy] $1"
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >&3
}

fail() {
    log "FAILED: $1"
    log "--- run ended ---"
    exit 1
}

log "--- run started ---"
log "log file: ${LOG:-none writable, stdout only}"
log "uid=$(id -u) shell=$0"
log "storage visible: $([ -d /sdcard/Android ] && echo yes || echo no)"

if [ "$(id -u)" != "0" ]; then
    fail "not running as root (uid $(id -u)). Use the device's 'run script as root' option."
fi

APK=$(pm path "$PKG" 2>/dev/null | head -n1 | sed 's/^package://' | tr -d '\r')
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    fail "package $PKG not installed. Install Argosy normally first."
fi
log "found $PKG at $APK"

copy_apks() {
    dest="$1"
    count=0
    for src in $(pm path "$PKG" 2>/dev/null | sed 's/^package://' | tr -d '\r'); do
        [ -f "$src" ] || continue
        cp "$src" "$dest/$(basename "$src")" || return 1
        count=$((count + 1))
    done
    [ "$count" -gt 0 ] || return 1
    log "copied $count apk(s) to $dest"
    copy_native_libs "$dest" || return 1
    return 0
}

copy_native_libs() {
    dest="$1"
    libs="$(dirname "$APK")/lib"
    if [ ! -d "$libs" ]; then
        log "no extracted native libraries; the apk carries its own"
        return 0
    fi
    rm -rf "$dest/lib"
    cp -r "$libs" "$dest/lib" || return 1
    find "$dest/lib" -type d -exec chmod 755 {} \;
    find "$dest/lib" -type f -exec chmod 644 {} \;
    log "copied native libraries ($(ls "$dest/lib" | tr '\n' ' ')) to $dest/lib"
    return 0
}

root_manager() {
    [ -e /data/adb/magisk ] && { echo "Magisk"; return 0; }
    [ -e /data/adb/ksu ] && { echo "KernelSU"; return 0; }
    [ -e /data/adb/ap ] && { echo "APatch"; return 0; }
    command -v magisk >/dev/null 2>&1 && { echo "Magisk"; return 0; }
    command -v ksud >/dev/null 2>&1 && { echo "KernelSU"; return 0; }
    command -v apd >/dev/null 2>&1 && { echo "APatch"; return 0; }
    return 1
}

MANAGER=$(root_manager)

run_step() {
    desc="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        log "  ok: $desc"
        return 0
    fi
    log "  skipped: $desc (not supported on this build)"
    return 1
}

apply_runtime_hardening() {
    log "applying runtime hardening (stored in /data, survives reboot, needs no /system)"
    run_step "exempt from doze" cmd deviceidle whitelist "+$PKG"
    run_step "pin standby bucket to active" am set-standby-bucket "$PKG" active
    run_step "allow background run" cmd appops set "$PKG" RUN_IN_BACKGROUND allow
    run_step "allow unrestricted background" cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow

    if cmd deviceidle whitelist 2>/dev/null | grep -q "$PKG"; then
        log "  verified: $PKG is on the doze whitelist"
    else
        log "  WARNING: $PKG is not on the doze whitelist after the attempt"
    fi
}

if [ -n "$MANAGER" ] && [ -d /data/adb/modules ]; then
    log "$MANAGER detected, installing systemless module"
    MOD="/data/adb/modules/$MODULE_ID"
    rm -rf "$MOD"
    mkdir -p "$MOD/system/app/Argosy" || fail "cannot create $MOD"
    copy_apks "$MOD/system/app/Argosy" || fail "could not copy the apk into the module"
    cat > "$MOD/module.prop" <<EOF
id=$MODULE_ID
name=Argosy Systemize
version=1.1
versionCode=2
author=argosy
description=Mounts Argosy into /system/app so android:persistent takes effect
EOF
    chmod 755 "$MOD" "$MOD/system" "$MOD/system/app" "$MOD/system/app/Argosy"
    chmod 644 "$MOD/system/app/Argosy/"*.apk "$MOD/module.prop"
    chcon -R u:object_r:system_file:s0 "$MOD/system" 2>/dev/null

    [ -f "$MOD/system/app/Argosy/base.apk" ] || fail "module built but base.apk is missing"
    [ -f "$MOD/module.prop" ] || fail "module built but module.prop is missing"
    apply_runtime_hardening
    log "OK: module staged at $MOD"
    log "NEXT: reboot, then set Argosy as your default launcher."
else
    if [ -d /data/adb/modules ]; then
        log "/data/adb/modules exists but no root manager was found; it is probably left over"
    fi
    log "no root manager, trying a direct /system remount"

    mount -o rw,remount /system 2>/dev/null
    mount -o rw,remount / 2>/dev/null

    if ! touch /system/.argosy-write-test 2>/dev/null; then
        log "/system is still read-only after remounting (remount reports success and changes nothing)"
        if [ "$(getprop ro.boot.dynamic_partitions)" = "true" ]; then
            log "this device uses dynamic partitions, so /system lives in a read-only super partition"
        fi
        if [ "$(getprop ro.boot.veritymode)" = "enforcing" ]; then
            log "dm-verity is enforcing, so /system cannot be modified in place"
        fi
        log "no root manager is installed, so there is no module to overlay /system with either"
        log "Argosy cannot become a system app here."
        apply_runtime_hardening
        log "PARTIAL: Argosy is now protected from being killed in the background."
        log "To make it a system app as well, install Magisk, KernelSU or APatch and re-run."
        log "--- run ended ---"
        exit 0
    fi
    rm -f /system/.argosy-write-test

    mkdir -p /system/app/Argosy || fail "cannot create /system/app/Argosy"
    copy_apks /system/app/Argosy || fail "could not copy the apk into /system/app"
    chmod 755 /system/app/Argosy
    chmod 644 /system/app/Argosy/*.apk
    chcon -R u:object_r:system_file:s0 /system/app/Argosy 2>/dev/null
    mount -o ro,remount /system 2>/dev/null || mount -o ro,remount / 2>/dev/null

    apply_runtime_hardening
    [ -f /system/app/Argosy/base.apk ] || fail "copy reported success but base.apk is missing"
    log "OK: copied to /system/app/Argosy"
    log "NEXT: reboot, then set Argosy as your default launcher."
fi

log "--- run ended ---"
log "after rebooting, confirm with: dumpsys package $PKG | grep -E 'codePath|pkgFlags'"
log "it worked if codePath is under /system and pkgFlags contains SYSTEM"
