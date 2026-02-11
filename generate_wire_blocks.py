#!/usr/bin/env python3
"""
Wire Block JSON Generator

Generates wire block JSON definition files for the 24 wire model variants.
Uses yaw rotation to cover all 64 connection states.

Naming: Wire_[vertical]_[horizontal]
- Vertical: XX (none), UX (up), XD (down), UD (both)
- Horizontal: 0 (none), 1 (single/N), 2a (adjacent/NE), 2o (opposite/NS), 3 (three/NEW), 4 (all/NESW)
"""

import json
import os

# Output directory for generated JSON files
OUTPUT_DIR = "src/main/resources/Server/Item/Items"

# The 24 wire variants
# Format: (variant_name, vertical_desc, horizontal_desc)
VARIANTS = [
    # No vertical (XX)
    ("Wire_XX_0", "no vertical", "center only"),
    ("Wire_XX_1", "no vertical", "north"),
    ("Wire_XX_2a", "no vertical", "north + east (adjacent)"),
    ("Wire_XX_2o", "no vertical", "north + south (opposite)"),
    ("Wire_XX_3", "no vertical", "north + east + west"),
    ("Wire_XX_4", "no vertical", "all horizontal (NESW)"),

    # Up only (UX)
    ("Wire_UX_0", "up only", "center only"),
    ("Wire_UX_1", "up only", "north"),
    ("Wire_UX_2a", "up only", "north + east (adjacent)"),
    ("Wire_UX_2o", "up only", "north + south (opposite)"),
    ("Wire_UX_3", "up only", "north + east + west"),
    ("Wire_UX_4", "up only", "all horizontal (NESW)"),

    # Down only (XD)
    ("Wire_XD_0", "down only", "center only"),
    ("Wire_XD_1", "down only", "north"),
    ("Wire_XD_2a", "down only", "north + east (adjacent)"),
    ("Wire_XD_2o", "down only", "north + south (opposite)"),
    ("Wire_XD_3", "down only", "north + east + west"),
    ("Wire_XD_4", "down only", "all horizontal (NESW)"),

    # Both up and down (UD)
    ("Wire_UD_0", "up + down", "center only"),
    ("Wire_UD_1", "up + down", "north"),
    ("Wire_UD_2a", "up + down", "north + east (adjacent)"),
    ("Wire_UD_2o", "up + down", "north + south (opposite)"),
    ("Wire_UD_3", "up + down", "north + east + west"),
    ("Wire_UD_4", "up + down", "all horizontal (NESW)"),
]


def generate_wire_block(variant_name: str, vertical_desc: str, horizontal_desc: str) -> dict:
    """Generate a single wire block JSON definition."""
    model_path = f"Blocks/wire/{variant_name}.blockymodel"
    description = f"{vertical_desc}, {horizontal_desc}"

    return {
        "Icon": "Icons/ItemsGenerated/wire.png",
        "CustomModel": model_path,
        "IconProperties": {
            "Scale": 0.58823,
            "Rotation": [22.5, 45, 22.5],
            "Translation": [0, -13.5]
        },
        "Categories": ["Furniture.Furniture"],
        "MaxStack": 100,
        "Recipe": {
            "Input": [{"ItemId": "Soil_Dirt"}],
            "Output": [{"ItemId": variant_name}],
            "BenchRequirement": [{
                "Id": "Fieldcraft",
                "Type": "Crafting",
                "Categories": ["Tools"]
            }]
        },
        "PlayerAnimationsId": "Block",
        "Set": "Wire_Block",
        "BlockType": {
            "CustomModel": model_path,
            "CustomModelTexture": [{"Texture": "BlockTextures/black.png"}],
            "BlockEntity": {
                "Components": {
                    "Transport": {}
                }
            },
            "Material": "Solid",
            "DrawType": "Model",
            "Group": "Wire",
            "Flags": {},
            "Gathering": {
                "Breaking": {
                    "GatherType": "Rocks"
                }
            },
            "BlockParticleSetId": "Stone",
            "Textures": [{
                "Sides": "BlockTextures/black.png",
                "UpDown": "BlockTextures/black.png",
                "Weight": 5
            }],
            "ParticleColor": "#615d44",
            "BlockSoundSetId": "Stone"
        },
        "ResourceTypes": [
            {"Id": "Rock"},
            {"Id": "Rock_Stone"}
        ],
        "Tags": {
            "Type": ["Redstone"],
            "Family": ["Wire"],
            "WireVariant": [variant_name]
        },
        "ItemSoundSetId": "ISS_Blocks_Stone",
        "_comment": f"Wire variant: {description}"
    }


def generate_base_wire_item() -> dict:
    """Generate the base Wire item that players place."""
    return {
        "Icon": "Icons/ItemsGenerated/wire.png",
        "CustomModel": "Blocks/wire/Wire_XX_0.blockymodel",
        "IconProperties": {
            "Scale": 0.58823,
            "Rotation": [22.5, 45, 22.5],
            "Translation": [0, -13.5]
        },
        "Categories": ["Furniture.Furniture"],
        "MaxStack": 100,
        "Recipe": {
            "Input": [{"ItemId": "Soil_Dirt"}],
            "Output": [{"ItemId": "Wire"}],
            "BenchRequirement": [{
                "Id": "Fieldcraft",
                "Type": "Crafting",
                "Categories": ["Tools"]
            }]
        },
        "PlayerAnimationsId": "Block",
        "Set": "Wire_Block",
        "BlockType": {
            "CustomModel": "Blocks/wire/Wire_XX_0.blockymodel",
            "CustomModelTexture": [{"Texture": "BlockTextures/black.png"}],
            "BlockEntity": {
                "Components": {
                    "Transport": {}
                }
            },
            "Material": "Solid",
            "DrawType": "Model",
            "Group": "Wire",
            "Flags": {},
            "Gathering": {
                "Breaking": {
                    "GatherType": "Rocks",
                    "ItemId": "Wire"
                }
            },
            "BlockParticleSetId": "Stone",
            "Textures": [{
                "Sides": "BlockTextures/black.png",
                "UpDown": "BlockTextures/black.png",
                "Weight": 5
            }],
            "ParticleColor": "#615d44",
            "BlockSoundSetId": "Stone"
        },
        "ResourceTypes": [
            {"Id": "Rock"},
            {"Id": "Rock_Stone"}
        ],
        "Tags": {
            "Type": ["Redstone"],
            "Family": ["Wire"]
        },
        "ItemSoundSetId": "ISS_Blocks_Stone",
        "_comment": "Base wire item - players craft and place this"
    }


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print("Generating Wire Block JSON Files (24 variants)")
    print("=" * 50)

    # Generate base Wire item
    wire_json = generate_base_wire_item()
    filepath = os.path.join(OUTPUT_DIR, "wire.json")
    with open(filepath, 'w') as f:
        json.dump(wire_json, f, indent=2)
    print(f"Created: {filepath}")

    # Generate 24 variant JSON files
    for variant_name, vertical_desc, horizontal_desc in VARIANTS:
        block_json = generate_wire_block(variant_name, vertical_desc, horizontal_desc)

        filename = f"{variant_name}.json"
        filepath = os.path.join(OUTPUT_DIR, filename)

        with open(filepath, 'w') as f:
            json.dump(block_json, f, indent=2)

        print(f"Created: {filepath}")

    print("=" * 50)
    print(f"Generated {len(VARIANTS) + 1} JSON files in {OUTPUT_DIR}")
    print("\nVariants by vertical state:")
    print("  XX (no vertical):  0, 1, 2a, 2o, 3, 4")
    print("  UX (up only):      0, 1, 2a, 2o, 3, 4")
    print("  XD (down only):    0, 1, 2a, 2o, 3, 4")
    print("  UD (up + down):    0, 1, 2a, 2o, 3, 4")


if __name__ == "__main__":
    main()
