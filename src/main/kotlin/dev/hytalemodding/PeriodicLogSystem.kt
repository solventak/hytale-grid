package dev.hytalemodding

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.logging.Logger

class PeriodicLogSystem : EntityTickingSystem<EntityStore>() {

    private val logger = Logger.getLogger("PeriodicLogSystem")
    private var tickCount = 0

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuf: CommandBuffer<EntityStore>
    ) {
        tickCount++
        if (tickCount % 10 == 0) {
            logger.info("[PeriodicLogSystem] Tick $tickCount reached!")
        }
    }

    override fun getQuery(): Query<EntityStore> {
        return Query.any<EntityStore>()
    }
}
