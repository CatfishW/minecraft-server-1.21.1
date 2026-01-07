#!/usr/bin/env python3
"""Serialize the Let's Do Vinery quest flow into a JSON companion file."""
from __future__ import annotations

from dataclasses import asdict, dataclass, field
from pathlib import Path
import json

OUTPUT_PATH = Path(__file__).with_name("vinery_story.json")


@dataclass
class Quest:
    quest_id: str
    title: str
    subtitle: str
    depends_on: list[str]
    size: int
    branch: bool
    tasks: list[dict]
    rewards: list[dict] = field(default_factory=list)


QUESTS: list[Quest] = [
    Quest(
        quest_id="34A7A4F5EAF7D5B2",
        title="Pressing Debut",
        subtitle="Grandma's Apple Press",
        depends_on=["1A06F05953C3949F"],
        size=2,
        branch=False,
        tasks=[{"type": "item", "item": "vinery:apple_press", "count": 1}],
        rewards=[
            {"item": "vinery:red_grape_bag", "count": 1},
            {"item": "vinery:oak_lattice", "count": 6},
        ],
    ),
    Quest(
        quest_id="3B4E5C1AFF8710E4",
        title="Cellar Order",
        subtitle="Display the Vintage",
        depends_on=["34A7A4F5EAF7D5B2"],
        size=0,
        branch=True,
        tasks=[{"type": "item", "item": "vinery:oak_wine_rack_small", "count": 1}],
        rewards=[
            {"item": "vinery:wine_bottle", "count": 2},
            {"item": "minecraft:lantern", "count": 1},
        ],
    ),
    Quest(
        quest_id="0DF541F92F7A3B8C",
        title="Fermentation Control",
        subtitle="Time Inside Barrels",
        depends_on=["34A7A4F5EAF7D5B2"],
        size=2,
        branch=False,
        tasks=[{"type": "item", "item": "vinery:fermentation_barrel", "count": 1}],
        rewards=[
            {"item": "vinery:white_grape_bag", "count": 1},
            {"item": "vinery:wine_bottle", "count": 2},
        ],
    ),
    Quest(
        quest_id="1E7D0E2A504E1C61",
        title="Seasonal Notes",
        subtitle="Window Box Journal",
        depends_on=["0DF541F92F7A3B8C"],
        size=0,
        branch=True,
        tasks=[{"type": "item", "item": "vinery:flower_box", "count": 1}],
        rewards=[{"item": "vinery:calendar", "count": 1}],
    ),
    Quest(
        quest_id="5E62A8D21CA4B17F",
        title="First Bottles",
        subtitle="Corking Ceremony",
        depends_on=["0DF541F92F7A3B8C"],
        size=2,
        branch=False,
        tasks=[{"type": "item", "item": "vinery:wine_bottle", "count": 3}],
        rewards=[
            {"item": "vinery:wine_box", "count": 1},
            {"item": "vinery:wine_bottle", "count": 2},
        ],
    ),
    Quest(
        quest_id="7C85F498FAE2AD0C",
        title="Fragrant Soirée",
        subtitle="Share the Vintage",
        depends_on=["5E62A8D21CA4B17F"],
        size=2,
        branch=False,
        tasks=[{"type": "item", "item": "vinery:aegis_wine", "count": 1}],
        rewards=[
            {"item": "vinery:lilitu_wine", "count": 1},
            {"item": "vinery:basket", "count": 1},
        ],
    ),
]


def write_story(output_path: Path = OUTPUT_PATH) -> Path:
    """Write the quest summary JSON file and return its path."""
    payload = {
        "chapter": "0A339E631AC882D0",
        "theme": "Let's Do Vinery",
        "quests": [asdict(q) for q in QUESTS],
    }
    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return output_path


if __name__ == "__main__":
    destination = write_story()
    print(f"Story JSON written to {destination}")
