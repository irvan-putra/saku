#!/usr/bin/env python3
"""Strict result check for a full `:app:testDebugUnitTest --continue` run.

Usage: check-full-suite.py RESULTS_DIR EXPECTED_TESTS_FILE GRADLE_EXIT_CODE START_EPOCH [SUMMARY_FILE]

Passes only when ALL of these hold:
  * the result files were written after START_EPOCH (no stale results);
  * the executed test set equals EXPECTED_TESTS_FILE exactly (nothing missing, extra or duplicated);
  * no test is skipped and none has an <error>;
  * the only failure is the documented one (KNOWN) and its failure text contains the documented
    signature (an IllegalStateException wrapping ValueRequiredException for the missing IconID,
    from KeePassJava2 2.2.6 reading a file written by another tool);
  * the Gradle exit code agrees: 1 when the documented failure is present, 0 when there is none.
Anything else exits non-zero. A build or compile failure (non-zero exit with no failed test)
also fails.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

KNOWN = "com.vanipy.saku.KdbxSpikeTest.readsPykeepassRoundtrip"
SIGNATURE = ("IllegalStateException", "ValueRequiredException", "IconID")


def check(results_dir, expected_file, rc, start, summary=None):
    problems = []
    expected = [l.strip() for l in open(expected_file) if l.strip()]
    if len(set(expected)) != len(expected):
        problems.append("expected list has duplicates")
    files = sorted(glob.glob(os.path.join(results_dir, "*.xml")))
    if not files:
        return ["no result files: the suite did not run"], []
    seen, failures = [], {}
    for f in files:
        if os.path.getmtime(f) < start:
            problems.append("stale result file (older than this run): " + os.path.basename(f))
            continue
        root = ET.parse(f).getroot()
        for case in root.iter("testcase"):
            name = case.get("classname", "") + "." + case.get("name", "")
            seen.append(name)
            if case.find("skipped") is not None:
                problems.append("skipped: " + name)
            if case.find("error") is not None:
                problems.append("error: " + name)
            fail = case.find("failure")
            if fail is not None:
                failures[name] = (fail.get("type") or "") + " " + (fail.get("message") or "") + " " + (fail.text or "")
    if sorted(seen) != sorted(expected):
        missing = sorted(set(expected) - set(seen))
        extra = sorted(set(seen) - set(expected))
        dup = sorted({n for n in seen if seen.count(n) > 1})
        problems.append("executed set differs: missing=%s extra=%s duplicated=%s" % (missing, extra, dup))
    for name, text in failures.items():
        if name != KNOWN:
            problems.append("unexpected failure: " + name)
        elif not all(t in text for t in SIGNATURE):
            problems.append("failure of the documented test has the wrong signature (needs %s)" % ", ".join(SIGNATURE))
    want_rc = 1 if KNOWN in failures else 0
    if rc != want_rc:
        problems.append("gradle exit code %d, expected %d for this result set" % (rc, want_rc))
    lines = ["full suite: %d tests executed, %d failed" % (len(seen), len(failures))]
    lines += ["failed: %s (%s)" % (n, "documented" if n == KNOWN else "UNEXPECTED") for n in sorted(failures)]
    return problems, lines


def main():
    d, exp, rc, start = sys.argv[1], sys.argv[2], int(sys.argv[3]), float(sys.argv[4])
    problems, lines = check(d, exp, rc, start)
    out = lines + ["CHECK FAIL: " + p for p in problems] + ([] if problems else ["CHECK OK"])
    print("\n".join(out))
    if len(sys.argv) > 5:
        open(sys.argv[5], "a").write("\n".join(out) + "\n")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
