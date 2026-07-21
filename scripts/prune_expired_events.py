#!/usr/bin/env python3
"""Remove events from feed.json once their endsAt timestamp has passed."""

import json
from datetime import datetime, timezone
from pathlib import Path


FEED = Path(__file__).resolve().parents[1] / "feed.json"


def parse_iso(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def main() -> int:
    data = json.loads(FEED.read_text(encoding="utf-8"))
    now = datetime.now(timezone.utc)
    original = data.get("events", [])
    remaining = [event for event in original if parse_iso(event["endsAt"]).astimezone(timezone.utc) > now]

    if len(remaining) == len(original):
        print("Geen verlopen events gevonden.")
        return 0

    data["events"] = remaining
    data["updatedAt"] = now.isoformat().replace("+00:00", "Z")
    FEED.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(original) - len(remaining)} verlopen event(s) verwijderd.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
