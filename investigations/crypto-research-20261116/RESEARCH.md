# Cryptography research checkpoint - 2026-11-16

Research, not an implementation choice, audit or usable password vault.
No dependency selected, license changed, server deployed or real secret used.

## What was written, and what is published
The first private design combined a Noise implementation with custom key
derivation, hash ratcheting and envelopes. Review found it critically broken,
including public handshake material used as a secret. It was withdrawn before
executable publication. An anonymous-sealed-box follow-up proposal was also
withdrawn: recipient encryption alone does not authenticate a sender.
These are rejected paths, not prototypes to repair into production.

Published source inspected at be60093ea01f37b1471aeb23d7590551b5b4e532:
- Main app is fixed synthetic UI with in-memory MockVault; real store throws.
- Main app has UI runtime dependencies; KeePassJava2 is a test dependency.
- A separate Android packaging probe links KeePassJava2 3.1.0 and calls its API;
  compilation is not Android execution or security proof.
- Checkpoint9 is custom SQLite state/approval/deduplication research. It uses
  standard SHA-256 for synthetic equality checks, not encryption or identity.
  It is plaintext and has documented races/limits. Not an operational gate.
- No working vault encryption, real synchronization or trusted-owner adapter.

## Bitwarden: actual product and implementation are different evidence
Official product docs describe AES256-CBC-HMAC-SHA256, PBKDF2-SHA256 or Argon2id
for password-derived master keys, and HKDF for non-password key derivation.
Android source f14a6e399e784073deb60c10f8cad0c24ea86be4 pins
com.bitwarden:sdk-android:4.0.0-9737-d9119f7a. Matching SDK source contains
existing aes/cbc/hmac/sha2/argon2/pbkdf2/hkdf dependencies, plus newer suites.
A dependency list is NOT proof every deployed vault uses every suite.
Android platform protection also uses Keystore/JCA AES-GCM and AndroidX
encrypted preferences. That layer is not the vault's interchange format.

The password-manager SDK explicitly warns it is unsupported for public use,
with unstable interfaces. bitwarden-crypto is GPL-3.0-only OR a separate
Bitwarden SDK license, and says internal/do not use. Welcoming copyleft does
not remove API/support risks. Do not recreate its primitive composition here.
Listed Bitwarden audits do not establish every current dependency/version or
any reuse in this project is audited.

## Candidate layers, not interchangeable replacements
| Candidate | Intended fit | Limit that matters |
|---|---|---|
| Existing Android vault reuse, e.g. KeePassDX | Local vault, unlock, editing, autofill together | App/fork/module-maintenance scope, not a stable public SDK; review/interop/integration still needed |
| KDBX library | Existing encrypted vault format, offline file interoperability | Implementation assurance is separate from format; key/unlock/lifecycle still our integration burden |
| age reference | File encryption, exports/backups or whole snapshots | Not vault UX, sender authentication, replay/conflict handling or recovery |
| kage | Kotlin/Android implementation of age | Separate implementation, no verified matching audit; age1.2 API coverage vs reference1.3 features |
| Bitwarden PM SDK | Existing Bitwarden product's shared business/crypto logic | Explicitly unsupported external API; licensing and maintenance constraints |
| OpenBao | Server secrets, policies, audit, key service | Not an embedded offline Android vault; server participates in plaintext operations |

KeePassJava2 2.2.6 failed a simple reverse-interoperability test after pykeepass
added an entry (missing required IconID). 3.1.0 narrow host interop/negative tests and Android
packaging passed; Android execution, broad parser/resource limits and review
scope remain unknown. Kotpass is another MIT KDBX library but bundles crypto
engine code; Kotlin alone is not an assurance advantage.

age reference is a Go library/format (BSD3). kage says API26+/Java17 and
MIT OR Apache2 licensing. No independent audit of the exact kage version was
established. No statement here says reference age itself was independently
audited: review scope must be verified, not inherited from a format name.

OpenBao is MPL2 server software; API/SDK modules are supported imports but
server internals are explicitly not a supported library. Transit handles
plaintext operations via an authenticated API without storing input payloads.
This is not phone-only custody or a zero-knowledge server. Standard decrypt
requires a reachable/unsealed service. Offline caching/envelope encryption
would add a new key-custody design, not solve it automatically. Unseal/recovery,
auth policies, TLS, patching, backup and audit sinks become operational work.
No server deployment is selected or approved by this comparison.

## License posture
Strong copyleft is a valid preference, not an exclusion. GNU's current AGPL
text is version3 (2007); distinguish -only and -or-later. Original dependency
licenses/notices remain. A GPL/AGPL combination has specific conditions, not
permission to relabel all third-party code. No project license change here.

## Working recommendation and next check
Reuse a complete high-level vault implementation rather than assemble
cryptographic primitives or invent a channel. Compare whole Android vault
reuse against KDBX-library integration before committing to more key/unlock
code. age remains a separate file/backup candidate; OpenBao optional/server.
No verified-ready winner yet. Whole-vault reuse comparison is in progress.
