#!/usr/bin/env python3
"""Checks the permission surface of a built APK from `aapt2 dump xmltree --file AndroidManifest.xml`.

Usage: check-manifest-permissions.py MANIFEST_TREE_TXT ALLOWED_NAME

Passes only when:
  * the manifest declares exactly one <permission>, named ALLOWED_NAME, with protectionLevel
    exactly 0x2 (signature; not signature|privileged or any other flag combination);
  * the manifest requests exactly one permission via <uses-permission> (or a -sdk-23 / -sdk-m
    variant), and it is ALLOWED_NAME;
  * there is no <permission-group>, <permission-tree> or any other permission element.
The tree must contain a <manifest> element, so empty or truncated input fails closed.
"""
import re
import sys

ELEM = re.compile(r"^(\s*)E: (\S+)")
ATTR = re.compile(r"^\s*A: (?:\S*:)?(name|protectionLevel)\(0x[0-9a-fA-F]+\)=(.*)$")
QUOTED = re.compile(r'"([^"]*)"')
HEX = re.compile(r"0x([0-9a-fA-F]+)\s*$")


def parse(text):
    elems = []  # (tag, attrs)
    for line in text.splitlines():
        m = ELEM.match(line)
        if m:
            elems.append((m.group(2), {}))
            continue
        a = ATTR.match(line)
        if a and elems:
            elems[-1][1][a.group(1)] = a.group(2)
    return elems


def check(text, allowed):
    problems = []
    elems = parse(text)
    if not any(t == "manifest" for t, _ in elems):
        return ["no <manifest> element in the input"]
    declared, requested = [], []
    for tag, attrs in elems:
        if tag == "permission":
            q = QUOTED.search(attrs.get("name", ""))
            lvl = attrs.get("protectionLevel")
            h = HEX.search(lvl) if lvl is not None else None
            level = int(h.group(1), 16) if h else (2 if lvl is not None and '"signature"' in lvl else None)
            declared.append((q.group(1) if q else None, level))
        elif tag.startswith("uses-permission"):
            q = QUOTED.search(attrs.get("name", ""))
            requested.append(q.group(1) if q else None)
        elif tag.startswith("permission-") or tag.startswith("uses-permission-"):
            problems.append("unexpected permission element: " + tag)
    if [n for n, _ in declared] != [allowed]:
        problems.append("declared permissions are %s, expected exactly [%r]" % ([n for n, _ in declared], allowed))
    for n, lvl in declared:
        if lvl != 2:
            problems.append("declared permission %r protectionLevel is %r, expected 0x2 (signature)" % (n, lvl))
    if requested != [allowed]:
        problems.append("requested permissions are %s, expected exactly [%r]" % (requested, allowed))
    return problems


def main():
    problems = check(open(sys.argv[1]).read(), sys.argv[2])
    print("\n".join("CHECK FAIL: " + p for p in problems) if problems else "PERMISSIONS OK")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
