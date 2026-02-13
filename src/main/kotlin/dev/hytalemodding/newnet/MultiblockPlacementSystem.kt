package dev.hytalemodding.newnet

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

/**
 * Shared placement and validation system for multiblock structures.
 *
 * ## Placement Flow
 * 1. When a multiblock-capable block is placed, call `tryFormStructure()`
 * 2. System scans neighbors and tries to match against all registered shapes
 * 3. If a valid shape match is found, all blocks in the structure are updated:
 *    - `structurePositions` set to all block positions
 *    - `isComplete` set to true
 *    - Face roles (`inputFaces`, `outputFaces`, `internalFaces`) computed from shape
 *    - `shapeId` set to matched shape's ID
 * 4. If no match, block remains incomplete (may self-destruct if invalid neighbors)
 *
 * ## Validation Rules (incomplete blocks)
 * Incomplete multiblock parts may self-destruct if they have incompatible neighbors.
 * This prevents half-built structures from interfering with the network.
 *
 * ## Destruction Handling
 * When a block in a complete structure is destroyed, all other blocks in the structure
 * are marked incomplete. They may then self-destruct if they have invalid neighbors.
 */

/**
 * Registry of multiblock shapes, keyed by shape ID.
 */
private val shapeRegistry = mutableMapOf<String, MultiblockShape>()

/**
 * Register a shape template for placement matching.
 */
fun registerShape(shape: MultiblockShape) {
    shapeRegistry[shape.id] = shape
}

/**
 * Get all registered shapes.
 */
fun getRegisteredShapes(): List<MultiblockShape> {
    return shapeRegistry.values.toList()
}

/**
 * Attempt to form a multiblock structure starting from a newly placed block.
 *
 * Scans neighbors and tries to match against all registered shapes (all orientations).
 * If a match is found, updates all blocks in the structure.
 *
 * @param pos Position of the newly placed block
 * @param componentType Component type for this multiblock (e.g., Mux2Part, DecoderPart)
 * @param worldAccess World access
 * @return true if structure was formed (complete), false if block remains incomplete
 */
fun <T : MultiblockPart> tryFormStructure(
    pos: Vector3i,
    componentType: ComponentType<ChunkStore, T>,
    worldAccess: WorldAccess
): Boolean {
    val placedBlock = worldAccess.getComponent(pos, componentType) ?: return false

    // Special case: if this block is already part of a complete structure (e.g., world load),
    // validate that structure still exists
    if (placedBlock.isComplete && placedBlock.structurePositions.isNotEmpty()) {
        if (validateExistingStructure(pos, placedBlock, componentType, worldAccess)) {
            return true
        }
        // Structure is no longer valid, mark incomplete and continue trying to form new one
        placedBlock.isComplete = false
        placedBlock.structurePositions = emptyList()
    }

    // Try to match against all registered shapes (all orientations)
    for (shape in shapeRegistry.values) {
        for (oriented in shape.getAllOrientations()) {
            if (tryMatchShape(pos, oriented, componentType, worldAccess)) {
                return true
            }
        }
    }

    // No match found — block remains incomplete
    placedBlock.isComplete = false
    placedBlock.structurePositions = emptyList()
    return false
}

/**
 * Try to match a specific oriented shape starting from a given position.
 *
 * Checks if all required block positions are occupied by the correct component type.
 * If match found, updates all blocks with structure info.
 *
 * @return true if match found and structure formed
 */
private fun <T : MultiblockPart> tryMatchShape(
    startPos: Vector3i,
    oriented: OrientedShape,
    componentType: ComponentType<ChunkStore, T>,
    worldAccess: WorldAccess
): Boolean {
    val shape = oriented.shape

    // Compute world positions for all blocks in this shape
    // Assume startPos corresponds to the first position in the shape (arbitrary choice)
    // We need to try the startPos as each possible position in the shape
    for ((originIndex, originRelPos) in shape.positions.withIndex()) {
        // Translation: startPos should map to originRelPos
        val translation = Vector3i(
            startPos.x - originRelPos.x,
            startPos.y - originRelPos.y,
            startPos.z - originRelPos.z
        )

        // Check if all positions in the shape are occupied by matching blocks
        val worldPositions = shape.positions.map { relPos ->
            Vector3i(
                relPos.x + translation.x,
                relPos.y + translation.y,
                relPos.z + translation.z
            )
        }

        // Verify all positions exist and have the correct component
        val allPresent = worldPositions.all { wp ->
            worldAccess.getComponent(wp, componentType) != null
        }

        if (!allPresent) continue

        // Match found! Update all blocks in the structure
        formStructure(worldPositions, shape, componentType, worldAccess)
        return true
    }

    return false
}

/**
 * Form a structure by updating all blocks with structure info.
 */
private fun <T : MultiblockPart> formStructure(
    worldPositions: List<Vector3i>,
    shape: MultiblockShape,
    componentType: ComponentType<ChunkStore, T>,
    worldAccess: WorldAccess
) {
    // Compute face roles for each block
    val internalFacesMap = shape.computeInternalFaces()

    for ((index, worldPos) in worldPositions.withIndex()) {
        val block = worldAccess.getComponent(worldPos, componentType) ?: continue
        val relPos = shape.positions[index]

        // Compute face roles for this specific block
        val inputFacesForBlock = shape.inputFaces
            .filter { (key, _) -> key.first == relPos }
            .map { (key, _) -> key.second }
            .toSet()

        val outputFacesForBlock = shape.outputFaces
            .filter { (key, _) -> key.first == relPos }
            .map { (key, _) -> key.second }
            .toSet()

        val internalFacesForBlock = internalFacesMap
            .filter { (key, _) -> key.first == relPos }
            .map { (key, _) -> key.second }
            .toSet()

        // Update block
        block.structurePositions = worldPositions
        block.isComplete = true
        block.inputFaces = inputFacesForBlock
        block.outputFaces = outputFacesForBlock
        block.internalFaces = internalFacesForBlock
        block.shapeId = shape.id

        // Update PowerConnectable mask: only output faces are connectable
        val connectable = worldAccess.getComponent(worldPos, ExamplePlugin.powerConnectableComponentType)
        if (connectable != null) {
            // Build mask: only output faces enabled
            var mask = 0
            for (face in outputFacesForBlock) {
                mask = mask or (1 shl face)
            }
            connectable.facesMask = mask
        }
    }
}

/**
 * Validate that an existing structure (loaded from save) is still intact.
 */
private fun <T : MultiblockPart> validateExistingStructure(
    pos: Vector3i,
    block: T,
    componentType: ComponentType<ChunkStore, T>,
    worldAccess: WorldAccess
): Boolean {
    // Check that all positions in structurePositions still have matching blocks
    for (structurePos in block.structurePositions) {
        val otherBlock = worldAccess.getComponent(structurePos, componentType)
        if (otherBlock == null || !otherBlock.isComplete) {
            return false
        }
        // Check that other block references the same structure
        if (otherBlock.structurePositions != block.structurePositions) {
            return false
        }
    }
    return true
}

/**
 * Check if an incomplete multiblock block should self-destruct.
 *
 * An incomplete block self-destructs if it has PowerConnectable or InputPort neighbors
 * that aren't part of a potential structure formation.
 *
 * @return true if the block should be destroyed
 */
fun <T : MultiblockPart> shouldDestroyIncompleteBlock(
    pos: Vector3i,
    componentType: ComponentType<ChunkStore, T>,
    worldAccess: WorldAccess
): Boolean {
    // Check all 6 neighbors
    for (face in 0..5) {
        val (npos, _) = neighborOfFace(pos, face)

        // If neighbor is same multiblock type, check if it's complete
        val neighborBlock = worldAccess.getComponent(npos, componentType)
        if (neighborBlock != null) {
            if (neighborBlock.isComplete) {
                // Complete structure next to incomplete block — destroy incomplete
                return true
            }
            continue // Incomplete neighbor is fine (potential future structure)
        }

        // If neighbor is any PowerConnectable → destroy
        if (worldAccess.getComponent(npos, ExamplePlugin.powerConnectableComponentType) != null) {
            return true
        }

        // If neighbor is InputPort → destroy
        if (worldAccess.getComponent(npos, ExamplePlugin.inputPortComponentType) != null) {
            return true
        }
    }

    return false
}

/**
 * Handle destruction of a block in a complete structure.
 *
 * Marks all other blocks in the structure as incomplete. They may then self-destruct
 * if they have invalid neighbors.
 */
fun <T : MultiblockPart> handleBlockDestroyed(
    destroyedPos: Vector3i,
    componentType: ComponentType<ChunkStore, T>,
    worldAccess: WorldAccess
) {
    val destroyedBlock = worldAccess.getComponent(destroyedPos, componentType) ?: return

    if (!destroyedBlock.isComplete) return

    // Mark all other blocks in the structure as incomplete
    for (structurePos in destroyedBlock.structurePositions) {
        if (structurePos == destroyedPos) continue

        val block = worldAccess.getComponent(structurePos, componentType) ?: continue
        block.isComplete = false
        block.structurePositions = emptyList()
        block.inputFaces = emptySet()
        block.outputFaces = emptySet()
        block.internalFaces = emptySet()
        block.shapeId = ""

        // Reset PowerConnectable mask to default (all faces)
        val connectable = worldAccess.getComponent(structurePos, ExamplePlugin.powerConnectableComponentType)
        if (connectable != null) {
            connectable.facesMask = 0x3F // All faces enabled
        }

        // Check if this now-incomplete block should also be destroyed
        if (shouldDestroyIncompleteBlock(structurePos, componentType, worldAccess)) {
            if (worldAccess is HytaleWorldAccess) {
                worldAccess.world.execute {
                    worldAccess.world.setBlock(structurePos.x, structurePos.y, structurePos.z, "Empty")
                }
            }
        }
    }
}
