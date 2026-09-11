#!/bin/sh
#
# Builds and runs the iOS app on an iOS Simulator.
#
#   ./run-simulator.sh                    # boot, build, install, launch
#   ./run-simulator.sh --device "iPhone 17"   # pick a different simulator
#   ./run-simulator.sh --list             # show available simulators
#   ./run-simulator.sh --no-build         # reinstall the last build only
#
# Build output goes to .ios-build/ inside this directory (gitignored).
#
# Note: SwiftPM keeps its manifest cache in ~/Library/Caches/org.swift.swiftpm
# and offers no flag to relocate it. On a machine where that path is not
# writable (sandbox, CI container), package resolution fails with
#   cannot open file '.../ManifestLoading/<pkg>.dia' (Operation not permitted)
# and xcodebuild needs to be granted access to it.
#
set -e

DIR=$(cd "$(dirname "$0")" && pwd)
cd "$DIR"

DEVICE_NAME="iPhone 15 Pro"
DERIVED=".ios-build/DerivedData"
BUNDLE_ID="ws.chill.gamecheckout"
BUILD=1

while [ $# -gt 0 ]; do
    case "$1" in
        --device) DEVICE_NAME="$2"; shift 2 ;;
        --list)
            xcrun simctl list devices available
            exit 0
            ;;
        --no-build) BUILD=0; shift ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
done

if [ ! -d GameCheckout.xcodeproj ]; then
    echo "==> GameCheckout.xcodeproj is missing; generating it with xcodegen"
    command -v xcodegen >/dev/null 2>&1 || {
        echo "error: xcodegen not installed (brew install xcodegen)" >&2
        exit 1
    }
    xcodegen generate
fi

# Resolve the device name to a UDID. Names repeat across runtimes, which makes
# -destination "name=..." ambiguous, so always pass an explicit id.
UDID=$(xcrun simctl list devices available \
    | grep -F "$DEVICE_NAME (" | head -1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')
if [ -z "$UDID" ]; then
    echo "error: no available simulator named '$DEVICE_NAME'" >&2
    echo "Run '$0 --list' to see the options." >&2
    exit 1
fi

if [ "$BUILD" -eq 1 ]; then
    echo "==> building for $DEVICE_NAME ($UDID)"
    xcodebuild -project GameCheckout.xcodeproj -scheme GameCheckout \
        -destination "id=$UDID" -derivedDataPath "$DERIVED" build \
        | grep -E "error:|warning:|BUILD SUCCEEDED|BUILD FAILED" || true
fi

APP="$DERIVED/Build/Products/Debug-iphonesimulator/GameCheckout.app"
if [ ! -d "$APP" ]; then
    echo "error: no build at $APP — run without --no-build" >&2
    exit 1
fi

echo "==> booting the simulator"
xcrun simctl boot "$UDID" 2>/dev/null || true

echo "==> installing"
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$UDID" "$APP"

echo "==> launching"
xcrun simctl launch "$UDID" "$BUNDLE_ID"

# Surface the device window on screen.
open -a Simulator

cat <<EOF

Running on $DEVICE_NAME.

  xcrun simctl io "$UDID" screenshot /tmp/ios.png     # screenshot
  xcrun simctl terminate "$UDID" $BUNDLE_ID           # quit the app
  xcrun simctl shutdown "$UDID"                       # shut the device down
EOF
