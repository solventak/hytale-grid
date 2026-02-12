# Launch Post Drafts

**Status:** DRAFT - Requires AK approval before posting

---

## Reddit Post - r/Hytale

**Title:** Grid: 4-State Digital Logic for Hytale Modding

**Body:**

Hey r/Hytale! I've been working on **Grid**, a foundation mod that brings 4-state digital logic to Hytale.

**What makes Grid different:**
- **4 logic states** instead of binary: LOW, HIGH, WEAK, and UNKNOWN (conflict)
- **Per-face networks** - each block face can belong to a different network
- **Multi-driver resolution** - multiple sources can drive the same net
- **Relay-controlled topology** - switches that alter network connectivity

**Core blocks:**
- Power Source (multi-input NOR gates)
- Wire (auto-connecting with visual updates)
- Relay (control-signal switches)
- Input Port (network state probes)
- Lamp (visual output)

Grid is designed as a **platform/foundation mod** - other modders can build on it to create tech mods, power systems, or complex circuitry.

**[TODO: Insert GIF/screenshot of working circuit here]**

**GitHub:** [Link TBD]  
**License:** MIT (open source)

Early alpha, expect API changes. Feedback welcome!

---

## Reddit Post - r/HytaleMods (if exists)

**Title:** Grid v0.1.0 - 4-State Logic Foundation Mod [API Preview]

**Body:**

Grid is a new foundation mod for Hytale that implements a robust 4-state digital logic system for modders to build on.

**Technical Features:**
- 4 logic states: LOW, HIGH, WEAK, UNKNOWN (conflict state destroys blocks)
- Per-face network assignment (6 independent networks per block)
- Multi-driver resolution with delta-cycle evaluation
- Relay-controlled dynamic topology
- Full ECS integration (Hytale's Entity Component System)

**API Usage Example:**
```kotlin
// Add Grid as a dependency
ExamplePlugin.powerSourceComponentType
ExamplePlugin.powerWireComponentType
ExamplePlugin.lampComponentType
```

**Use Cases:**
- Tech mods with power networks
- Complex redstone-like circuitry
- Modpack foundations
- Logic gate implementations

**[TODO: Insert technical diagram or circuit example]**

**Documentation:** Full KDoc comments throughout codebase  
**GitHub:** [Link TBD]  
**License:** MIT

Looking for early adopters and API feedback!

---

## Discord Announcement - Hytale Official Modding Channels

**Message:**

🦉 **Grid v0.1.0** - 4-State Digital Logic for Hytale

Grid is a foundation mod bringing robust digital logic to Hytale:
✅ 4-state logic (LOW, HIGH, WEAK, UNKNOWN)
✅ Per-face network assignment
✅ Multi-driver resolution
✅ Relay-controlled topology
✅ Open source (MIT)

Built as a platform for tech mods and modpacks.

**[TODO: Embed screenshot/GIF]**

GitHub: [Link TBD]

Early alpha - feedback welcome! 🦉

---

## What We Need for Posts

**Visual Content (TODO):**
1. **Primary GIF/video** (~10-20 seconds):
   - Place power source
   - Connect wires
   - Wires light up
   - Lamp turns on
   - Show relay switching

2. **Screenshot 1** - Simple circuit (source → wire → lamp)

3. **Screenshot 2** - Complex circuit showing multiple networks

4. **Screenshot 3** (optional) - Wire connection varieties (visual appeal)

**Timing Strategy:**
- **Reddit:** Post during peak hours (12-3 PM EST / 10 AM-1 PM MST)
- **Discord:** Post shortly after Reddit (link to Reddit post for discussion)
- **Week +1:** Follow-up with "Here's a working clock circuit" example

**Posting Checklist:**
- [ ] Visual content created
- [ ] GitHub repo made public
- [ ] Release v0.1.0 published on GitHub
- [ ] README polished
- [ ] AK reviews all post drafts
- [ ] Test links in posts before submitting
- [ ] Monitor comments/questions for first 2-3 hours

---

## Response Templates (for comments/questions)

**"Does this work with [other mod]?"**
> Grid is a foundation mod - other mods can integrate by using Grid's ECS components. I don't have compatibility info yet, but the API is designed to be extensible. Feel free to open a GitHub discussion if you're interested in integrating!

**"When will X feature be added?"**
> No promises on timelines - Grid is early alpha. If there's a feature you'd like to see, open a GitHub issue and we can discuss!

**"Is this similar to redstone?"**
> Conceptually yes, but Grid uses 4-state logic instead of binary, and supports per-face network assignment. Think of it more like electrical engineering than redstone.

**Bug reports:**
> Thanks for reporting! Can you open a GitHub issue with steps to reproduce? [link]

**"Can I use this in my modpack?"**
> Absolutely! Grid is MIT licensed. Just make sure to credit the mod and link to the GitHub repo.
