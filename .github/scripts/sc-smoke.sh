#!/bin/bash
# SC smoke test in the CI emulator: install the APK, open it, check it is still alive after 40 s without a crash.
# Prefers the release APK when it carries our signature (main/tags), otherwise the debug one.
set -u
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
mkdir -p evidence
APK=""
for f in apk/release/*.apk; do
  [ -f "$f" ] && "$BT/apksigner" verify --print-certs "$f" 2>/dev/null | grep -qi "SHA-256 digest: $SC_CERT_SHA256" && APK=$f
done
[ -z "$APK" ] && APK=$(ls apk/debug/*.apk | head -1)
PKG=$("$BT/aapt2" dump packagename "$APK")
echo "APK=$APK PKG=$PKG" | tee evidence/apk.txt
adb install -r -g "$APK" || exit 1
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 40
adb shell screencap -p /sdcard/s1.png && adb pull /sdcard/s1.png evidence/pantalla.png
adb logcat -d > evidence/logcat.txt
PID=$(adb shell pidof "$PKG" | tr -d '\r')
echo "pid=$PID" | tee -a evidence/apk.txt
if grep -q "FATAL EXCEPTION" evidence/logcat.txt && grep -A3 "FATAL EXCEPTION" evidence/logcat.txt | grep -q "$PKG"; then
  echo "::error::the app crashed"; grep -A20 "FATAL EXCEPTION" evidence/logcat.txt | head -40; exit 1
fi
[ -n "$PID" ] || { echo "::error::the app is not running after 40 s"; exit 1; }
echo "SMOKE OK"
