# Pre-Release Checklist

Based on RELEASE-MANAGEMENT.md - use this for each release.

---

## Build & Testing

- [x] **Mod builds successfully** - `./gradlew build -x test` works
- [ ] ⚠️ **Fix unit tests** - Currently 14 tests fail with `NoClassDefFoundError` (non-blocking for alpha)
- [ ] **Manual gameplay test**
  - Place blocks (source, wire, relay, lamp)
  - Verify power network propagation
  - Check visual updates (wire states, lamp on/off)
  - Test relay switching
- [ ] **Performance check**
  - Create large network (20+ blocks)
  - Check for lag/frame drops
- [ ] **Compatibility test**
  - Load world with mod installed
  - Place circuits
  - Restart server/client
  - Verify circuits still work

---

## Documentation

- [x] **README.md** - Complete with installation, features, API docs
- [x] **CHANGELOG.md** - Version history tracking
- [x] **LICENSE** - MIT license
- [ ] **Update version in `build.gradle.kts`** - Match release tag
- [ ] **Review CLAUDE.md** - Ensure API docs are current
- [ ] **Remove debug logging** - Check for verbose logs in production

---

## Visual Content

- [ ] **Primary GIF** - Short circuit demo (10-20 sec)
  - Place power source
  - Connect wires (show auto-connection)
  - Wires light up
  - Lamp turns on
  - Toggle relay
- [ ] **Screenshot 1** - Simple circuit (source → wire → lamp)
- [ ] **Screenshot 2** - Complex circuit (multiple networks)
- [ ] **Screenshot 3** - Wire connection varieties (visual appeal)

---

## Code Hygiene

- [ ] **Remove commented-out code** - Clean up dead code
- [ ] **Check for TODOs** - Address or document as known issues
- [ ] **Verify asset references** - All textures/models load correctly
- [ ] **Version string consistency**
  - `build.gradle.kts` version matches tag
  - README version references match
  - CHANGELOG version entry exists

---

## Repository

- [ ] **Decide GitHub username** - Update README URLs
- [ ] **Make repo public** - When AK approves
- [ ] **Create git tag** - `v0.1.0` with release notes
- [ ] **Push tag to GitHub** - `git push origin v0.1.0`

---

## GitHub Release

- [ ] **Create release on GitHub**
  - Tag: `v0.1.0`
  - Title: "Grid v0.1.0 - Initial Alpha Release"
  - Description: Copy from CHANGELOG, add screenshots
  - Attach: `Grid-v0.1.0.jar` from `build/libs/`
  - Check "Pre-release" box (for alpha/beta)
- [ ] **Verify download link works**

---

## Launch Posts

- [ ] **Reddit r/Hytale post** - Review draft in LAUNCH-POSTS-DRAFT.md
- [ ] **Reddit r/HytaleMods post** (if exists) - Technical version
- [ ] **Discord announcement** - Hytale modding channels
- [ ] **AK approves all posts** - REQUIRED before posting

---

## Post-Launch

- [ ] **Monitor feedback** - Reddit/Discord comments (first 2-3 hours)
- [ ] **Respond to questions** - Use response templates from LAUNCH-POSTS-DRAFT.md
- [ ] **Track GitHub issues** - Bug reports and feature requests
- [ ] **Plan week +1 follow-up** - "Here's a working circuit" example post

---

## Known Issues (Document These)

### Blockers (Must Fix)
- None identified yet

### Non-Blockers (Document as Known Issues)
- ⚠️ Unit tests failing (NoClassDefFoundError) - Need to investigate test setup
- (Add more as discovered during testing)

---

## Version Numbering

**For v0.1.0:**
- This is an **alpha release**
- Use GitHub pre-release checkbox
- Document in README: "Early Development - Expect breaking changes"

**Future versions:**
- `0.x.x` = Pre-1.0 (alpha/beta)
- `1.0.0` = First stable release
- Follow semantic versioning strictly post-1.0
