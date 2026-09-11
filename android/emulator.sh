#!/bin/sh
#
# Boots the Android emulator for this project and installs the debug build on it.
#
# Everything lives inside this directory (see .gitignore), so it works even when
# $HOME is not writable:
#
#   .android-sdk/     SDK: platform, build-tools, emulator, system image
#   .android-home/    ANDROID_USER_HOME + AVD home (the emulator's own state)
#
# Usage:
#   ./emulator.sh              boot the AVD and install the app
#   ./emulator.sh --no-install boot only
#   ./emulator.sh --headless   boot with no window (useful for automated checks)
#   ./emulator.sh --wipe       discard the AVD's saved state, then boot
#
set -e

DIR=$(cd "$(dirname "$0")" && pwd)
SDK="$DIR/.android-sdk"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

# Keep AVD state and the SDK's per-user files inside the project.
export ANDROID_USER_HOME="$DIR/.android-home"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"

# The emulator also needs a scratch directory. On macOS it insists on
# ~/Library/Caches/TemporaryItems regardless of TMPDIR, so on a machine where
# that is not writable (a sandbox, CI container, read-only home) it aborts with
# "Failed to create jwk directory ... Operation not permitted". Pointing TMPDIR
# at the project covers the platforms that do respect it; for macOS, grant the
# emulator access to ~/Library/Caches if it still aborts.
mkdir -p "$ANDROID_USER_HOME/tmp"
export TMPDIR="$ANDROID_USER_HOME/tmp/"

AVD_NAME="gamecheckout_api35"
EMULATOR="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
SYSTEM_IMAGE_DIR="system-images/android-35/google_apis/arm64-v8a"

INSTALL=1
HEADLESS=0
WIPE=0
for arg in "$@"; do
    case "$arg" in
        --no-install) INSTALL=0 ;;
        --headless) HEADLESS=1 ;;
        --wipe) WIPE=1 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

if [ ! -x "$EMULATOR" ]; then
    echo "error: emulator not installed at $EMULATOR" >&2
    echo "Install it with:" >&2
    echo "  sdkmanager --sdk_root=$SDK 'emulator' '$SYSTEM_IMAGE'" >&2
    exit 1
fi

mkdir -p "$ANDROID_AVD_HOME"

# ---- create the AVD once -------------------------------------------------
# The AVD is written directly rather than via `avdmanager`, which rejects a
# hand-extracted system image because it insists on a populated SDK repo cache.
# These two files are exactly what avdmanager would produce.
if [ ! -f "$ANDROID_AVD_HOME/$AVD_NAME.ini" ]; then
    if [ ! -d "$SDK/$SYSTEM_IMAGE_DIR" ]; then
        echo "error: system image missing at $SDK/$SYSTEM_IMAGE_DIR" >&2
        echo "Install it with:" >&2
        echo "  sdkmanager --sdk_root=$SDK 'emulator' 'system-images;android-35;google_apis;arm64-v8a'" >&2
        exit 1
    fi

    echo "==> creating AVD $AVD_NAME"
    mkdir -p "$ANDROID_AVD_HOME/$AVD_NAME.avd"

    cat > "$ANDROID_AVD_HOME/$AVD_NAME.ini" <<EOF
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/$AVD_NAME.avd
path.rel=avd/$AVD_NAME.avd
target=android-35
EOF

    cat > "$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini" <<EOF
avd.ini.encoding=UTF-8
AvdId=$AVD_NAME
PlayStore.enabled=false
abi.type=arm64-v8a
avd.ini.displayname=Game Checkout API 35
disk.dataPartition.size=6442450944
fastboot.forceColdBoot=yes
hw.accelerometer=yes
hw.audioInput=yes
hw.battery=yes
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=arm64
hw.cpu.ncore=4
hw.dPad=no
hw.device.manufacturer=Google
hw.device.name=pixel_6
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.gsmModem=yes
hw.keyboard=yes
hw.lcd.density=440
hw.lcd.height=2400
hw.lcd.width=1080
hw.mainKeys=no
hw.ramSize=2048
hw.sdCard=no
hw.sensors.orientation=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=$SYSTEM_IMAGE_DIR/
runtime.network.latency=none
runtime.network.speed=full
tag.display=Google APIs
tag.id=google_apis
vm.heapSize=256
EOF
fi

# ---- boot ----------------------------------------------------------------
BOOT_ARGS=""
if [ "$HEADLESS" -eq 1 ]; then
    BOOT_ARGS="-no-window -no-audio -no-boot-anim"
fi
if [ "$WIPE" -eq 1 ]; then
    BOOT_ARGS="$BOOT_ARGS -wipe-data"
fi

if "$ADB" devices | grep -q "^emulator-.*device$"; then
    echo "==> an emulator is already running"
else
    echo "==> booting $AVD_NAME (this takes a minute on first run)"
    # shellcheck disable=SC2086
    "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-save -gpu swiftshader_indirect $BOOT_ARGS &
fi

echo "==> waiting for the device to come online"
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 2
done
echo "==> booted: $("$ADB" shell getprop ro.build.version.release | tr -d '\r') (API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r'))"

if [ "$INSTALL" -eq 1 ]; then
    # A project-local debug keystore may be required in locked-down environments.
    if [ ! -f "$DIR/app/debug/debug.keystore" ] && [ ! -f "$HOME/.android/debug.keystore" ]; then
        echo "==> generating a project-local debug keystore"
        mkdir -p "$DIR/app/debug"
        keytool -genkeypair -keystore "$DIR/app/debug/debug.keystore" \
            -storepass android -keypass android \
            -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=Android Debug,O=Android,C=US"
    fi

    echo "==> building and installing"
    "$DIR/gradlew" :app:installDebug

    echo "==> launching ws.chill.gamecheckout/.MainActivity"
    "$ADB" shell am start -n ws.chill.gamecheckout/.MainActivity
fi

echo
echo "The emulator is up. Useful commands:"
echo "  $ADB shell am start -n ws.chill.gamecheckout/.MainActivity   # launch the app"
echo "  $ADB logcat -s GameCheckout                                  # app logs"
echo "  $ADB exec-out screencap -p > /tmp/screen.png                 # screenshot"
echo "  $ADB emu kill                                                # shut it down"
