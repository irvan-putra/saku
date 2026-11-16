# KeePassDX reuse: baseline qualification and integration boundaries

Research checked 2026-11-16. Source-only continuation of the earlier comparison.
No adoption, fork, license change, deployment, account access or real vault
migration. Local unchanged-source build/test preparation began after this source
review; no passing test/build result is asserted here. No custom cryptography.
A checklist is not executed test evidence.

## Recommendation
Keep whole-app KeePassDX reuse as the leading Saku direction, conditional on a
synthetic Android baseline and focused security review. Its app already owns
unlock, manual entry, search, autofill and file save/merge behavior that a KDBX
parser alone does not supply. Do not extract internal modules and call them a
supported vault SDK. Start by qualifying unchanged upstream, not by renaming it
and adding a transport in the same step.

No new product decision is needed for source research. After a synthetic
baseline, the meaningful choice is whether to accept an upstream-sized vault UI
and continuing fork maintenance for one app, or retain a separate upstream vault
with a transport companion. Rebuilding around a parser is the fallback if those
shapes do not fit, not the default merely because it is a smaller dependency.

## Revalidated baseline
Live upstream HEAD remains 77ecfa8654a5bd0d79d512f8fa71c0af8a29b554, version 4.5.8
(2026-10-31 commit). Four principal source areas: app, database, crypto, icon-pack.
The app's libre flavor is an appropriate baseline to inspect for an open fork;
this is not an adoption/distribution decision. Current build files use
compile/target 36, min 19, Java 17, AGP 8.13.2, Gradle 8.14.2, Kotlin 2.0.0 and native
NDK 25.2.9519653/CMake. Do not assume Saku's existing Gradle/NDK scaffold suffices.

The manifest has actual Android AutofillService guarded by
BIND_AUTOFILL_SERVICE, an input method guarded by BIND_INPUT_METHOD and a
credential provider guarded by BIND_CREDENTIAL_PROVIDER_SERVICE. The inspected app source
manifest does not request INTERNET. This is not proof about the eventual merged
manifest or packaged APK; inspect those in baseline validation. Adding in-process Delta Chat changes that
network/trust boundary; an offline vault plus external transport/provider
preserves it more closely but adds a second app/service and permission handoff.

## What can be reused, and the cost that remains
| Area | Existing source | Remaining Saku responsibility |
|---|---|---|
| Vault files | KDB/KDBX read/write, KDF/crypto and entry/group/history models | Exact format/version interoperability, damaged/malicious file handling and resource limits on actual Android |
| Unlock | Master credentials, keyfile/hardware paths, AndroidKeyStore wrapper | Current device-credential/biometric lifecycle review, reboot/enrollment invalidation/recovery; no audit transfer |
| Autofill | Real fill/save service plus origin parsing and entry selection | App/browser domain matching, unlock-cancel, wrong-origin negatives, manual selection, renamed package/OS registration |
| Save/merge | URI streams, decrypted merge, temp serialization file | Provider commit/atomicity, conflict/rollback, failed-write and crash recovery; merge is not inbound authority |
| DC sync | None in examined vault app | Authenticated counterpart authorization, queue/ack/replay/quota/provenance, locked-state behavior and safe import without full-vault disclosure |

Existing share/otpauth launch intents are not an authenticated assistant entry
insertion API. External text must not become an unlock or overwrite capability.
Whole-file sharing to let the assistant update the same database can require
full-vault key/read access; it is not equivalent to depositing only newly
created entries. Keep that authority boundary explicit. Do not solve it by
inventing signatures, a ratchet or another encryption envelope.

## Focused save-path negative-test targets
Source inspection independently reviewed, not reproduced on a provider/device.
Database.saveData serializes to a temporary file, then copies bytes into the
provider's output stream and removes the temp file. The URI helper opens the
destination using `wt` (with `rwt` retry on FileNotFoundException), and file URIs
use FileOutputStream. No destination-side temp-and-rename operation appears in
this path. Serialization staging is not atomic target replacement: if the
provider truncates on open, a later copy failure can leave a partial target.
Provider-specific commit/recovery behavior remains unverified.

A separate nullable-stream path (`databaseOutputStream.invoke()?.use`) skips
copying without an explicit exception in this method. The URI helper returns
null for a null URI, unsupported scheme, null file path or provider null result.
The app caller can then mark data up to date. ContextualDatabase clears its
nullable fileUri on close. This does not establish that a save races that clear,
or that a particular provider returns null in practice. It is a narrow
source-level false-up-to-date-state target, not a reproduced/common failure or
file-corruption claim. Nothing is opened by the null path, so that path itself
does not truncate the existing file. Unsaved edits could later be lost if the
false saved state is relied on. Inject null stream, mid-copy exception and
process death in synthetic tests before treating success as durable.

## Test evidence exists in source, but was not run here
Upstream includes native Android Argon2/AES tests, KDF clamping/serialization,
keyfile, hashed-block, model and utility tests. These are starting fixtures,
not proof that the exact Android integration is qualified. KdfClampingTest's
exception test uses JVM `assert`, so check assertions are enabled or strengthen
the oracle before relying on that specific test. Crypto and format tests do not
cover device autofill behavior or a new network importer.

Suggested bounded next execution, only after execution scope is confirmed:
1. Build pinned unchanged libre variant and run its existing unit/native tests.
   Record build inputs, exact APK identity and failures; no real vault.
2. Synthetic KDBX: create, lock/reopen, edit/save/reopen, wrong password,
   attachment/history, duplicate merge and deliberate interrupted-write cases.
   Cross-open a synthetic fixture with another implementation if available.
3. Android: no-network offline unlock, screen-lock/reboot lifecycle and autofill
   fill/save against synthetic native/browser forms, including wrong-origin and
   cancel paths. Inspect actual screens. No password/account migration.
4. Only then choose the smallest transport extension boundary and review its
   authority/key custody separately. A passing unchanged vault is not proof of
   the extension's security. Keep inbound data queued while locked, not master
   keys silently retained to make background sync convenient.

## Licensing and branding inventory, not legal clearance
Core source headers are GPLv3-or-later. README says forks are possible; it does
not establish permission to present a derivative as official KeePassDX. Preserve
copyright, license and modification notices; use Saku's separate application ID,
name/icon and signing identity if a fork is chosen. Changing applicationId alone
does not update the many hardcoded class/package references and XML settings
activities; identity migration needs its own check, not mass text replacement.
No separate affirmative trademark/brand grant was established in inspected
README/license locations. Do not imply upstream endorsement.

The inventory is broader than GPL alone: BouncyCastle's permissive notice,
Apache and Mozilla components, fonts and icon packs, native Argon2 (CC0 OR
Apache2 header), and Brian Gladman's native AES notice (including attribution
and nonendorsement conditions). Classic icons have mixed notices; do not replace
all licensing with one AGPL file. Strong copyleft preference permits considering
GPL/AGPL combinations but does not relicense others' files. A release-specific
SBOM/notices/source-offer and compatibility check remains before distribution.

## Assurance limits
No matching independent professional audit of this exact version was established.
Community issue #2480, titled "[Security Audit] Security vulnerabilities and
improvements report", and maintainer fixes do not establish such an audit. Current local
unlock wrapper still has the earlier documented AES-CBC migration review item;
this is distinct from the KDBX file format/cipher. No vulnerability exploitation,
performance claim or runtime qualification is asserted by this source inspection.

## Sources
- https://github.com/Kunzisoft/KeePassDX/commit/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/README.md
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/LICENSE
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/build.gradle.kts
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/gradle/libs.versions.toml
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/AndroidManifest.xml
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/java/com/kunzisoft/keepass/credentialprovider/autofill/KeeAutofillService.kt
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/java/com/kunzisoft/keepass/credentialprovider/activity/EntrySelectionLauncherActivity.kt
- https://github.com/Kunzisoft/KeePassDX/wiki/File-Manager-and-Sync
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/database/src/main/java/com/kunzisoft/keepass/database/element/Database.kt
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/java/com/kunzisoft/keepass/database/action/SaveDatabaseRunnable.kt
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/java/com/kunzisoft/keepass/database/action/MergeDatabaseRunnable.kt
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/crypto/src/androidTest/java/com/kunzisoft/encrypt/Argon2Test.kt
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/database/src/test/java/com/kunzisoft/keepass/tests/crypto/KdfClampingTest.kt
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/crypto/src/main/jni/argon2/include/argon2.h
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/crypto/src/main/jni/aes/aes/aeskey.c
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/LICENSES/LICENSE_ICON_PACK_CLASSIC
- https://github.com/Kunzisoft/KeePassDX/issues/2480
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/database/src/main/java/com/kunzisoft/keepass/utils/UriHelper.kt
- https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/java/com/kunzisoft/keepass/database/ContextualDatabase.kt
