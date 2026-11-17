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
