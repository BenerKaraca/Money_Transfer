"""Print JaCoCo source lines with uncovered branches after a CI test run."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

report = Path(sys.argv[1])
if not report.is_file():
    print(f"JaCoCo report not found: {report}")
    raise SystemExit(0)

root = ET.parse(report).getroot()
missing = []
for package in root.findall("package"):
    for source in package.findall("sourcefile"):
        for line in source.findall("line"):
            count = int(line.get("mb", "0"))
            if count:
                missing.append(f"{package.get('name')}/{source.get('name')}:{line.get('nr')} missed={count}")

print("Uncovered branches:")
print("\n".join(missing) if missing else "none")
