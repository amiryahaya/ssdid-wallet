#!/bin/bash
# Run all Maestro E2E tests
# Usage: ./run-all.sh [android|ios]
#
# Prerequisites:
#   brew install maestro
#   Android: emulator running or device connected (adb devices)
#   iOS: simulator running (xcrun simctl list devices | grep Booted)

set -e

PLATFORM=${1:-android}
FLOWS_DIR="$(dirname "$0")/flows"
RESULTS_DIR="$(dirname "$0")/results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RESULTS_DIR"

echo "=== SSDID Wallet E2E Tests ($PLATFORM) ==="
echo "Results: $RESULTS_DIR"
echo ""

PASS=0
FAIL=0
SKIP=0

for flow in "$FLOWS_DIR"/uc*.yaml; do
    name=$(basename "$flow" .yaml)
    echo -n "Running $name... "

    if maestro test "$flow" --format junit --output "$RESULTS_DIR/$name.xml" 2>"$RESULTS_DIR/$name.log"; then
        echo "✅ PASS"
        ((PASS++))
    else
        echo "❌ FAIL (see $RESULTS_DIR/$name.log)"
        ((FAIL++))
    fi
done

echo ""
echo "=== Results ==="
echo "✅ Passed: $PASS"
echo "❌ Failed: $FAIL"
echo "📁 Reports: $RESULTS_DIR"

exit $FAIL
