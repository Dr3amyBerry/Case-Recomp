#!/usr/bin/env bash
set -euo pipefail
API_LEVEL="${1:?api level}"
gradle --no-daemon :app:connectedDebugAndroidTest
if [ "$API_LEVEL" = "26" ]; then
  bash ../tools/android_external_qa.sh \
    ../phase8-download/case-recomp-synthetic-baseline.apk \
    ../phase8-download/case-recomp-synthetic-debug.apk \
    ../phase8-runtime
fi
