#!/usr/bin/env python3
"""
Wire Connection Lookup Table

Maps all 64 possible wire connection states (6 faces: U, D, N, E, S, W)
to one of 24 canonical model variants + a yaw rotation (0, 90, 180, 270).

This reduces 64 models down to 24 by using Y-axis rotation for horizontal symmetry.

Naming: Wire_[vertical]_[horizontal]
- Vertical: XX (none), UX (up), XD (down), UD (both)
- Horizontal: 0 (none), 1 (single/N), 2a (adjacent/NE), 2o (opposite/NS), 3 (three/NEW), 4 (all/NESW)
"""

from dataclasses import dataclass
from enum import Enum
from typing import Tuple

class Rotation(Enum):
    NONE = 0
    NINETY = 90
    ONE_EIGHTY = 180
    TWO_SEVENTY = 270

# The 24 canonical wire model variants
VARIANTS = [
    # No vertical (index 0-5)
    "Wire_XX_0",   # center only
    "Wire_XX_1",   # N (single)
    "Wire_XX_2a",  # NE (adjacent pair)
    "Wire_XX_2o",  # NS (opposite pair)
    "Wire_XX_3",   # NEW (three-way)
    "Wire_XX_4",   # NESW (all four)

    # Up only (index 6-11)
    "Wire_UX_0",
    "Wire_UX_1",
    "Wire_UX_2a",
    "Wire_UX_2o",
    "Wire_UX_3",
    "Wire_UX_4",

    # Down only (index 12-17)
    "Wire_XD_0",
    "Wire_XD_1",
    "Wire_XD_2a",
    "Wire_XD_2o",
    "Wire_XD_3",
    "Wire_XD_4",

    # Both up and down (index 18-23)
    "Wire_UD_0",
    "Wire_UD_1",
    "Wire_UD_2a",
    "Wire_UD_2o",
    "Wire_UD_3",
    "Wire_UD_4",
]

@dataclass
class WireConnections:
    """Represents connections on 6 faces."""
    up: bool = False
    down: bool = False
    north: bool = False
    east: bool = False
    south: bool = False
    west: bool = False

    def to_index(self) -> int:
        """Convert to 6-bit index (0-63). Bit order: U D N E S W"""
        index = 0
        if self.up:    index |= 0b100000  # bit 5
        if self.down:  index |= 0b010000  # bit 4
        if self.north: index |= 0b001000  # bit 3
        if self.east:  index |= 0b000100  # bit 2
        if self.south: index |= 0b000010  # bit 1
        if self.west:  index |= 0b000001  # bit 0
        return index

    @classmethod
    def from_index(cls, index: int) -> 'WireConnections':
        """Create from 6-bit index."""
        return cls(
            up    = bool(index & 0b100000),
            down  = bool(index & 0b010000),
            north = bool(index & 0b001000),
            east  = bool(index & 0b000100),
            south = bool(index & 0b000010),
            west  = bool(index & 0b000001),
        )

    def __str__(self):
        chars = []
        chars.append('U' if self.up else '.')
        chars.append('D' if self.down else '.')
        chars.append('N' if self.north else '.')
        chars.append('E' if self.east else '.')
        chars.append('S' if self.south else '.')
        chars.append('W' if self.west else '.')
        return ''.join(chars)


def get_horizontal_pattern(n: bool, e: bool, s: bool, w: bool) -> Tuple[int, Rotation]:
    """
    Determine the horizontal pattern type and required yaw rotation.

    Returns: (pattern_offset, rotation)

    Pattern offsets (added to vertical base):
        0 = none (center only)
        1 = single connection (canonical: North)
        2 = adjacent pair (canonical: North-East)
        3 = opposite pair (canonical: North-South)
        4 = three-way (canonical: NEW, missing South)
        5 = all four
    """
    count = sum([n, e, s, w])

    if count == 0:
        # No horizontal connections
        return (0, Rotation.NONE)

    elif count == 1:
        # Single connection - canonical is North
        if n: return (1, Rotation.NONE)
        if e: return (1, Rotation.NINETY)
        if s: return (1, Rotation.ONE_EIGHTY)
        if w: return (1, Rotation.TWO_SEVENTY)

    elif count == 2:
        # Check opposite vs adjacent
        if (n and s) or (e and w):
            # Opposite pair - canonical is North-South
            if n and s: return (3, Rotation.NONE)
            if e and w: return (3, Rotation.NINETY)
        else:
            # Adjacent pair - canonical is North-East
            if n and e: return (2, Rotation.NONE)
            if e and s: return (2, Rotation.NINETY)
            if s and w: return (2, Rotation.ONE_EIGHTY)
            if w and n: return (2, Rotation.TWO_SEVENTY)

    elif count == 3:
        # Three-way - canonical is NEW (missing South)
        if not s: return (4, Rotation.NONE)       # NEW, missing S
        if not w: return (4, Rotation.NINETY)     # NES, missing W
        if not n: return (4, Rotation.ONE_EIGHTY) # ESW, missing N
        if not e: return (4, Rotation.TWO_SEVENTY) # SWN, missing E

    elif count == 4:
        # All four - no rotation needed
        return (5, Rotation.NONE)

    return (0, Rotation.NONE)


def get_variant(conn: WireConnections) -> Tuple[str, Rotation]:
    """
    Get the canonical variant name and yaw rotation for a connection state.

    Returns: (variant_name, rotation)
    """
    # Determine vertical base index (0, 6, 12, or 18)
    if conn.up and conn.down:
        vertical_base = 18  # UD
    elif conn.up:
        vertical_base = 6   # UX
    elif conn.down:
        vertical_base = 12  # XD
    else:
        vertical_base = 0   # XX

    # Determine horizontal pattern and rotation
    h_offset, rotation = get_horizontal_pattern(conn.north, conn.east, conn.south, conn.west)

    # Look up variant name
    variant_index = vertical_base + h_offset
    variant_name = VARIANTS[variant_index]

    return (variant_name, rotation)


def build_lookup_table() -> list:
    """Build the complete 64-entry lookup table."""
    table = []
    for i in range(64):
        conn = WireConnections.from_index(i)
        variant, rotation = get_variant(conn)
        table.append({
            'index': i,
            'connections': conn,
            'variant': variant,
            'rotation': rotation,
        })
    return table


def print_lookup_table():
    """Print the lookup table in a readable format."""
    table = build_lookup_table()

    print("Wire Lookup Table (64 states -> 24 variants + yaw rotation)")
    print("=" * 70)
    print(f"{'Index':>5} | {'UDNESW':^6} | {'Variant':<12} | {'Rotation':<12}")
    print("-" * 70)

    for entry in table:
        print(f"{entry['index']:>5} | {str(entry['connections']):^6} | {entry['variant']:<12} | {entry['rotation'].name:<12}")


def print_variant_summary():
    """Print summary of which states map to each variant."""
    table = build_lookup_table()

    print("\n" + "=" * 70)
    print("Variant Summary (which connection states use each model)")
    print("=" * 70)

    # Group by variant
    by_variant = {}
    for entry in table:
        v = entry['variant']
        if v not in by_variant:
            by_variant[v] = []
        by_variant[v].append(entry)

    for variant in VARIANTS:
        entries = by_variant.get(variant, [])
        print(f"\n{variant}:")
        for e in entries:
            conn = e['connections']
            rot = e['rotation']
            print(f"  {str(conn)} (index {e['index']:>2}) -> rotate {rot.value:>3} deg")


if __name__ == "__main__":
    print_lookup_table()
    print_variant_summary()

    # Example usage
    print("\n" + "=" * 70)
    print("Example Usage:")
    print("=" * 70)

    # Wire connected to North and Up
    conn = WireConnections(up=True, north=True)
    variant, rotation = get_variant(conn)
    print(f"\nConnections: {conn}")
    print(f"  -> Use model: {variant}")
    print(f"  -> Rotate yaw: {rotation.value} degrees")

    # Wire connected to East, South, West (missing North) + Down
    conn = WireConnections(down=True, east=True, south=True, west=True)
    variant, rotation = get_variant(conn)
    print(f"\nConnections: {conn}")
    print(f"  -> Use model: {variant}")
    print(f"  -> Rotate yaw: {rotation.value} degrees")
