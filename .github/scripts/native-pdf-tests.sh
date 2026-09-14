#!/usr/bin/env bash
# Preserve the test result while collecting previews before emulator shutdown.
native_test_exit=0
gradle :app:connectedDebugAndroidTest --no-daemon --stacktrace || native_test_exit=$?
adb pull /sdcard/Download/AudioPdfPreviews app/build/pdf-device-previews || true
exit "$native_test_exit"
