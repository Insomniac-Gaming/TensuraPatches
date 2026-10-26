"""Rebuild the tiny, source-controlled Minecraft 1.21.1 GameTest structure."""

import gzip
import struct
from pathlib import Path


def named(tag_type, name, payload):
    name_bytes = name.encode()
    return bytes([tag_type]) + struct.pack(">H", len(name_bytes)) + name_bytes + payload


def string(value):
    encoded = value.encode()
    return struct.pack(">H", len(encoded)) + encoded


def int_list(values):
    return b"\x03" + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound(payload):
    return payload + b"\x00"


def list_of_compounds(values):
    return b"\x0a" + struct.pack(">i", len(values)) + b"".join(compound(v) for v in values)


root = compound(
    named(3, "DataVersion", struct.pack(">i", 3955))
    + named(9, "size", int_list([1, 1, 1]))
    + named(9, "palette", list_of_compounds([named(8, "Name", string("minecraft:air"))]))
    + named(9, "blocks", list_of_compounds([
        named(9, "pos", int_list([0, 0, 0]))
        + named(3, "state", struct.pack(">i", 0))
    ]))
    + named(9, "entities", list_of_compounds([]))
)
data = named(10, "", root)
destination = Path(__file__).parent / "src/main/resources/data/insomniac_player_tests/structure/empty.nbt"
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_bytes(gzip.compress(data, mtime=0))
