"""Turns `gh pr list --json` output on stdin into the website's changelog.json on stdout."""
import json
import re
import sys

STOP = re.compile(r"claude|anthropic|generated with|co-authored|\U0001F916|^#+\s*test plan", re.I)
POINT = re.compile(r"^[-*]\s+(?:\[[ x]\]\s+)?(.+)")


CHECKS = re.compile(r"^#+\s*(test|testing|checks?|how to test|verification|notes?)", re.I)


def short(text):
    """The first sentence of a long point."""
    text = text.replace("**", "").replace("`", "")
    if len(text) <= 160:
        return text
    cut = re.split(r"(?<=[.!?])\s", text, maxsplit=1)[0]
    return cut if len(cut) <= 180 else cut[:157].rstrip() + "..."


def points(body):
    """The first bullets of the description, up to the test notes."""
    found = []
    for line in (body or "").splitlines():
        line = line.strip()
        if STOP.search(line) or CHECKS.match(line) or (line.startswith("#") and found):
            break
        m = POINT.match(line)
        if m and len(m.group(1)) > 3:
            found.append(short(m.group(1)))
        if len(found) >= 5:
            break
    return found


prs = json.load(sys.stdin)
entries = [
    {
        "number": p["number"],
        "title": p["title"],
        "mergedAt": p["mergedAt"],
        "author": (p.get("author") or {}).get("login"),
        "url": p["url"],
        "points": points(p.get("body")),
    }
    for p in prs
    if p.get("mergedAt")
]
entries.sort(key=lambda e: e["mergedAt"], reverse=True)
json.dump(entries, sys.stdout, ensure_ascii=False, indent=1)
