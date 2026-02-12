# newnet - 4-State Logic Power Network System

A Minecraft-inspired redstone-like power system for Hytale, implemented with 4-state logic and per-face network assignment.

## Architecture Overview

### Core Concept

The newnet system implements a **per-face network model** where each of a block's 6 faces can belong to a different power network. This enables fine-grained control over power routing (e.g., relay control faces isolated from conduction paths).

Networks carry one of four states:
- **ZERO** - Logic low (off)
- **ONE** - Logic high (on)
- **HIGH_Z** - High-impedance (floating, no driver)
- **UNKNOWN_X** - Conflict/error state (destroys all connected blocks)

### Block Types

#### PowerSource (Driver)
- Acts as a multi-input **inverting gate** (NOR logic)
- Reads adjacent InputPort probes
- Inverts the combined input and drives all connectable faces
- Default-on (drives ONE when no inputs)

#### PowerWire
- Passively routes power
- Star-connects all 6 connectable faces (all faces share same network)
- Auto-swaps between 24 visual variants based on neighbor connections
- Variants: straight, corner, T-junction, cross, vertical pipes, etc.

#### Relay
- **Controlled switch** that alters network topology
- Two face types:
  - **Control faces**: Adjacent to InputPorts (isolated, read-only)
  - **Conduction faces**: All other connectable faces
- When **enabled**: Conduction faces star-connect (power flows through)
- When **disabled**: No internal connectivity (acts as 6 isolated endpoints)
- Control evaluation: Pure OR (any control net ONE → enabled)

#### InputPort
- **Network probe** that bridges control logic to power networks
- Has NO network membership (not part of any net)
- One face points toward PowerSource/Relay (driver side)
- Opposite face probes adjacent block's network
- Auto-configures driverSideFace on placement (destroyed if no valid driver found)

#### Mux2 (2:1 Multiplexer)
- **Selective relay** — 2-block multiblock that routes one of two inputs to output
- Topology-level routing (not computed): physically connects Y to A or B net based on S
- **Physical layout**: Two Mux2Part blocks side-by-side along a pair axis
  - **S (select)**: InputPort on the narrow face (only one MUX block exposed)
  - **A (input 0)**: InputPort on the fat end, on the block closer to S
  - **B (input 1)**: InputPort on the fat end, on the block farther from S
  - **Y (output)**: Opposite fat end — conducts to whichever input is selected
- **Select behavior**:
  - S=0 (ZERO) → Y connects to A's net
  - S=1 (ONE) → Y connects to B's net
  - S=HIGH_Z → disconnected (no conduction)
  - S=UNKNOWN_X → disconnected + controlFault (safe-off)
- **InputPort pass-through**: A/B InputPorts bridge power through to MUX input faces
  (unlike normal InputPorts which only sense). Flood fill reaches through them in both directions.
- **Dynamic conduction mask**: Each MUX block's connectable faces change based on select state.
  Only selected input face + pair face + output face conduct. Non-selected input is isolated.
- **Placement**: Incomplete MUX (single block) self-destructs if adjacent to any PowerConnectable,
  InputPort, or complete MUX. Two adjacent Mux2Part blocks auto-pair.
- **Visual**: Selected block shows "On" state (blue), non-selected shows "default"
- Color: Blue (blue_off/blue_on textures)

#### Lamp
- Passive power consumer
- Lit when ANY connected face's network is ONE
- Useful for debugging and visual indicators

### Components

| Component | Purpose |
|-----------|---------|
| `PowerConnectable` | Declares which faces can connect (6-bit mask) |
| `PowerNetIds` | Stores network ID per face (6 IDs per block) |
| `PowerSource` | Marks block as inverting driver, stores drive state |
| `PowerWire` | Marker for wire blocks (special connectivity) |
| `Relay` | Stores relay enabled/controlFault/lastEnabled state |
| `Mux2Part` | Stores pairedPos, pairFace, selectedInput, isDisconnected, controlFault |
| `InputPort` | Stores driverSideFace configuration |
| `Lamp` | Stores lit state |
| `VisualState` | Generic visual state component ("default", "On") |

### Resource

**StateChangeEventQueue** (store-level singleton):
- `changes`: Pending PLACED/DESTROYED events
- `nextNetId`: Auto-incrementing network ID allocator
- `powerNetValueCache`: Current 4-state value per network
- `netMembers`: Reverse index (net ID → set of block faces)
- `visualDirtyPositions`: Positions needing visual update
- `wireDirtyPositions`: Wire positions needing shape update

## Execution Flow

### TopologySystem.tick()

The main system that runs each tick when there are pending changes.

#### Outer Loop: Topology Rounds (max 8)

Handles relay state changes that alter network connectivity.

1. **Expand seeds** → include neighbors + InputPort driver blocks
2. **Phase 1: Clear nets** → Invalidate all networks touching seed blocks
3. **Phase 2: Rebuild topology** → Flood fill to assign new network IDs
4. **Iteratively expand for InputPorts** → Continue until stable
5. **Phase 3-5: Delta-cycle evaluation** (inner loop, max 64 cycles):
   - Initialize all nets to HIGH_Z
   - Evaluate PowerSource blocks (compute inverter drive)
   - Resolve network values (multi-driver 4-state)
   - Check if stable (no net values changed)
   - Repeat until stable or max cycles reached
   - If unstable → force all nets to UNKNOWN_X
6. **Phase 6: Evaluate relay and MUX controls** → Read InputPort probes, update states
   - Relay: OR of control inputs → enabled/disabled
   - MUX: Read S InputPort → selectedInput (0=A, 1=B) or disconnected
   - **MUX controls only evaluated on round 0** to prevent oscillation
     (re-seeding clears S's net; re-probing would read HIGH_Z and oscillate)
7. **If any relay/MUX toggled** → collect positions, expand through InputPorts
   (including far-side blocks behind MUX InputPorts), and loop back
8. **If stable** → break outer loop

#### Post-Topology Processing

After topology stabilizes (or rounds exhaust):

- **Phase 8**: Destroy blocks on UNKNOWN_X networks ("magic smoke")
- **Phase 9-10**: Update visual states for all block types
  - Lamp: "On" if any net is ONE
  - Relay: "On" if enabled
  - PowerSource: "On" if driveState is ONE
  - InputPort: "On" if probed net is ONE
  - MUX: Selected block "On", non-selected "default"
  - PowerWire: "On" if any net is ONE

### VisualStateSystem.tick()

Runs AFTER TopologySystem (system dependency).

1. **Phase 1: Wire shape updates**
   - Process `wireDirtyPositions`
   - Compute connections using WireLookupTable
   - Swap wire variant if needed (via world.setBlock)
   - Preserve powered state across swaps

2. **Phase 2: Visual state updates**
   - Process `visualDirtyPositions`
   - Read VisualState.state for each position
   - Call worldChunk.setBlockInteractionState()

### Event Systems

**PowerBlockAddedSystem** (ChunkStore RefSystem):
- Triggers on block entity addition
- Validates InputPort placement (finds adjacent driver)
- Queues PLACED event
- Marks wire + neighbors dirty (for shape updates)

**PowerBlockBreakEvent** (EntityStore EventSystem):
- Triggers on player block break
- Queues DESTROYED event
- Marks wire neighbors dirty (for shape updates)

## Topology Rules

### Flood Fill Connectivity

During network assignment (floodFillPower):

1. **Wire blocks**: All connectable faces star-connect (same net)
2. **Relay blocks (enabled)**: Conduction faces star-connect, control faces isolated
3. **Relay blocks (disabled)**: No internal connectivity
4. **MUX blocks (complete, non-disconnected)**: Dynamic conduction mask based on select state
   - Selected input face + pair face + output face conduct
   - Non-selected input face is isolated
   - Seed generation uses conduction mask (not facesMask) to prevent spurious nets
   - Flood fill entry check also uses conduction mask
5. **Cross-block adjacency**: Face N connects to OPPOSITE_FACE[N] on neighbor
6. **MUX InputPort pass-through**: When flood fill reaches a MUX input face or a wire
   adjacent to a MUX InputPort, it bridges through the InputPort to the block on the
   other side. This allows power to flow: source → wire → InputPort → MUX input face.
   Works in both directions (MUX→wire and wire→MUX).
7. **MUX net merge**: If flood fill reaches a MUX face that already has a different net
   (from a different flood fill path), the nets are merged (all members reassigned).

### Relay Control Evaluation

Pure OR logic with safe-off on error:

- No control faces → disabled, no fault
- Any control net is ONE → enabled
- Any control net is UNKNOWN_X → disabled with controlFault=true
- All control nets ZERO/HIGH_Z → disabled

### Multi-Driver Resolution

Networks can have multiple PowerSource drivers. State4.resolve() implements tri-state logic:

| Drivers | Result |
|---------|--------|
| [] | HIGH_Z (floating) |
| [ONE] | ONE |
| [ONE, HIGH_Z] | ONE (Z ignored) |
| [ZERO, ONE] | UNKNOWN_X (conflict) |
| [ONE, UNKNOWN_X] | UNKNOWN_X (error propagates) |

## Limits and Safety

- **MAX_DELTA_CYCLES**: 64 (inner evaluation loop per topology round)
- **MAX_TOPOLOGY_ROUNDS**: 8 (outer relay toggle loop)
- **Oscillation detection**: If unstable → force UNKNOWN_X → destroy blocks
- **Safe-off**: Relays disable on UNKNOWN_X control inputs

## Example Circuits

### Inverter
```
Wire → InputPort → [PowerSource] → Wire → Lamp
        (probe)      (inverts)     (output)
```

### Relay Switch
```
Wire (control) → InputPort → [Relay] → Wire → Lamp
                  (probe)     (switch)
```

### 2:1 MUX
```
                    S (select)
                    │
               InputPort
                    │
          ┌─────[MUX MUX]─────┐
          │    (pair axis)     │
     InputPort            (output fat end)
     InputPort                 │
          │                  Lamp
     Wire A / Wire B
     (power sources)
```
- S=0 → Lamp shows Wire A's value
- S=1 → Lamp shows Wire B's value
- InputPorts on A/B are pass-through (power flows through them)

### Feedback Loop (Oscillator)
```
[PowerSource] → Wire → InputPort ↩
     ↑                    ↓
     └────────────────────┘
(Oscillates → UNKNOWN_X → destroys)
```

## Wire Visual System

Wires automatically swap between 24 model variants based on connectivity:

- **Vertical component**: `XX` (none), `UX` (up), `XD` (down), `UD` (both)
- **Horizontal pattern**: `0` (none), `1` (single), `2a` (adjacent pair), `2o` (opposite pair), `3` (three-way T), `4` (cross)

Example: `Wire_UD_2o` = vertical pipe (up+down) with opposite horizontal connections (N-S or E-W)

See `WireLookupTable.kt` for the full 64-entry mapping table.

## File Structure

```
newnet/
├── README.md                      (this file)
├── TopologySystem.kt              Core evaluation system
├── VisualStateSystem.kt           Visual application system
├── BlockChangeEventSystems.kt     Placement/destruction handlers
├── RelayControl.kt                Relay control evaluation logic
├── PowerConnectable.kt            Face connectivity component
├── PowerNetIds.kt                 Per-face network ID storage
├── Driver.kt                      PowerSource component
├── PowerWire.kt                   Wire marker component
├── Lamp.kt                        Lamp component
├── Relay.kt                       Relay component
├── InputPort.kt                   Network probe component
├── Mux2Part.kt                    MUX multiblock component
├── Mux2Control.kt                 MUX select evaluation + control logic
├── Mux2PlacementSystem.kt         MUX pairing, validation, destruction
├── Mux2Topology.kt                MUX conduction mask + topology helpers
├── VisualState.kt                 Generic visual state component
└── shared/
    ├── State4.kt                  4-state logic enum
    ├── FaceMask.kt                Face bitmask enum
    └── NetKind.kt                 Network type enum (placeholder)
```

## Future Enhancements

- Signal networks (separate from power)
- Analog power levels (0-15 like redstone)
- Network size limits (prevent mega-networks)
- Per-network tick budgets
- Visual wire coloring by network
- Network inspection tool (show IDs and values)
