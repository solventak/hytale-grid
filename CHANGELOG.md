# Changelog

All notable changes to Grid will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### In Development
- Initial public release preparation
- Documentation and README
- License (MIT)
- Release management process

---

## Version History

_(Versions will be listed here after first release)_

### [0.1.0] - 2026-02-14 (First Public Release)

**Added:**
- 4-state logic system (LOW, HIGH, WEAK, UNKNOWN)
- Core components:
  - PowerSource (multi-input NOR gates)
  - PowerWire (per-face network connections)
  - Relay (control-signal switches)
  - InputPort (network state probes)
  - Lamp (visual output blocks)
- Core systems:
  - TopologySystem (network evaluation)
  - VisualStateSystem (wire shape updates)
  - PowerBlockAddedSystem (placement events)
  - PowerBlockBreakEvent (destruction events)
- Wire generation scripts
- Per-face network assignment
- Multi-driver resolution
- Delta-cycle evaluation
- Relay-controlled topology
- Conflict detection (UNKNOWN_X state)

**Technical:**
- Kotlin 1.9+ plugin architecture
- Hytale ECS integration
- KDoc comments throughout codebase
- Architecture documentation in CLAUDE.md

---

## Unreleased Changes Log

_(Track unreleased changes here before tagging a version)_

**Example format:**
- `[Added]` New feature description
- `[Changed]` Modified behavior description
- `[Deprecated]` Soon-to-be-removed feature
- `[Removed]` Removed feature
- `[Fixed]` Bug fix description
- `[Security]` Security fix description

---

## Notes

- **Pre-1.0:** Frequent breaking changes expected. See migration guides in docs.
- **Post-1.0:** Strict semver adherence. Deprecation warnings before breaking changes.
