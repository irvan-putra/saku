# Storage checkpoint 6 - narrow host tests and Android packaging probe

Source/testing artifact only; license choice pending. No APK included.
No real credentials, no network, no Android execution and no chosen vault.

## Host: KeePassJava2.kdbx.database:3.1.0
Standalone Gradle/JDK17 project; `gradle test`, heap capped at 256 MiB.
Three tests pass in about 3 seconds on this host:
- Reads 2.2.6 and pykeepass-edited fixtures, checks both expected fields, saves.
- Rejects one wrong key, eight truncations and five single-bit mutations.
- Rejects 100 seeded short malformed byte strings.
The saved file also opens in pykeepass with both expected values intact.
Fixtures use SYNTHETIC-master, not a real password.

These are small regression samples, not a fuzz campaign, audit, proof of
resource bounds or general interoperability. No hostile KDF cost/header
allocation limits verified. No A24 Argon2 performance measured.

## Android: compilation/DEX/packaging only
Separate namespace dev.saku.storageprobe, no activities or network permissions.
Calls the actual 3.1.0 KdbxDatabase/KdbxCredentials API in a compiled class.
AGP8.1.4/JDK17/Gradle8.2.1, compile34/target34/min24: assembleDebug PASSES.
Dependency-resource collisions required excluding the duplicate OSGI manifest
and merging duplicate LICENSE.md/NOTICE.md files (not deleting license text).
No production namespace/release implication. No APK delivery.

This establishes only compilation/DEX/packaging. The class was not run on
Android. Provider behavior, class loading, Android APIs, memory use, persistence
and interoperability still need device tests. No runtime support claim.

## Existing failure preserved
This spike does not change the retained 2.2.6 missing-IconID reverse-interop
failure in checkpoint5. No dependency is selected as Saku's vault yet.
No DIY crypto fallback.
