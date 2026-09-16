package com.stratum.core.domain.session

import com.stratum.core.domain.content.HeroClassDefinition
import com.stratum.core.domain.world.BlockPos
import com.stratum.core.domain.world.Direction
import com.stratum.core.domain.world.WorldPoint

/**
 * Everything about the player the simulation needs. Immutable: the session
 * replaces it each tick, so a renderer holding an old copy sees a consistent
 * past rather than a half-updated present.
 */
data class PlayerState(
    val heroClassId: String,
    val position: WorldPoint,
    val facing: Direction = Direction.SOUTH,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val resource: Int = 50,
    val maxResource: Int = 50,
    val resourceName: String = "Focus",
    val toolTier: Int = 1,
    /** Block ids the player can place, in hotbar order. */
    val hotbar: List<String> = emptyList(),
    val selectedSlot: Int = 0,
    val inventory: Map<String, Int> = emptyMap(),
) {
    val blockPos: BlockPos get() = position.toBlockPos()

    /** The block the player is standing in; the one below is what holds them up. */
    val feet: BlockPos get() = blockPos

    val selectedBlockId: String?
        get() = hotbar.getOrNull(selectedSlot)

    val isAlive: Boolean get() = health > 0

    fun countOf(itemId: String): Int = inventory[itemId] ?: 0

    fun withItem(itemId: String, amount: Int = 1): PlayerState =
        copy(inventory = inventory + (itemId to (countOf(itemId) + amount)))

    /** Removes one of an item, returning null when the player has none to spend. */
    fun consuming(itemId: String): PlayerState? {
        val held = countOf(itemId)
        if (held <= 0) return null
        return copy(
            inventory = if (held == 1) inventory - itemId else inventory + (itemId to held - 1),
        )
    }

    fun selectingSlot(slot: Int): PlayerState =
        if (hotbar.isEmpty()) this else copy(selectedSlot = slot.coerceIn(0, hotbar.lastIndex))

    companion object {
        fun from(hero: HeroClassDefinition, spawn: WorldPoint): PlayerState = PlayerState(
            heroClassId = hero.id,
            position = spawn,
            health = hero.baseHealth,
            maxHealth = hero.baseHealth,
            resource = hero.baseResource,
            maxResource = hero.baseResource,
            resourceName = hero.resourceName,
            hotbar = hero.startingBlockIds,
            inventory = hero.startingBlockIds.associateWith { STARTING_STACK },
        )

        const val STARTING_STACK = 32
    }
}
