#!/usr/bin/env python3
"""Negative tests for check-full-suite.py using synthetic result files. No Android needed."""
import importlib.util
import os
import sys
import tempfile
import time

here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("cfs", os.path.join(here, "check-full-suite.py"))
cfs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cfs)
EXPECTED = os.path.join(here, "expected-tests.txt")
NAMES = [l.strip() for l in open(EXPECTED) if l.strip()]
START = time.time() - 5


def xml(cases):
    body = ""
    for cls, name, extra in cases:
        body += '<testcase name="%s" classname="%s">%s</testcase>' % (name, cls, extra)
    return '<?xml version="1.0"?><testsuite name="s" tests="%d">%s</testsuite>' % (len(cases), body)


def cases(fail_known=True, **kw):
    out = []
    for n in NAMES:
        cls, name = n.rsplit(".", 1)
        extra = ""
        if n == cfs.KNOWN and fail_known:
            extra = '<failure type="java.lang.IllegalStateException" message="wrapped">caused by ValueRequiredException: IconID</failure>'
        out.append((cls, name, extra))
    return out


def run(cs, rc, old=False, nofile=False, expected=EXPECTED):
    with tempfile.TemporaryDirectory() as d:
        if not nofile:
            p = os.path.join(d, "TEST-a.xml")
            open(p, "w").write(xml(cs))
            if old:
                os.utime(p, (START - 3600, START - 3600))
        problems, _ = cfs.check(d, expected, rc, START)
        return not problems


bad = 0
def expect(ok, label, got):
    global bad
    if got != ok:
        print("FAIL case:", label, "expected", ok, "got", got); bad = 1

expect(True, "documented failure, rc 1", run(cases(), 1))
expect(True, "no failures, rc 0", run(cases(False), 0))
expect(False, "stale results", run(cases(), 1, old=True))
expect(False, "no result files", run(cases(), 1, nofile=True))
expect(False, "missing test", run(cases()[1:], 1))
c = cases(); expect(False, "extra test", run(c + [("x.Y", "z", "")], 1))
c = cases(); expect(False, "duplicated test", run(c + [c[0]], 1))
c = cases(); c[3] = (c[3][0], c[3][1], "<skipped/>"); expect(False, "skipped", run(c, 1))
c = cases(); c[3] = (c[3][0], c[3][1], '<error message="boom">t</error>'); expect(False, "error element", run(c, 1))
c = cases(); c[3] = (c[3][0], c[3][1], '<failure message="x">t</failure>'); expect(False, "second, unexpected failure", run(c, 1))
c = [(a, b, '<failure message="something else">t</failure>' if a + "." + b == cfs.KNOWN else e) for a, b, e in cases()]
expect(False, "documented test failing for another reason", run(c, 1))
c = [(a, b, '<failure type="java.lang.IllegalStateException" message="x">only IconID here</failure>' if a + "." + b == cfs.KNOWN else e) for a, b, e in cases()]
expect(False, "documented test, partial signature", run(c, 1))
c = cases(False)
for i in range(3, 13): c[i] = (c[i][0], c[i][1], "<skipped/>")
expect(False, "ten skipped tests", run(c, 0))
expect(False, "build failure, no failed test", run(cases(False), 1))
expect(False, "documented failure but rc 0", run(cases(), 0))
if bad:
    sys.exit(1)
print("check-full-suite tests OK")
