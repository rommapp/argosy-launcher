#!/system/bin/sh

PKG="${1:-com.nendo.argosy}"
MODULE_ID="argosy_systemize"
MOD="/data/adb/modules/$MODULE_ID"
SYSTEM_DIR="/system/app/Argosy"

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
    echo "[unsystemize-argosy] $1"
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >&3
}

fail() {
    log "FAILED: $1"
    log "--- run ended ---"
    exit 1
}

log "--- run started (unsystemize) ---"
log "log file: ${LOG:-none writable, stdout only}"
log "uid=$(id -u) shell=$0"

if [ "$(id -u)" != "0" ]; then
    fail "not running as root (uid $(id -u)). Use the device's 'run script as root' option."
fi

CODE_PATHS=$(dumpsys package "$PKG" 2>/dev/null | sed -n 's/^ *codePath=//p' | tr -d '\r' | sort -u)
[ -n "$CODE_PATHS" ] || fail "package $PKG is not installed"

HAS_OUR_SYSTEM_COPY=no
HAS_OTHER_SYSTEM_COPY=no
HAS_DATA_COPY=no
for p in $CODE_PATHS; do
    case "$p" in
        "$SYSTEM_DIR") HAS_OUR_SYSTEM_COPY=yes ;;
        /data/*) HAS_DATA_COPY=yes ;;
        *) HAS_OTHER_SYSTEM_COPY=yes ;;
    esac
done
log "code paths: $(echo $CODE_PATHS)"

if [ "$HAS_OTHER_SYSTEM_COPY" = "yes" ]; then
    fail "Argosy is part of this device's system image, not installed by Argosy's systemize script. It cannot be removed here."
fi
if [ "$HAS_OUR_SYSTEM_COPY" = "no" ]; then
    log "Argosy is not a system app here; nothing to remove."
    log "OK: nothing to do"
    log "--- run ended ---"
    exit 0
fi

if [ -d "$MOD" ]; then
    KIND=module
else
    KIND=direct
fi
log "system copy installed by systemize ($KIND), installed update present: $HAS_DATA_COPY"

if [ "$HAS_DATA_COPY" = "no" ]; then
    if [ "$KIND" = "module" ] && [ -f "$MOD/disable" ]; then
        rm -f "$MOD/disable"
        log "switched the module back on: with no installed update, rebooting with it off would remove Argosy and its data"
    fi
    APKS=$(ls "$SYSTEM_DIR"/*.apk 2>/dev/null)
    [ -n "$APKS" ] || fail "no apk found in $SYSTEM_DIR"
    SESSION=$(pm install-create --staged -r 2>/dev/null | sed -n 's/.*\[\([0-9]*\)\].*/\1/p')
    [ -n "$SESSION" ] || fail "could not open a staged install session"
    for apk in $APKS; do
        size=$(stat -c %s "$apk")
        if ! pm install-write -S "$size" "$SESSION" "$(basename "$apk")" "$apk" >/dev/null 2>&1; then
            pm install-abandon "$SESSION" >/dev/null 2>&1
            fail "could not stage $apk"
        fi
    done
    if ! pm install-commit "$SESSION" 2>&1 | grep -q Success; then
        fail "the staged install was refused; nothing was changed"
    fi
    log "staged Argosy as an installed update; it applies on the next boot"
    log "PARTIAL: reboot, then run this again to finish removing Argosy from the system apps."
    log "--- run ended ---"
    exit 0
fi

if [ "$KIND" = "module" ]; then
    touch "$MOD/remove" || fail "could not mark the module for removal"
    rm -f "$MOD/disable"
    log "module $MODULE_ID marked for removal on the next boot"
else
    mount -o rw,remount /system 2>/dev/null
    mount -o rw,remount / 2>/dev/null
    rm -rf "$SYSTEM_DIR"
    mount -o ro,remount /system 2>/dev/null || mount -o ro,remount / 2>/dev/null
    [ -d "$SYSTEM_DIR" ] && fail "could not remove $SYSTEM_DIR"
    log "removed $SYSTEM_DIR"
fi

log "OK: Argosy will be an ordinary app after a reboot, with its data kept."
log "NEXT: reboot."
log "--- run ended ---"
