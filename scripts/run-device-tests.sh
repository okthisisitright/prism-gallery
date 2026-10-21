#!/usr/bin/env bash
# Keep diagnostics available even when Gradle's UTP runner only prints a report path.
set -uo pipefail
./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
prism_test_result=$?
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for path in Path('app/build/outputs/androidTest-results').rglob('TEST-*.xml'):
    root = ET.parse(path).getroot()
    for case in root.iter('testcase'):
        failures = list(case.findall('failure')) + list(case.findall('error'))
        print(('FAIL' if failures else 'PASS') + ' ' + case.get('classname', '') + '.' + case.get('name', ''))
        for failure in failures:
            detail = (failure.text or failure.get('message', 'Unknown failure'))[:7000]
            print(detail)
            escaped = detail.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
            print('::error title=Android device test::' + escaped)
PY
adb logcat -d -s AndroidRuntime TestRunner > device-logcat.txt || true
tail -160 device-logcat.txt
adb exec-out run-as com.prism.gallery tar -C files -cf - screenshots > ui-screenshots.tar 2>/dev/null || rm -f ui-screenshots.tar
exit "$prism_test_result"
