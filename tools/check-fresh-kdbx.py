#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Host check of a FRESH KDBX4 file written by KeePassDX code (synthetic data).

Closes the recorded gap that no file written by KeePassDX's own code had been
read by a second implementation: the emulator job exports a vault file written
by pinned KeePassDX code (Argon2id), and this script reads it with pykeepass.
Old-fixture checks live in check-kdbx-fixtures.py; this script pins no file
hash because the fixture is written fresh each run.

Synthetic data only. Says nothing about Android UI, memory bounds on device,
or files written by other versions.
"""
import sys
from importlib.metadata import version
from pykeepass import PyKeePass
from pykeepass.exceptions import CredentialsError

REQUIRED = "4.2.0"  # install exactly: pip install pykeepass==4.2.0

EXPECT = {"example.test": "SYNTHETIC-spike"}


def main():
    if version("pykeepass") != REQUIRED:
        sys.exit(f"pykeepass {REQUIRED} required, found {version('pykeepass')}")
    path = sys.argv[1] if len(sys.argv) > 1 else "saku-fresh-argon2id.kdbx"
    kp = PyKeePass(path, password="SYNTHETIC-master")
    algo = kp.kdf_algorithm
    # pykeepass 4.2.0 returns one of 'aeskdf', 'argon2', 'argon2id'.
    if algo != "argon2id":
        sys.exit(f"expected argon2id KDF in the KeePassDX-written file, found {algo!r}")
    found = {e.url or e.title: e.password for e in kp.entries}
    for key, pw in EXPECT.items():
        hits = [v for k, v in found.items() if k and key in k]
        if pw not in hits:
            sys.exit(f"expected synthetic entry for {key} missing")
    try:
        PyKeePass(path, password="SYNTHETIC-wrong")
    except CredentialsError:
        pass
    else:
        sys.exit("wrong password accepted")
    print("ok", path, "kdf=argon2id", "entries=" + str(len(kp.entries)))


if __name__ == "__main__":
    main()
