#!/usr/bin/env python3
"""Host check of the two public synthetic KDBX fixtures with pykeepass.

Synthetic data only. Does not select a storage library and says nothing about
Android, memory bounds, or KeePassDX-written files.
"""
import hashlib, pathlib, sys
from importlib.metadata import version
from pykeepass import PyKeePass
from pykeepass.exceptions import CredentialsError

REQUIRED = "4.2.0"  # install exactly: pip install pykeepass==4.2.0

ROOT = pathlib.Path(__file__).resolve().parent.parent / "spikes/storage3/src/test/resources"
FIXTURES = {
    "saku-synthetic-2.2.6.kdbx": "a83c78cfae18dca02cb515231ff5ccd9ab0b91eb264d8bf4f139bc32290d839f",
    "saku-pykeepass-roundtrip.kdbx": "a19b0f565ba071def741826faf6c15ceb2b53cfc3d18c884363d9f652b13f712",
}
EXPECT = {
    "saku-synthetic-2.2.6.kdbx": {"example.test": "SYNTHETIC-spike"},
    "saku-pykeepass-roundtrip.kdbx": {"example.test": "SYNTHETIC-spike",
                                      "python-roundtrip.test": "SYNTHETIC-from-python"},
}

def main():
    if version("pykeepass") != REQUIRED:
        sys.exit(f"pykeepass {REQUIRED} required, found {version('pykeepass')}")
    for name, digest in FIXTURES.items():
        path = ROOT / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            sys.exit(f"hash mismatch: {name}")
        kp = PyKeePass(str(path), password="SYNTHETIC-master")
        found = {e.url or e.title: e.password for e in kp.entries}
        for key, pw in EXPECT[name].items():
            hits = [v for k, v in found.items() if k and key in k]
            if pw not in hits:
                sys.exit(f"{name}: expected entry for {key} missing")
        try:
            PyKeePass(str(path), password="SYNTHETIC-wrong")
        except CredentialsError:
            pass
        else:
            sys.exit(f"{name}: wrong password accepted")
        print("ok", name)

if __name__ == "__main__":
    main()
