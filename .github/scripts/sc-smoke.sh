#!/bin/bash
# SC smoke test in the CI emulator.
#  1. Release APK (only when it carries our signature, i.e. main / v* tags): installs, opens, still alive after 40 s.
#  2. Debug APK: first-run gates pre-accepted (legal agreement, storage), then sc-ui-check.mjs walks our screens
#     through the WebView DevTools protocol and takes a screenshot of each one. Evidence goes to evidence/.
set -u
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
mkdir -p evidence
FALLO=0
# Same screen as the phone players reported problems on (Samsung S25 Ultra at its default FHD+): the game menus pick
# their scale from the window size, so a small emulator screen would hide what players see
adb shell wm size 2340x1080
adb shell wm density 450

alive_check() { # $1 = package, $2 = tag
  # Same as the debug launch below: right after boot Google Play services restarts and Android kills any app using its
  # font provider («depends on provider … FontsProvider in dying proc»). That is the emulator, not the app (it failed
  # the 0.1.20 and 0.1.22 runs): let it settle, and launch again once if that is what killed it.
  sleep 60
  local intento
  for intento in 1 2; do
  adb logcat -c
  adb shell monkey -p "$1" -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 40
  [ -n "$(adb shell pidof "$1" | tr -d '\r')" ] && break
  adb logcat -d | grep -q "Killing.*$1.*FontsProvider in dying proc" || break
  echo "$2 launch $intento: killed by the Play services restart, launching again" | tee -a evidence/apk.txt
  done
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
# Streamed to a file as it happens, so the log survives even if the emulator itself dies
(adb logcat -v time > evidence/logcat-live.txt 2>&1 &)
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
# Test app update (phase 5): a manifest offering the same debug build as a much newer version (versionCode 999999)
cp apk/debugci/*.apk packci/app-ci.apk
printf '{"versionCode":999999,"versionName":"9.9.9-ci","notas":"Prueba de CI.","apks":{"x86_64":{"url":"http://10.0.2.2:8000/app-ci.apk","sha256":"%s","size":%s}}}\n' \
  "$(sha256sum packci/app-ci.apk | cut -d' ' -f1)" "$(stat -c %s packci/app-ci.apk)" > packci/sc-app.json
adb shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow
cp .github/scripts/fake-authorize.html packci/
# Microsoft's real sign-in page for the app's exact request (client ID, nativeclient redirect, PKCE), checked from the
# runner because the emulator cannot draw it: it must answer with the sign-in form, not an AADSTS error.
MS_URL="https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=1ce6e35a-126f-48fd-97fb-54d143ac6d45&response_type=code&redirect_uri=https%3A%2F%2Flogin.microsoftonline.com%2Fcommon%2Foauth2%2Fnativeclient&scope=XboxLive.signin%20offline_access&prompt=select_account&state=ci$RANDOM$RANDOM&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
MS_CODE=$(curl -sL -A "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36" -o evidence/microsoft-authorize.html -w '%{http_code} %{url_effective}' "$MS_URL")
echo "Microsoft authorize: $MS_CODE" | tee -a evidence/apk.txt
# Accepted = HTTP 200 on Microsoft's consumer sign-in form (login.live.com/oauth20_authorize.srf) and no AADSTS error
if grep -qo "AADSTS[0-9]*" evidence/microsoft-authorize.html || ! echo "$MS_CODE" | grep -q "^200 https://login.live.com/oauth20_authorize.srf"; then
  echo "::error::Microsoft's sign-in page refuses the app's request: $(grep -o 'AADSTS[0-9]*[^<]*' evidence/microsoft-authorize.html | head -1)"
  FALLO=1
fi
(cd packci && python3 -m http.server 8000 >/dev/null 2>&1 &)
sleep 2
node .github/scripts/sc-ui-check.mjs "$PKG" || FALLO=1
timeout 60 adb logcat -d > evidence/debug-logcat.txt
if grep -A3 "FATAL EXCEPTION" evidence/debug-logcat.txt | grep -q "$PKG"; then
  echo "::error::debug: the app crashed"; grep -A20 "FATAL EXCEPTION" evidence/debug-logcat.txt | head -40; FALLO=1
fi
[ $FALLO -eq 0 ] && echo "SMOKE OK"
# The result is kept in a file and judged by a later step: the emulator has hung on shutdown after the game ran,
# and that must not decide (or stall) the job. Kill it ourselves.
# The app's own log (FCL Logging: «SC pack: …» lines and full stack traces that never reach logcat)
adb pull /sdcard/SCLauncher/log evidence/app-log >/dev/null 2>&1 || true
echo "$FALLO" > evidence/resultado.txt
adb emu kill >/dev/null 2>&1 || true
sleep 10
# Killing qemu alone was not enough: the step still waited until its 45-min cap (28-10: script done at 02:34, step
# ended 03:06). The action keeps the emulator's output pipes, and its helpers (netsimd, crashpad_handler, the emulator
# launcher) hold them. Note what is left, then kill everything run from the emulator folder and our own background jobs.
ps -eo pid,ppid,etime,cmd | grep -E "emulator|qemu|netsim|crashpad|adb|http.server" | grep -v grep > evidence/procesos-al-cerrar.txt || true
pkill -9 -f "${ANDROID_HOME:-/usr/local/lib/android/sdk}/emulator/" >/dev/null 2>&1 || true
pkill -9 -f qemu-system >/dev/null 2>&1 || true
pkill -9 -f "http.server 8000" >/dev/null 2>&1 || true
pkill -9 -f "adb logcat" >/dev/null 2>&1 || true
# Without this the action's own «adb emu kill» waits on the dead emulator until the step times out
adb kill-server >/dev/null 2>&1 || true
exit 0
