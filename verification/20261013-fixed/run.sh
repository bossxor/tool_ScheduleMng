#!/usr/bin/env bash
set -euo pipefail
verification_package=com.bossxor.harugyeol.verification
verification_output=build/verification-fixed/device-results.txt
python3 -c 'import json; assert json.load(open("build/verification-fixed/app-artifacts/outputs/apk/debug/output-metadata.json"))["applicationId"] == "com.bossxor.harugyeol.verification"'
adb install --user 0 build/verification-fixed/app-artifacts/outputs/apk/debug/app-debug.apk
trap 'adb uninstall "$verification_package"' EXIT
adb shell pm grant --user 0 "$verification_package" android.permission.POST_NOTIFICATIONS
adb shell am instrument -w --user 0 "$verification_package/com.bossxor.harugyeol.verification.FixedInstrumentation" | tee "$verification_output"
python3 -c 'from pathlib import Path; text=Path("build/verification-fixed/device-results.txt").read_text(); assert "status=ALL_FIXED_REGRESSIONS_PASSED" in text and "INSTRUMENTATION_CODE: -1" in text, text'
