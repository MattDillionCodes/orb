#!/usr/bin/env bash
# Build OrbDash and run it on its own dedicated Android TV emulator (OrbDash_TV).
# Never touches other emulators or physical TVs: every adb call targets the serial
# whose AVD name is verified to be OrbDash_TV.
set -euo pipefail

AVD="OrbDash_TV"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
IMAGE="system-images;android-34;android-tv;arm64-v8a"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

avd_name() { "$ADB" -s "$1" emu avd name 2>/dev/null | head -1 | tr -d '\r'; }

find_serial() {
  for s in $("$ADB" devices | awk '/^emulator-/{print $1}'); do
    [ "$(avd_name "$s")" = "$AVD" ] && { echo "$s"; return; }
  done
}

# 1. Create the AVD the first time.
if ! "$EMU" -list-avds | grep -qx "$AVD"; then
  echo "Creating $AVD..."
  echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" -k "$IMAGE" -d tv_1080p >/dev/null
  cfg="$HOME/.android/avd/$AVD.avd/config.ini"
  printf 'hw.ramSize=2048\nhw.keyboard=yes\nhw.gpu.enabled=yes\nhw.gpu.mode=host\n' >> "$cfg"
fi

# 2. Boot it on a free port if it isn't already running.
SERIAL="$(find_serial || true)"
if [ -z "$SERIAL" ]; then
  for p in $(seq 5584 2 5680); do
    lsof -nP -iTCP:"$p" -iTCP:"$((p + 1))" -sTCP:LISTEN >/dev/null 2>&1 || { PORT=$p; break; }
  done
  echo "Booting $AVD on port $PORT..."
  nohup "$EMU" -avd "$AVD" -port "$PORT" -no-snapshot-save -no-boot-anim >/tmp/orbdash-emulator.log 2>&1 &
  SERIAL="emulator-$PORT"
  for _ in $(seq 1 120); do
    [ "$(avd_name "$SERIAL")" = "$AVD" ] && [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 2
  done
fi
[ "$(avd_name "$SERIAL")" = "$AVD" ] || { echo "Could not reach $AVD (see /tmp/orbdash-emulator.log)"; exit 1; }
echo "Using $SERIAL ($AVD)"

# 3. Build, install, and make OrbDash the Home app on this emulator only.
"$ROOT/gradlew" -p "$ROOT" :app:assembleDebug -q
"$ADB" -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
"$ADB" -s "$SERIAL" shell cmd package set-home-activity com.orbdash/.MainActivity >/dev/null
# The stock TV launcher always wins the Home button, so switch it off here (undo: pm enable).
"$ADB" -s "$SERIAL" shell pm disable-user --user 0 com.google.android.tvlauncher >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell am start -n com.orbdash/.MainActivity >/dev/null
echo "OrbDash is running on $AVD."
