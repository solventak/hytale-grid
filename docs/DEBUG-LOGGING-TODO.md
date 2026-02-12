# Debug Logging Cleanup TODO

**Status:** Non-blocking for alpha release, but should be addressed before beta/1.0.

---

## Issue

The codebase contains extensive debug `println()` statements throughout the newnet/ systems. These were used during development for topology/evaluation debugging but should not be in production code.

**Total count:** ~60+ active println statements across 7 files

---

## Files with Debug Logging

### High Priority (Most Verbose)
1. **TopologySystem.kt** - 27 println statements
   - Topology building, flood fill, source evaluation, visual updates
   - Most verbose file, logs every network operation

2. **BlockChangeEventSystems.kt** - 7 println statements
   - Block placement/break events, queue operations

3. **Mux2PlacementSystem.kt** - 7 println statements
   - MUX pairing logic, validation, destruction

### Medium Priority
4. **Mux2Control.kt** - 2 println statements
   - MUX routing changes

5. **Mux2Topology.kt** - 1 println statement
   - MUX mask computation

6. **RelayControl.kt** - 1 println statement
   - Relay toggle events

7. **VisualStateSystem.kt** - 2 println statements
   - Wire shape swapping, visual state changes

---

## Solutions

### Option 1: Conditional Debug Flag (Recommended)
Add a debug flag to ExamplePlugin that controls verbose logging:

```kotlin
companion object {
    const val DEBUG = false  // Set to true for debug logging
    // ... component types ...
}
```

Then replace all println statements with:
```kotlin
if (ExamplePlugin.DEBUG) println("[TopologySystem] ...")
```

**Pros:**
- Preserves debug capability for development
- Easy to toggle on/off
- No performance impact when disabled

**Cons:**
- More code churn

### Option 2: Remove All Printlns
Delete all debug println statements entirely.

**Pros:**
- Cleaner codebase
- No performance overhead

**Cons:**
- Loses debugging capability
- Harder to diagnose issues in production

### Option 3: Proper Logging Framework
Replace println with proper logger usage:
```kotlin
logger.at(Level.FINE).log("[TopologySystem] ...")
```

**Pros:**
- Professional logging solution
- Configurable log levels
- Better for production

**Cons:**
- Most code churn
- Requires logger setup in each class

---

## Recommendation

For v0.1.0 alpha: **Leave as-is, document as known issue**  
For v0.2.0+: **Implement Option 1 (DEBUG flag)**  
For v1.0.0: **Implement Option 3 (proper logging)**

---

## Other Debug Code

### Commented-Out Printlns
Many files have commented-out println statements (e.g., PoweredComponent.kt has 30+). These can be safely deleted since they're already disabled.

### WireLookupTable.kt
This file has 5 println statements that print a lookup table. This appears to be intentional debug output (table visualization), not logging. Consider moving to a separate debug utility or removing if not needed.

### Source.kt
Has active println statements for BFS pathfinding. Should be removed or gated behind DEBUG flag.

---

## Impact Assessment

**Performance:** Minimal impact for alpha (printlns only fire during topology changes)  
**User Experience:** Console spam during gameplay (annoying but not breaking)  
**Release Blocking:** No - acceptable for alpha, should fix for beta

---

## Action Items

- [ ] Decide on logging strategy (Option 1, 2, or 3)
- [ ] Clean up or remove commented-out println statements
- [ ] Document debug mode in README if keeping DEBUG flag
- [ ] Add to CHANGELOG as "Known Issue" for v0.1.0
