# Saku web vault: synthetic tests

Fake data only. No real passwords, no network use at test time. This is a regression check for the development preview, not a security review or audit.

Run: `cd tests && npm ci && npm test` (Node 20+).

What it checks (`argon2-limits.mjs`): the page's real `LIMITS` and Argon2 hook from `site/vault.js` (the hostile-KDF guards) against synthetic KDBX 4 files built in memory. Over-cap memory, iterations and parallelism are refused before any allocation; memory between the soft (256 MiB) and hard (1 GiB) caps asks `confirm()` (stubbed to "no") and is refused when declined; a file just under the soft cap, and files exactly at the iteration/parallelism caps, unlock. Over-cap fixtures are sealed with a no-op key so the big allocation never actually runs. It also prints what the real KeePassXC fixture asks for, as context for the guessed cap values.

What it checks (`run.mjs`): the page's own `checkFileEnd()` from `site/vault.js` (the closing HMAC block of a KDBX 4 file, which kdbxweb 2.1.1 does not verify) against the fixtures and against tampered copies: a flipped bit in the closing HMAC, one appended byte, the closing block cut off, a mid-file flip, a wrong password.

Fixtures (`fixtures/`, all fake, passwords in `run.mjs`):
- `keepassxc-2.7.12-kdbx4-argon2d.kdbx`: made with the KeePassXC 2.7.12 GUI (KDBX 4, Argon2d, 64 MiB), one entry added with its CLI.
- `keepassxc-2.7.9-kdbx3.kdbx`: KeePassXC 2.7.9 CLI `db-create` (KDBX 3.1, AES-KDF). The end check is skipped for KDBX 3 by design.
- `demo.kdbx`: the page's demo vault (made with pykeepass).
- `page-saved-*.kdbx`: files saved by the page after an edit.

Known limits:
- Not covered: Argon2id, ChaCha20, attachments, TOTP, keyfiles, KDBX 4.1 features, phones, other browsers. `argon2-limits.mjs` checks the KDF-cap guards, not whether the guessed cap values (soft 256 MiB, hard 1 GiB, 100 iterations, parallelism 16) are the right ones.
- The Node test needs an XML DOM (`@xmldom/xmldom`, test-only, pinned at 0.8.12) because browsers have one built in. The page itself uses the browser's DOM.
- The KeePassXC cross-check was done by hand: download the official AppImage from the KeePassXC releases page, run `--appimage-extract`, then use `keepassxc-cli export` on files saved by the page. KeePassXC 2.8.0 needed a newer glibc than the test machine had. Do not commit the AppImage.
