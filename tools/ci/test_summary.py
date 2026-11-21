"""Souhrn výsledků testů z XML souborů JUnit (Gradle / Android Test Orchestrator).

Použití: python3 tools/ci/test_summary.py <adresář s XML> [<další adresář> ...]
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

def testu(n: int) -> str:
    """Český tvar: 1 test, 2–4 testy, 5 a víc testů."""
    return f"{n} test" if n == 1 else f"{n} testy" if 2 <= n <= 4 else f"{n} testů"


total = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
rows = []
for d in sys.argv[1:]:
    for f in sorted(Path(d).rglob("*.xml")):
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for s in suites:
            vals = {k: int(s.get(k, 0) or 0) for k in total}
            for k in total:
                total[k] += vals[k]
            rows.append((s.get("name", f.stem), vals))

for name, v in rows:
    ok = v["tests"] - v["failures"] - v["errors"] - v["skipped"]
    print(f"{name}: {testu(v['tests'])}, OK {ok}, selhalo {v['failures'] + v['errors']}, přeskočeno {v['skipped']}")
ok = total["tests"] - total["failures"] - total["errors"] - total["skipped"]
print(f"CELKEM: {testu(total['tests'])}, OK {ok}, selhalo {total['failures'] + total['errors']}, přeskočeno {total['skipped']}")
