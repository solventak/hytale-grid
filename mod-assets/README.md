# mod-assets/

Asset working directory for Grid development.

## Structure

- `Common/Blocks/` — Block model files (.bbmodel, etc.)
- `Common/BlockTextures/` — Block texture source files
- `Common/Icons/` — Item icon source files and variants

## Single Source of Truth

**Item and block definitions (JSON) live in `src/main/resources/Server/`** — this is what Gradle builds into the mod JAR.

This directory (`mod-assets/`) is for **asset creation only** — textures, models, and icon variants. Once assets are finalized, they're referenced from the JSON definitions in `src/main/resources/`.

## Workflow

1. Create/edit assets here (textures, models, icons)
2. Export/copy final assets to `src/main/resources/Common/`
3. Update item/block JSON in `src/main/resources/Server/` to reference them
4. Build with `./gradlew build`

Do NOT duplicate JSON definitions here — they belong in `src/main/resources/` only.
