"""Break the service on purpose and require the test suite to notice.

Runs the suite once clean, then once per fault. A fault counts as caught only
when tests fail on assertions (or time out waiting for something that never
happens). Any other error, like a container that would not start, stops the
drill: a red build for the wrong reason proves nothing.

    python3 scripts/fault_drills.py                 # clean run, then every fault
    python3 scripts/fault_drills.py seat_race       # clean run, then one fault
"""

import glob
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

FAULTS = {
    "ghost_success": "the API answers 201, then the transaction rolls back and nothing is stored",
    "lost_event": "the booking is stored but its outbox row is not, so no event ever leaves",
    "double_submit": "the idempotency key is ignored, so a retry is told its own seat is taken",
    "seat_race": "the seat is checked, paid for, then written, so two fans both get A1",
    "double_ticket": "the desk checks for the event before printing and records it after, so two consumers both print",
}

# A wait that timed out is a failed expectation, not a broken run.
TIMEOUT = "org.awaitility.core.ConditionTimeoutException"
SHOWN = 3

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORTS = os.path.join(ROOT, "target", "surefire-reports")
MVN = "mvnw" if os.path.exists(os.path.join(ROOT, "mvnw")) else "mvn"


def run_suite(fault):
    shutil.rmtree(REPORTS, ignore_errors=True)
    cmd = [MVN, "-B", "-q", "test", "-Ddrills.fault=" + fault]
    subprocess.run(cmd, cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    caught, broken, total = [], [], 0
    for path in sorted(glob.glob(os.path.join(REPORTS, "TEST-*.xml"))):
        for case in ET.parse(path).getroot().iter("testcase"):
            total += 1
            name = case.get("classname").rsplit(".", 1)[-1] + " > " + case.get("name").replace("_", " ").rstrip("()")
            failure, error = case.find("failure"), case.find("error")
            if failure is not None:
                caught.append(name)
            elif error is not None and error.get("type") == TIMEOUT:
                caught.append(name)
            elif error is not None:
                broken.append(name + "  (" + (error.get("type") or "error") + ")")
    return caught, broken, total


def main():
    chosen = sys.argv[1:] or list(FAULTS)
    unknown = [f for f in chosen if f not in FAULTS]
    if unknown:
        sys.exit("unknown fault: " + ", ".join(unknown))

    caught, broken, total = run_suite("")
    if caught or broken or total == 0:
        print("The clean suite is not green, so the drills would prove nothing.")
        for name in caught + broken:
            print("        " + name)
        sys.exit(1)

    missed = 0
    for fault in chosen:
        caught, broken, _ = run_suite(fault)
        if broken:
            print(f"BROKEN  {fault}: the run failed for a reason that is not the fault")
            for name in broken:
                print("        " + name)
            missed += 1
            continue
        if not caught:
            print(f"MISSED  {fault}: {FAULTS[fault]}")
            missed += 1
            continue
        print(f"CAUGHT  {fault}: {FAULTS[fault]}")
        for name in caught[:SHOWN]:
            print("        " + name)
        if len(caught) > SHOWN:
            print(f"        and {len(caught) - SHOWN} more")

    print()
    if missed:
        print(f"{missed} of {len(chosen)} faults got past the suite.")
        sys.exit(1)
    print("Every fault was caught.")


if __name__ == "__main__":
    main()
