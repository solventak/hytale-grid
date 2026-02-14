package dev.hytalemodding.newnet

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import dev.hytalemodding.GridPlugin
import dev.hytalemodding.newnet.shared.State4

/**
 * EntityStore EventSystem that handles player right-click interactions with Lever blocks.
 * 
 * Triggers when:
 * - A player right-clicks a block (via UseBlockEvent.Post on EntityStore)
 * - The block has a Lever component
 * 
 * Responsibilities:
 * 1. **Toggle lever state**: Flip lever.isOn between true/false
 * 2. **Update PowerSource output**: Set driveState to ONE (on) or ZERO (off)
 * 3. **Mark visual dirty**: Add position to visualDirtyPositions for VisualStateSystem
 * 4. **Queue topology event**: Add PLACED event to trigger network re-evaluation
 * 
 * Note: This runs on EntityStore (player entity system), not ChunkStore.
 * Cross-store access is used to:
 * - Read/write Lever and PowerSource components via HytaleWorldAccess
 * - Queue events on ChunkStore StateChangeEventQueue resource
 */
class LeverInteractionSystem : EntityEventSystem<EntityStore, UseBlockEvent.Post>(UseBlockEvent.Post::class.java) {
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        cmdBuf: CommandBuffer<EntityStore>,
        event: UseBlockEvent.Post
    ) {
        val world = cmdBuf.externalData.world
        val pos = event.targetBlock
        val worldAccess: WorldAccess = HytaleWorldAccess(world)

        // Check if this block has a Lever component
        val lever = worldAccess.getComponent(pos, GridPlugin.leverComponentType) ?: return
        val powerSource = worldAccess.getComponent(pos, GridPlugin.powerSourceComponentType) ?: return

        // Toggle the lever state
        lever.isOn = !lever.isOn

        // Update PowerSource driveState based on new toggle state
        // OFF → ONE, ON → ZERO (inverted because PowerSource is an inverting gate)
        powerSource.driveState = if (lever.isOn) State4.ZERO else State4.ONE
        powerSource.lastDriveState = powerSource.driveState

        // Queue topology change event to re-evaluate network
        val queue = world.chunkStore.store.getResource(GridPlugin.stateChangeQueueType)
        queue.changes.add(StateChangeEvent(pos, StateChangeKind.PLACED))
        
        // Mark visual state dirty for VisualStateSystem to update appearance
        queue.visualDirtyPositions.add(pos)
        
    }

    override fun getQuery(): Query<EntityStore> = Query.and(Player.getComponentType())
}
