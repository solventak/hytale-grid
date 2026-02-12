# Grid Release Management

## Version Control Strategy

**Branch Model:**
- `main` - stable release branch (what users download)
- `dev` - active development, features merge here first
- `feature/*` - individual feature branches
- `hotfix/*` - critical bug fixes that bypass dev

**Versioning:**
- Semantic versioning: `MAJOR.MINOR.PATCH` (e.g., `0.1.0`, `0.2.0`, `1.0.0`)
- Pre-release tags: `0.1.0-alpha.1`, `0.1.0-beta.2`
- **Pre-1.0:** Frequent breaking changes expected, document in changelog
- **Post-1.0:** Strict semver, deprecation warnings before breaking changes

---

## Pre-Release Checklist

Before tagging a release:

### 1. Testing
- [ ] Manual gameplay test (place blocks, power network, visuals)
- [ ] Performance check (lag test with large networks)
- [ ] Compatibility test (load existing worlds from previous version)

### 2. Documentation
- [ ] Update `CHANGELOG.md` with all changes since last version
- [ ] Update README if features/installation changed
- [ ] Check that example code/screenshots are current

### 3. Code Hygiene
- [ ] Remove debug logging
- [ ] Check for commented-out code
- [ ] Verify mod version in `build.gradle.kts` matches tag

### 4. Assets
- [ ] Confirm all textures/models load correctly
- [ ] Check for missing or broken asset references

---

## GitHub Release Process

**Step-by-step:**

1. **Tag the release**
   ```bash
   git tag -a v0.1.0 -m "Release 0.1.0: Initial public release"
   git push origin v0.1.0
   ```

2. **Build the mod**
   ```bash
   ./gradlew build
   # Output: build/libs/Grid-v0.1.0.jar
   ```

3. **Create GitHub Release**
   - Go to **Releases** → **Draft a new release**
   - **Tag:** Select the version tag (e.g., `v0.1.0`)
   - **Title:** "Grid v0.1.0 - 4-State Logic for Hytale"
   - **Description:** Copy from `CHANGELOG.md`, add:
     - Screenshot/GIF of the mod in action
     - Installation instructions (link to docs)
     - Known issues (if any)
   - **Attach:** `Grid-v0.1.0.jar` from build output
   - **Pre-release checkbox:** Use for alpha/beta versions

4. **Publish**
   - Double-check everything, then hit **Publish release**
   - GitHub automatically creates a `.zip` and `.tar.gz` of the source

---

## Distribution Channels

### Primary: GitHub Releases
- **Why:** Direct control, no middleman, free hosting
- **Audience:** Developers, modders building on Grid
- **Frequency:** Every release (including pre-releases)
- **Format:** JAR file + source code archives

### Future: Hytale Official Platform
- **When available:** Mirror releases to Hytale's official mod distribution
- **Why:** Best visibility within the game, official support

---

## Packaging Standards

**Every release includes:**
1. **JAR file** — `Grid-v0.1.0.jar` (consistent naming)
2. **README.txt** — Plain text installation guide
3. **LICENSE** — Open source license (MIT/Apache 2.0 - TBD)
4. **CHANGELOG snippet** — What's new in this version

---

## Per-Release Communications

### Changelog Announcement
- Visual changelog (before/after screenshots if relevant)
- Post to GitHub Releases description
- Clear "what changed, why it matters" language

### Migration Guide (when needed)
- If breaking changes: document how to update existing worlds
- Code examples for API changes
- Deprecation warnings in advance when possible

---

## Questions to Decide

- **Open-source license:** MIT? Apache 2.0?
- **Repository visibility:** Public now or after first stable release?
- **Support level:** How much time per week for maintenance/issues?
