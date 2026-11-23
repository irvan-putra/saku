# Why saku is local-first - design rationale record

Date: 2026-11-23. Record of the design-rationale discussion about why saku
keeps its vault off GitHub, from the same conversation that produced
[tampung's backup-tiers note](https://github.com/irvan-putra/tampung/blob/main/research/2026-11-23-backup-tiers-discussion.md).

## The question

If a private GitHub repo works as cold storage for signing-key ciphertext,
why doesn't saku (itself a secrets tool) use one as its vault backend?

## The answer, as discussed

- Saku is local-first by design: a KeePassDX vault on Irvan's phone,
  encrypted sync over DeltaChat, and Instinct never holds the vault key.
  The 2026-11-18 architecture split (backups to the user's Google Drive,
  sync/chat over DeltaChat, public data on Bluesky) deliberately did not
  include GitHub.
- A private repo is not append-only: anyone holding a token can force-push
  or delete history, so it fails the ransomware-resistance bar.
- On a free personal account, branch protection / rulesets (the feature
  that would block force pushes) is available only for PUBLIC
  repositories; private repos need a paid plan. [S1]
- Confidentiality is the bigger point: putting the vault database in a
  GitHub repo means one leaked token hands over the whole ciphertext
  database at once. The signing-key backups repo gets away with being a
  repo because it is a cold copy of two small ciphertexts that nothing
  reads, not a live backend.

## Parked items from the 2026-11-22 secrets review

- SOPS + a private repo (Git-backed encrypted storage) is a legitimate
  pattern, but it solves a different job - ops secrets in repos - not
  saku's personal-vault job. Revisit if the app count keeps growing.
- OpenBao: deferred.
- Saku itself: parked.

## Sources

- [S1] GitHub REST API branch protection:
  https://docs.github.com/en/rest/branches/branch-protection
