#!/bin/bash
# Build and deploy mod to Hytale
# Usage: ./deploy.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODS_DIR="$HOME/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods"
ASSETS_DIR="$HOME/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Saves/qwerty/mods/alexkennedy.Grid"

cd "$SCRIPT_DIR"

echo "=== Building mod ==="
./gradlew clean build -x test

# Find the built jar
JAR=$(find build/libs -name "*.jar" ! -name "*-sources.jar" | head -1)
if [ -z "$JAR" ]; then
    echo "❌ No jar found in build/libs/"
    exit 1
fi
echo "✅ Built: $JAR"

echo ""
echo "=== Deploying jar ==="
mkdir -p "$MODS_DIR"
cp "$JAR" "$MODS_DIR/"
echo "✅ Copied to $MODS_DIR/$(basename "$JAR")"

echo ""
echo "=== Deploying assets ==="
mkdir -p "$ASSETS_DIR"
rsync -av --delete "$SCRIPT_DIR/mod-assets/" "$ASSETS_DIR/"
echo "✅ Assets synced to $ASSETS_DIR"

echo ""
echo "=== Deploy complete ==="
