#!/usr/bin/env python3
"""Synthetic tests for check-manifest-permissions.py (aapt2 xmltree text shapes). No Android needed."""
import importlib.util
import os
import sys

here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("cmp", os.path.join(here, "check-manifest-permissions.py"))
cmp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cmp)
P = "com.vanipy.saku.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"


def decl(name=P, level="(type 0x11)0x2", style=0):
    pre = "android:" if style == 0 else "http://schemas.android.com/apk/res/android:"
    out = '    E: permission (line=7)\n      A: %sname(0x01010003)="%s" (Raw: "%s")\n' % (pre, name, name)
    if level is not None:
        out += "      A: %sprotectionLevel(0x01010009)=%s\n" % (pre, level)
    return out


def uses(name=P, tag="uses-permission", style=0):
    pre = "android:" if style == 0 else "http://schemas.android.com/apk/res/android:"
    return '    E: %s (line=11)\n      A: %sname(0x01010003)="%s" (Raw: "%s")\n' % (tag, pre, name, name)


def tree(*parts):
    return "N: android=http://schemas.android.com/apk/res/android\n  E: manifest (line=2)\n    A: package=\"com.vanipy.saku\"\n" + "".join(parts)


bad = 0
def expect(ok, label, text):
    global bad
    got = not cmp.check(text, P)
    if got != ok:
        print("FAIL case:", label, "expected", ok, "got", got); bad = 1

expect(True, "good, short attribute names", tree(decl(), uses()))
expect(True, "good, namespaced attribute names", tree(decl(style=1), uses(style=1)))
expect(True, "good, uses before declaration", tree(uses(), decl()))
expect(False, "empty input", "")
expect(False, "no manifest element", "N: x\n")
expect(False, "no declaration", tree(uses()))
expect(False, "no uses-permission", tree(decl()))
expect(False, "protectionLevel normal (0x0)", tree(decl(level="(type 0x11)0x0"), uses()))
expect(False, "protectionLevel signature|privileged (0x12)", tree(decl(level="(type 0x11)0x12"), uses()))
expect(False, "protectionLevel dangerous (0x1)", tree(decl(level="(type 0x11)0x1"), uses()))
expect(False, "protectionLevel missing", tree(decl(level=None), uses()))
expect(False, "declaration with another name", tree(decl(name="com.other.PERM"), uses()))
expect(False, "second declaration", tree(decl(), decl(name="com.vanipy.saku.OTHER"), uses()))
expect(False, "extra uses-permission INTERNET", tree(decl(), uses(), uses("android.permission.INTERNET")))
expect(False, "uses-permission-sdk-23 extra", tree(decl(), uses(), uses("android.permission.CAMERA", "uses-permission-sdk-23")))
expect(False, "permission-group element", tree(decl(), uses(), "    E: permission-group (line=3)\n"))
expect(False, "permission-tree element", tree(decl(), uses(), "    E: permission-tree (line=3)\n"))
expect(False, "uses a different single permission", tree(decl(), uses("android.permission.INTERNET")))
if bad:
    sys.exit(1)
print("check-manifest-permissions tests OK")
