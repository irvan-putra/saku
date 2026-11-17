# Storage evaluation - implementation not selected

KDBX is the intended interchange-format candidate. Format reputation does not
establish this implementation's safety or Android compatibility.

## 2.2.6 simple candidate - host JVM only
Initial save failed on JDK17 reflection into java.util.UUID. A host-only
add-opens diagnostic workaround permits own roundtrip and wrong-password tests.
Fifty short malformed headers reject quickly, not broad fuzz coverage.
Output is KDBX4.0/AES256/Argon2 per pykeepass inspection.
2.2.6 -> pykeepass field check PASSES.
pykeepass adds entry and saves -> 2.2.6 read FAILS (required IconID absent).
The failing test and synthetic file remain. This is not a selected store.

## 3.1.0 actual module/API spike - host JVM only
Artifact KeePassJava2.kdbx.database:3.1.0, Jackson-based KdbxDatabase,
org.linguafranca.pwdb.format.KdbxCredentials. Not simple:3.1.0 (does not exist).
Standalone JVM test reads the original and pykeepass-modified fixtures, checks
both synthetic entries and saves. pykeepass reads that save and verifies both
fields. These narrow tests PASS without the 2.2.6 reflection workaround.
No finding establishes Android compatibility, memory bounds, audit status,
or real-device Argon2 performance. No general interop/quality endorsement.

## Still required
Cross-check actual KeePass/KeePassDX; optional/malformed XML and header cases;
KDF parameter bounds and hostile allocation limits; wrong-key/tamper/truncate
cases; save durability and crash recovery; Android package compatibility and
real A24 memory/latency. No DIY crypto fallback if either candidate fails.

Sources:
https://repo1.maven.org/maven2/org/linguafranca/pwdb/KeePassJava2/maven-metadata.xml
https://repo1.maven.org/maven2/org/linguafranca/pwdb/KeePassJava2-simple/maven-metadata.xml
https://repo1.maven.org/maven2/org/linguafranca/pwdb/KeePassJava2.kdbx.database/3.1.0/KeePassJava2.kdbx.database-3.1.0-sources.jar
https://github.com/jorabin/KeePassJava2

## Checkpoint 6: KeePassJava2 3.1.0 host and Android packaging probe

A separate, disposable source probe adds three narrow host tests for 3.1.0: reading two synthetic interoperability fixtures and saving; rejecting one wrong key, eight truncations and five bit mutations; and rejecting 100 seeded short malformed inputs. All three pass on the host. The saved file was also read by pykeepass with both expected synthetic fields.

A separate Android project in `spikes/storage3-checkpoint6/android` compiles, DEXes, and assembles a minimal class that calls the 3.1.0 API. It uses only the disposable namespace `com.vanipy.saku.storageprobe`, declares no network permission, and is not production code. Duplicate `LICENSE.md`/`NOTICE.md` resources are merged and only the duplicate OSGI manifest is excluded. **The class was not run on Android.** This does not establish class loading, provider behavior, Android API support, memory bounds, persistence, or interoperability on a device.

This checkpoint does not select a storage library, establish an audit/security endorsement, or change the retained 2.2.6 missing-`IconID` reverse-interop failure. No APK is included.

## Checkpoint 7: KeePassDX as the next qualification path (not a selection)

This research qualification path examines KeePassDX at pinned revision 77ecfa8654a5bd0d79d512f8fa71c0af8a29b554. Its own database and crypto modules are the next path to qualify. No library is selected, the KeePassJava2 candidates and their files stay as recorded above, and no license is chosen.

Evidence on file (public CI, synthetic data only):
- Host baseline: https://github.com/irvan-putra/saku/actions/runs/43881775978 (success). Runs the upstream `:database` and `:app` unit tests and compiles Kotlin.
- Seven synthetic storage tests (private-file close/reopen, logical restart and injected outputs only) on an x86_64 API 34 emulator, run 43891302886 on 1cbc580 (success; the earlier run on fab9d43 failed): https://github.com/irvan-putra/saku/actions/runs/43891302886
- Synthetic UI wiring and pixels passed on c4bb3aa. The earlier runs on fed3b86 and 680765b failed.

Added in this checkpoint:
- `tools/check-kdbx-fixtures.py`: pykeepass 4.2.0 (the script refuses any other version; install `pykeepass==4.2.0`) reads the two older public synthetic fixtures (hash-pinned), checks the expected fields, and rejects a wrong password (it catches only `CredentialsError`). Host only. It is an old-fixture read and wrong-key check. It is not interoperability evidence for files written by KeePassDX code.
- The baseline workflow summary step now also requires the upstream `KdfClampingTest` result to exist, with exactly its four named tests once each, the expected class, zero failures/errors/skips in suite counters, and no failure/error/skipped child evidence. The existing host command already runs `:database:testDebugUnitTest` unfiltered; the summary only checks that evidence. Raw run logs were not independently read.

Known limits to keep in release copy:
- The seven storage tests do not cover process kill, power loss, fsync/durable rename, SAF, hostile KDF input, a second reader, or A24 behavior.
- Native Argon2 execution is not inferred from those generic round trips: a fresh database defaults to AES-KDF. The two older fixtures use Argon2 but were written by KeePassJava2 and pykeepass, not by KeePassDX code. Check the KDF of any KeePassDX-written fixture before claiming Argon2 coverage.
- KDF memory budget (source concern, not an exploit proof). At the pin, raw KDF parameters are validated against an intrinsic range and rejected when outside it, but that range is not a phone budget (up to UInt.MAX_VALUE bytes). A caller-supplied `Limits` check exists (`Argon2Kdf.checkLimits`, app limits via `AppUtil.getLimits`). A static trace found it applied to the already-loaded nullable engine in `Database.loadData`, before the fresh `DatabaseKDBX` is created. For a freshly parsed header the path runs `openDatabase`, key assignment, `makeFinalKey`, then the transform, with intrinsic raw-parameter validation but no caller `Limits`. No budget check on the fresh parsed engine was found. A device callback existing does not prove enforcement.
- Open test gap: a bounded test that a synthetic header asking for more than a chosen budget is rejected before the native transform. Design it so it never allocates the large amount (check the parameters before the transform). The synthetic overlay's 128000000-byte ceiling is a test value, not an endorsed A24 budget. The phone budget policy is unmeasured and undecided.
- No file written by KeePassDX's own code has been read by a second implementation yet.
- Emulator runs show function only. A24 memory and unlock latency are unmeasured.
- No independent audit of this version was found; see docs/research/keepassdx-baseline-qualification.md.
- The KeePassJava2 3.1.0 class has never executed on Android.
- Sync protocol redesign and autofill are not covered here.
