#!/system/bin/sh
# Make Argosy a system app so it survives memory pressure from heavy emulators.
#
# Run this through your device's "run script as root" setting (it executes as
# root and makes the script executable for you). Do NOT run it over adb.
#
# Argosy declares android:persistent="true", which Android only honors for apps
# on /system. Placing it there keeps the launcher process from being OOM-killed
# while a heavy emulator runs and lifts non-system-app limits.
#
# Prefers a Magisk module (systemless, reversible, works on read-only dynamic
# partitions); falls back to a direct /system remount only if writable. Lands in
# /system/app, not priv-app, to avoid privapp-permissions bootloops.

PKG="com.nendo.argosy"
MODULE_ID="argosy_systemize"

log() { echo "[systemize-argosy] $1"; }

if [ "$(id -u)" != "0" ]; then
    log "Not running as root. Use the device's 'run script as root' option."
    exit 1
fi

APK=$(pm path "$PKG" 2>/dev/null | head -n1 | sed 's/^package://' | tr -d '\r')
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    log "Package $PKG not installed. Install Argosy normally first."
    exit 1
fi
log "Found APK: $APK"

if [ -d /data/adb/modules ]; then
    log "Magisk detected - installing systemless module."
    MOD="/data/adb/modules/$MODULE_ID"
    rm -rf "$MOD"
    mkdir -p "$MOD/system/app/Argosy"
    cp "$APK" "$MOD/system/app/Argosy/Argosy.apk"
    cat > "$MOD/module.prop" <<EOF
id=$MODULE_ID
name=Argosy Systemize
version=1.0
versionCode=1
author=argosy
description=Mounts Argosy into /system/app so android:persistent takes effect
EOF
    chmod 755 "$MOD" "$MOD/system" "$MOD/system/app" "$MOD/system/app/Argosy"
    chmod 644 "$MOD/system/app/Argosy/Argosy.apk" "$MOD/module.prop"
    chcon -R u:object_r:system_file:s0 "$MOD/system" 2>/dev/null
    log "Module installed. Reboot, then set Argosy as your default launcher."
else
    log "No Magisk - trying direct /system remount."
    if ! { mount -o rw,remount /system 2>/dev/null || mount -o rw,remount / 2>/dev/null; }; then
        log "Cannot remount /system read-write (likely a dynamic partition)."
        log "Install Magisk and re-run."
        exit 1
    fi
    mkdir -p /system/app/Argosy
    cp "$APK" /system/app/Argosy/Argosy.apk
    chmod 755 /system/app/Argosy
    chmod 644 /system/app/Argosy/Argosy.apk
    chcon -R u:object_r:system_file:s0 /system/app/Argosy 2>/dev/null
    mount -o ro,remount /system 2>/dev/null || mount -o ro,remount / 2>/dev/null
    log "Copied to /system/app. Reboot, then set Argosy as your default launcher."
fi

log "Verify after reboot: dumpsys package $PKG | grep -E 'flags|PERSISTENT' shows SYSTEM and PERSISTENT."
