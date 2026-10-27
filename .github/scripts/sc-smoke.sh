#!/bin/bash
# SC smoke test in the CI emulator.
#  1. Release APK (only when it carries our signature, i.e. main / v* tags): installs, opens, still alive after 40 s.
#  2. Debug APK: first-run gates pre-accepted (legal agreement, storage), then sc-ui-check.mjs walks our screens
#     through the WebView DevTools protocol and takes a screenshot of each one. Evidence goes to evidence/.
set -u
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
mkdir -p evidence
FALLO=0

alive_check() { # $1 = package, $2 = tag
  adb logcat -c
  adb shell monkey -p "$1" -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 40
  adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png "evidence/$2-arranque.png" >/dev/null
  adb logcat -d > "evidence/$2-logcat.txt"
  local pid; pid=$(adb shell pidof "$1" | tr -d '\r')
  echo "$2 pid=$pid" | tee -a evidence/apk.txt
  if grep -A3 "FATAL EXCEPTION" "evidence/$2-logcat.txt" | grep -q "$1"; then
    echo "::error::$2: the app crashed"; grep -A20 "FATAL EXCEPTION" "evidence/$2-logcat.txt" | head -40; FALLO=1
  fi
  [ -n "$pid" ] || { echo "::error::$2: the app is not running after 40 s"; FALLO=1; }
}

# ---- 1. Signed release ----
for f in apk/release/*.apk; do
  [ -f "$f" ] || continue
  if "$BT/apksigner" verify --print-certs "$f" 2>/dev/null | grep -qi "SHA-256 digest: $SC_CERT_SHA256"; then
    PKG=$("$BT/aapt2" dump packagename "$f")
    echo "release APK=$f PKG=$PKG" | tee -a evidence/apk.txt
    adb install -r -g "$f" || { echo "::error::release install failed"; exit 1; }
    alive_check "$PKG" release
    adb uninstall "$PKG" >/dev/null
  fi
done

# ---- 2. Debug APK: our screens ----
APK=$(ls apk/debug/*.apk | head -1)
PKG=$("$BT/aapt2" dump packagename "$APK")
echo "debug APK=$APK PKG=$PKG" | tee -a evidence/apk.txt
adb install -r -g "$APK" || { echo "::error::debug install failed"; exit 1; }
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow
# First-run gates of FCL (LegalAgreement.VERSION = 2, storage notice) are accepted here, not tapped through.
adb shell run-as "$PKG" mkdir -p shared_prefs
adb shell "run-as $PKG sh -c 'cat > shared_prefs/launcher.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="legal_agreement_version" value="2" />
    <boolean name="isAgree" value="true" />
</map>
XML
adb logcat -c
# Right after boot Google Play services restarts itself, and Android kills any app using its font provider with it
# (seen in CI: "depends on provider ... FontsProvider in dying proc"). Let it settle, and relaunch once if that happens.
sleep 60
PID=""
for intento in 1 2 3; do
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
  # Wait for MainActivity (the runtimes are unpacked on first start; that can take a few minutes on the emulator)
  for i in $(seq 1 60); do
    adb shell dumpsys activity activities | grep -q "mResumedActivity.*MainActivity\|topResumedActivity.*MainActivity" && break
    sleep 5
  done
  sleep 15
  PID=$(adb shell pidof "$PKG" | tr -d '\r')
  [ -n "$PID" ] && break
  echo "launch $intento: the app died right after starting, relaunching" | tee -a evidence/apk.txt
done
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png evidence/debug-0-tras-arranque.png >/dev/null
[ -n "$PID" ] || { echo "::error::debug app not running"; adb logcat -d > evidence/debug-logcat.txt; exit 1; }
adb forward tcp:9222 "localabstract:webview_devtools_remote_$PID"
# Test update of the modpack, served from the runner (the emulator reaches the host at 10.0.2.2)
python3 .github/scripts/sc-pack-ci.py packci || { echo "::error::could not build the test pack"; exit 1; }
(cd packci && python3 -m http.server 8000 >/dev/null 2>&1 &)
sleep 2
node .github/scripts/sc-ui-check.mjs "$PKG" || FALLO=1
adb logcat -d > evidence/debug-logcat.txt
if grep -A3 "FATAL EXCEPTION" evidence/debug-logcat.txt | grep -q "$PKG"; then
  echo "::error::debug: the app crashed"; grep -A20 "FATAL EXCEPTION" evidence/debug-logcat.txt | head -40; FALLO=1
fi
[ $FALLO -eq 0 ] && echo "SMOKE OK"
# The result is kept in a file and judged by a later step: the emulator has hung on shutdown after the game ran,
# and that must not decide (or stall) the job. Kill it ourselves.
echo "$FALLO" > evidence/resultado.txt
adb emu kill >/dev/null 2>&1 || true
sleep 10
pkill -9 -f qemu-system >/dev/null 2>&1 || true
exit 0
