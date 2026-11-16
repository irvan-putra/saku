# Isolated dummy import screen proposal

Uncompiled/unexecuted UI candidate, separate from the passed seven storage tests.
Not a production import feature. Fixed public dummy credentials/data only.

Proposed disposable module `dummy-ui`:
- main: DummyImportActivity.kt and DummyImportFixture.kt, package
  com.kunzisoft.keepass.tests.saku; manifest/build files as supplied.
- androidTest: DummyImportActivityTest.kt, same package.
- append only `include(":dummy-ui")` to pinned upstream settings in disposable
  checkout. Production app/client, minSdk, branding, dependencies unchanged.
  New module minSdk26; upstream source revision remains pinned.

Four UI tests cover control wiring/status, not tap hitboxes, accessibility or
independent KDBX readback. Background HOME must end the Activity session; its
last screenshot is launcher/session-ended evidence, not a locked-vault image.
Storage replay/recovery remains in the separate seven-test result.

Default FLAG_SECURE remains. Build property `dummyPixelCapture=true` clears it
only in this separate fixed-public-dummy harness; never port this option to real
vault UI. Pixel-enabled debug APKs stay disposable emulator-only, never release.

Proposed execution, not approved or run by this document:
One new manual public standard ubuntu-24.04 job capped45min, pinned upstream plus
hash-verified exact overlay, JDK17/SDK36/NDK25.2.9519653/CMake3.22.1, AOSP API34
x86_64 fresh emulator/KVM required. Same bounded boot/cleanup as storage workflow.
No secret, account, real vault, phone, paid fallback/retry/cache/artifact upload.
Separate new workflow; existing host/storage workflows and results untouched.

Before run: inspect exact overlay/generated settings diff; assemble/install only
new module debug target+test APKs, inspect merged badging no target permissions,
min26 and expected package, plus native libraries' x86_64 presence in APK. The
supplied run script is source-only. It does not provision or boot the emulator.

Direct `am instrument`, not connected test auto-uninstall; require exactly four
named non-skipped method statuses. Extract six exact cache PNGs using debug
run-as BEFORE uninstall. Script checks PNG header/dimensions/size, exports base64
with hashes to ordinary job logs (up to1MiB per PNG, no artifact upload), then
clears/uninstalls apps. Capture failure/partial evidence fails, not green.
No private device or account may ever be present on this emulator. Screenshots
in public repo job logs are nonsecret and available to repository readers.

After run: retrieve exact run logs through authorized read route; decode only six
marked PNG blocks; verify filename set/length/SHA256 and actual PNG decode. Open
and inspect all actual pixels before any UI completion claim. Record clipped or
missing controls as failure. UI status text is not independent storage proof.
If log download is blocked/truncated or any PNG cannot be decoded, visual work
remains unverified. Logs are not a substitute for inspecting images.

Compute cost: GitHub standard runners for public repos are free; avoid larger
runners and persistent caches/artifacts. Ordinary logs only, no artifact quota
assumption. User approval still required for publication and one UI execution.
No result is asserted here.
