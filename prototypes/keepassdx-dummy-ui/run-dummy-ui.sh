#!/usr/bin/env bash
# Run only in disposable emulator job, after reviewed overlay/build/boot steps.
set -euo pipefail
: "${RUNNER_TEMP:?}"
: "${ANDROID_HOME:?}"
: "${ANDROID_SERIAL:?}"
test "$ANDROID_SERIAL" = emulator-5554
target=com.vanipy.saku.synthetic
tests=com.vanipy.saku.synthetic.test
cleanup() {
  adb uninstall "$tests" || true
  adb shell pm clear "$target" || true
  adb uninstall "$target" || true
}
trap cleanup EXIT
cd "$RUNNER_TEMP/keepassdx-upstream"
export GRADLE_USER_HOME="$RUNNER_TEMP/saku-ui-gradle"
./gradlew --no-daemon --console=plain --max-workers=2 -PdummyPixelCapture=true \
  :dummy-ui:assembleDebug :dummy-ui:assembleDebugAndroidTest
app=dummy-ui/build/outputs/apk/debug/dummy-ui-debug.apk
testapk=dummy-ui/build/outputs/apk/androidTest/debug/dummy-ui-debug-androidTest.apk
test -f "$app"
test -f "$testapk"
python3 - <<'NATIVE'
import zipfile
with zipfile.ZipFile('dummy-ui/build/outputs/apk/debug/dummy-ui-debug.apk') as apk:
    libs=[n for n in apk.namelist() if n.startswith('lib/x86_64/') and n.endswith('.so')]
    if not libs: raise SystemExit('Missing x86_64 native library packaging')
    print('Packaged x86_64 libraries:', ', '.join(libs))
NATIVE
badging="$RUNNER_TEMP/dummy-ui-badging.txt"
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$app" > "$badging"
printf '%s\n' 'Synthetic APK badging preflight (package/sdk/debug/permissions):'
grep -E "^(package|sdkVersion|targetSdkVersion|application-debuggable|uses-permission)" "$badging" || true
check_badging() {
  local pattern="$1" description="$2"
  if grep -Eq "$pattern" "$badging"; then
    printf 'PASS: %s\n' "$description"
  else
    printf 'FAIL: %s\n' "$description" >&2
    exit 1
  fi
}
if grep -q '^uses-permission' "$badging"; then
  printf '%s\n' 'FAIL: unexpected merged target permission' >&2
  exit 1
else
  printf '%s\n' 'PASS: no merged target permissions'
fi
check_badging "^package: name='$target' " 'expected isolated synthetic package id'
# Badging is a summary, not authoritative evidence when sdkVersion is absent.
# Read the SAME packaged APK with the SDK's manifest analyzer; never use source DSL.
python3 - "$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" "$app" <<'SDKCHECK'
import pathlib, re, subprocess, sys, xml.etree.ElementTree as ET
analyzer, apk = sys.argv[1:]
if not pathlib.Path(analyzer).is_file():
    raise SystemExit('FAIL: packaged-manifest analyzer unavailable')
def read_manifest(command):
    result = subprocess.run([analyzer, 'manifest', command, apk],
                            check=True, capture_output=True, text=True)
    return result.stdout.strip()
minimum = read_manifest('min-sdk')
target_sdk = read_manifest('target-sdk')
xml = read_manifest('print')
print('Packaged APK manifest SDK diagnostics:')
print('min-sdk=' + repr(minimum) + '; target-sdk=' + repr(target_sdk))
print(xml)
# Require strict scalar output, not a missing value, codename or fallback default.
if not re.fullmatch(r'26', minimum) or not re.fullmatch(r'36', target_sdk):
    raise SystemExit('FAIL: packaged APK SDK scalar mismatch (require min26/target36)')
manifest = ET.fromstring(xml)
ns = '{http://schemas.android.com/apk/res/android}'
sdk = manifest.findall('uses-sdk')
if (manifest.tag != 'manifest' or len(sdk) != 1
        or sdk[0].get(ns + 'minSdkVersion') != '26'
        or sdk[0].get(ns + 'targetSdkVersion') != '36'):
    raise SystemExit('FAIL: packaged manifest must contain exactly one min26/target36 uses-sdk')
print('PASS: packaged APK minimum SDK26 and target SDK36, scalar and manifest XML agree')
SDKCHECK
check_badging '^application-debuggable$' 'debuggable test APK'
printf '%s\n' 'Preflight passed; proceeding to install and run exactly four UI methods.'
# Fresh AVD only; no preexisting app may be reused to mask state differences.
if adb shell pm path "$target" | grep -q '^package:'; then
  printf '%s\n' 'Unexpected installed synthetic target'
  exit 1
fi
adb install "$app"
adb install "$testapk"
# Direct instrumentation, NOT connectedAndroidTest: do not uninstall before pixels copy.
# Pipe status does not prove instrumentation passed; parse all final method statuses below.
adb shell am instrument -w -r \
  -e class com.kunzisoft.keepass.tests.saku.DummyImportActivityTest \
  "$tests/androidx.test.runner.AndroidJUnitRunner" | tee "$RUNNER_TEMP/dummy-ui-instrumentation.txt"
python3 - <<'PY'
import collections, os, pathlib, re
text = (pathlib.Path(os.environ['RUNNER_TEMP'])/'dummy-ui-instrumentation.txt').read_text()
expected = {
 'previewThenCancelKeepsApproveDisabledUntilFreshReview',
 'approveRequiresPreviewThenShowsReopenedFileResult',
 'explicitLockHidesPreviewAndRequiresFreshUnlock',
 'backgroundEndsSessionInsteadOfResumingUnlockedPreview',
}
status = {}; passed = collections.Counter(); started = collections.Counter()
for line in text.splitlines():
    line = line.rstrip('\r')
    if line.startswith('INSTRUMENTATION_STATUS: '):
        key, sep, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
        if sep: status[key] = value
    elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
        code = int(line.rsplit(': ',1)[1])
        name = status.get('test'); klass = status.get('class')
        if code not in (0,1): raise SystemExit('Failed/skipped/ignored instrumentation method')
        if name not in expected or klass != 'com.kunzisoft.keepass.tests.saku.DummyImportActivityTest':
            raise SystemExit('Unexpected instrumentation method/class')
        (started if code == 1 else passed)[name] += 1
        status = {}
if (set(passed) != expected or set(started) != expected
    or any(n != 1 for n in passed.values()) or any(n != 1 for n in started.values())
    or not re.search(r'^INSTRUMENTATION_CODE: -1\s*$',text,re.M)
    or 'INSTRUMENTATION_FAILED:' in text or 'INSTRUMENTATION_ABORTED:' in text):
    raise SystemExit('Missing/partial instrumentation evidence')
print('Exact four UI wiring methods completed, no skipped methods. Pixels still need inspection.')
PY
# Only six exact filenames are read. No app database, vault, key or logs are exported.
pixels="$RUNNER_TEMP/saku-ui-pixels"
mkdir "$pixels"
for name in 01-locked 02-preview 03-cancelled 04-approved 05-relocked 06-launcher-after-session-ended; do
  adb exec-out run-as "$target" cat "cache/dummy-ui-pixels/$name.png" > "$pixels/$name.png"
done
# Ordinary job logs only, no upload-artifact/cache and no billed storage dependency.
# Bounded PNG blocks can be recovered from authenticated run logs before app uninstall.
python3 - <<'PY'
import base64, hashlib, os, pathlib, struct
root = pathlib.Path(os.environ['RUNNER_TEMP'])/'saku-ui-pixels'
for path in sorted(root.glob('*.png')):
    raw = path.read_bytes()
    if not 64 <= len(raw) <= 1024*1024: raise SystemExit('PNG outside fixed 1MiB bound')
    if raw[:8] != b'\x89PNG\r\n\x1a\n' or raw[12:16] != b'IHDR': raise SystemExit('Not a PNG')
    width,height = struct.unpack('>II',raw[16:24])
    if not (1 <= width <= 2560 and 1 <= height <= 2560): raise SystemExit('Unexpected image size')
    name=path.name; sha=hashlib.sha256(raw).hexdigest()
    print(f'SAKU_DUMMY_PNG_BEGIN {name} {len(raw)} {sha} {width} {height}', flush=True)
    encoded=base64.b64encode(raw).decode('ascii')
    for start in range(0,len(encoded),120): print('SAKU_DUMMY_PNG_DATA '+encoded[start:start+120])
    print('SAKU_DUMMY_PNG_END '+name, flush=True)
PY
printf '%s\n' 'Six dummy PNGs exported to ordinary logs. Actual visual inspection remains required.' >> "$GITHUB_STEP_SUMMARY"
