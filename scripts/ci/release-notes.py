#!/usr/bin/env python3
"""Prints Markdown release notes for one version from app/src/main/res/raw/changelog.json.

Usage: release-notes.py <version, e.g. 2.3.0> [changelog.json]
Exit code 3 when the changelog has no entry for that version (the workflow then falls back to
GitHub's generated notes and warns).
"""
import json
import sys

version = sys.argv[1].lstrip("v")
path = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/res/raw/changelog.json"
with open(path, encoding="utf-8") as fh:
    releases = json.load(fh).get("releases", [])

matches = [r for r in releases if str(r.get("version", "")).lstrip("v") == version]
if not matches:
    sys.exit(3)

lines = []
for entry in matches:
    section = entry.get("app")
    if len(matches) > 1 and section:
        lines.append(f"### {section}")
    lines.extend(f"- {item}" for item in entry.get("body", []))
    lines.append("")
print("\n".join(lines).strip())
