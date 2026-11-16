# Whole Android vault reuse versus KDBX library - bounded comparison

2026-11-16. Read-only research. No fork, code change, license change, build,
workflow, deployment, real vault or implementation selection.

## Practical recommendation
Prefer a narrowly maintained fork/reuse of an existing Android vault over
building an entire password manager around a file-format library, IF the goal
remains one Saku app with offline unlock, manual entries and autofill. KeePassDX
is the concrete candidate inspected, not a claim of audited readiness.
Do a baseline synthetic Android runtime/review check before deciding to fork.
Do not import its internals under the assumption they are a stable public SDK.

This reduces the amount of new key/unlock/autofill code; it does not make
Delta Chat integration free or make every upstream security choice acceptable.
If upstream-sized UI and ongoing fork maintenance are unacceptable, the smaller
KDBX-library route is possible, but leaves more security-sensitive app behavior
to build. That product/maintenance tradeoff is the eventual owner decision,
not a request to choose a cipher or trust an unverified library today.

## Actual inspected architecture
KeePassDX revision 77ecfa8654a5bd0d79d512f8fa71c0af8a29b554 (4.5.8 source):
app, database, crypto and icon-pack modules. database and crypto are Android
library modules (min19, compile36, Java17); crypto uses NDK/CMake plus Bouncy
Castle. They are repository modules, not demonstrated stable published SDKs.
DatabaseTaskProvider binds the app's database task service for entry/load/save/
merge actions; this is app-internal integration, not a remote plugin contract.

Existing app has manual vault editing, KDBX handling, Android AutofillService,
credential-provider service and biometric/device unlock code. A library-only
Saku instead has to own those UI/service/unlock/recovery flows itself. Existing
KeePassJava2 tests do not exercise those Android flows at all.

KeePassDX uses Storage Access Framework URI streams. It deliberately separates
retrieving/sending files (external provider) from decrypting/merging data (vault
app). It documents merge/save/reload behavior and warns provider caching,
write permissions and missed change events can affect safety. Do not treat
"reload" as conflict-safe merge: reload discards currently open changes.

## Delta Chat boundary without new cryptography
Two distinct shapes, neither implemented:
- Companion/provider: keep upstream vault app offline and let a separate
  transport/provider exchange encrypted files via SAF. This preserves its
  intended architecture and avoids a large UI fork, but adds a second app and
  requires correct atomic provider writes, conflict handling and permission
  lifecycle. Sharing the SAME full vault for the assistant to update would
  require vault-key/read access beyond newly created entries; do not assume it.
- Narrow fork: retain upstream vault/crypto/unlock/autofill and add a bounded
  authenticated inbound-entry import while the vault is unlocked. Keep the
  transport boundary separate; use mature transport authentication, not a
  new signature/ratchet/envelope-encryption design. Automatic receive while
  locked must queue encrypted transport data, not retain an unlocked vault or
  silently retain its master key. Key custody, queue retention, replay and
  imported-entry provenance still require review.

The one-way new-entry goal does not automatically fit whole-database file
replication. Do not grant the assistant the master vault key or overwrite
manual entries to make transport convenient. An existing database merge is
not proof it satisfies that narrower authority boundary.

## Security evidence and limitations
No matching independent professional audit of this exact KeePassDX revision
was established. GitHub issue2480 is a contributor's self-described review;
maintainer responses and follow-up fixes are evidence, not an independent audit
certificate or validation of every allegation. A device-credential key-binding
fix is visible in current source (requires authentication for biometric OR
device-credential mode). Current DeviceUnlockManager still uses AES-CBC for
its local unlock wrapper; issue discussion says GCM migration was delayed due
to existing-key compatibility. That is not the KDBX cipher and must not be
confused with vault-file authentication. This comparison does not pronounce
it exploitable or acceptable; it is a concrete focused review item.

Production gates include upstream security issue resolution/version pin,
credential/memory lifecycle, malformed/KDF resource bounds, save/merge crash
safety, restore/backup behavior and real autofill/device tests. Fork additions
need their own review; upstream assurance never covers new integration.

## License inventory, not completed legal clearance
Core file headers/README specify GPLv3-or-later; preserve upstream copyrights,
license and modified-file notices. Supporting sources/licenses include Bouncy
Castle's permissive notice, Apache components, Mozilla components, native
Argon2/AES sources and separate font/icon licenses. Review those actual files
and transitive dependencies before distribution; this is not a complete SBOM.
Adding AGPL-covered Saku code does not relicense upstream GPL files. GPLv3 and
AGPLv3 combination conditions need a deliberate package/notice plan.

## Sources
https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/README.md
https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/database/build.gradle.kts
https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/crypto/build.gradle.kts
https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/java/com/kunzisoft/keepass/database/DatabaseTaskProvider.kt
https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/java/com/kunzisoft/keepass/biometric/DeviceUnlockManager.kt
https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/src/main/AndroidManifest.xml
https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/gradle/libs.versions.toml
https://github.com/Kunzisoft/KeePassDX/wiki/File-Manager-and-Sync
https://github.com/Kunzisoft/KeePassDX/wiki/AutoFill
https://github.com/Kunzisoft/KeePassDX/issues/2480
https://github.com/Kunzisoft/KeePassDX/blob/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/LICENSES/LICENSE_BOUNCY_CASTLE
