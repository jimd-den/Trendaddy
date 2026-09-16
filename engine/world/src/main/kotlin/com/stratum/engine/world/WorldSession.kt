package com.stratum.engine.world

import com.stratum.core.domain.content.AssembledContent
import com.stratum.core.domain.content.BiomeDefinition
import com.stratum.core.domain.session.PlayerState
import com.stratum.core.domain.world.BlockPos
import com.stratum.core.domain.world.Chunk
import com.stratum.core.domain.world.Direction
import com.stratum.core.domain.world.World
import com.stratum.core.domain.world.WorldConfig
import com.stratum.core.domain.world.WorldPoint
import kotlin.math.abs
import kotlin.math.floor

/**
 * One run: a streaming world, a player, and the rules that connect them.
 *
 * The session owns all mutable game state and exposes it only as immutable
 * snapshots, so the UI layer can never reach in and change the world behind the
 * simulation's back.
 */
class WorldSession(
    val content: AssembledContent,
    val config: WorldConfig,
    heroClassId: String? = null,
) {
    private val generator = LayeredTerrainGenerator(config, content.biomes)
    private val streamingWorld = StreamingWorld(content.registry, generator, config)
    private val interaction = BlockInteractionSystem(streamingWorld)

    val world: World get() = streamingWorld

    private val hero = heroClassId
        ?.let { id -> content.heroClasses.firstOrNull { it.id == id } }
        ?: content.heroClasses.firstOrNull()

    var player: PlayerState private set

    /** Effort spent on the block currently being mined, reset when the target changes. */
    private var miningTarget: BlockPos? = null
    private var miningProgress: Float = 0f

    init {
        streamingWorld.focusOn(SPAWN_CHUNK)
        val spawn = findSpawn()
        player = hero
            ?.let { PlayerState.from(it, spawn) }
            ?: PlayerState(heroClassId = "none", position = spawn)
    }

    /**
     * Drops the player onto the surface at the world origin. Searches outward if
     * the origin column happens to be unsuitable, so a spawn is never inside rock.
     */
    private fun findSpawn(): WorldPoint {
        for (radius in 0..SPAWN_SEARCH_RADIUS) {
            for (y in -radius..radius) {
                for (x in -radius..radius) {
                    if (maxOf(abs(x), abs(y)) != radius) continue
                    val surface = streamingWorld.surfaceAt(x, y)
                    if (surface in 1 until Chunk.HEIGHT - 2) {
                        return WorldPoint(x + 0.5f, y + 0.5f, (surface + 1).toFloat())
                    }
                }
            }
        }
        return WorldPoint(0.5f, 0.5f, (config.seaLevel + 1).toFloat())
    }

    // ---- movement --------------------------------------------------------

    /**
     * Moves the player horizontally, resolving terrain as it goes.
     *
     * A step up of one block is climbed automatically, since requiring a jump
     * for every furrow in the terrain makes an isometric world miserable to walk.
     * Anything taller blocks, and unsupported ground drops the player.
     */
    fun move(dx: Float, dy: Float): MoveOutcome {
        if (dx == 0f && dy == 0f) return MoveOutcome(player, moved = false, blocked = false)

        val facing = facingFor(dx, dy)
        val target = player.position.translated(dx, dy, 0f)
        val targetColumn = BlockPos(floor(target.x).toInt(), floor(target.y).toInt(), player.blockPos.z)

        val resolved = resolveStandingPosition(targetColumn, target)
        if (resolved == null) {
            player = player.copy(facing = facing)
            return MoveOutcome(player, moved = false, blocked = true)
        }

        player = player.copy(position = resolved, facing = facing)
        streamingWorld.focusOn(player.blockPos)
        return MoveOutcome(player, moved = true, blocked = false)
    }

    /**
     * Where the player ends up in the target column, or null if the way is blocked.
     * Climbs at most [STEP_UP] and falls any distance.
     *
     * The player stands on top of the highest solid block at or below the reach
     * of a step. Searching downward from there and stopping at the first solid
     * block is what stops a walk from tunnelling through a wall into whatever
     * cavity lies behind it.
     */
    private fun resolveStandingPosition(column: BlockPos, target: WorldPoint): WorldPoint? {
        val currentZ = player.blockPos.z

        var highestSolid = -1
        for (z in (currentZ + STEP_UP) downTo 0) {
            if (streamingWorld.isSolid(BlockPos(column.x, column.y, z))) {
                highestSolid = z
                break
            }
        }

        val standingZ = highestSolid + 1
        // Anything taller than a single step is a wall. Falls are unrestricted.
        if (standingZ - currentZ > STEP_UP) return null
        if (standingZ >= Chunk.HEIGHT) return null
        return WorldPoint(target.x, target.y, standingZ.toFloat())
    }

    private fun facingFor(dx: Float, dy: Float): Direction = when {
        abs(dx) >= abs(dy) && dx > 0 -> Direction.EAST
        abs(dx) >= abs(dy) -> Direction.WEST
        dy > 0 -> Direction.SOUTH
        else -> Direction.NORTH
    }

    // ---- interaction -----------------------------------------------------

    /**
     * Applies mining effort to a block. Progress is kept per target here rather
     * than by the caller, so releasing and re-pressing on the same block does not
     * silently restart the dig.
     */
    fun mine(target: BlockPos, deltaSeconds: Float): MineResult {
        if (target != miningTarget) {
            miningTarget = target
            miningProgress = 0f
        }

        val result = interaction.mine(
            MineRequest(
                origin = player.blockPos,
                target = target,
                toolTier = player.toolTier,
                deltaSeconds = deltaSeconds,
                progressSoFar = miningProgress,
            ),
        )

        when (result) {
            is MineResult.InProgress -> miningProgress = result.progress
            is MineResult.Broken -> {
                miningTarget = null
                miningProgress = 0f
                player = player.withItem(result.drop)
                if (result.drop !in player.hotbar && content.registry.contains(result.drop)) {
                    player = player.copy(hotbar = player.hotbar + result.drop)
                }
                settlePlayer()
            }
            is MineResult.Rejected -> {
                miningTarget = null
                miningProgress = 0f
            }
        }
        return result
    }

    fun cancelMining() {
        miningTarget = null
        miningProgress = 0f
    }

    val miningFraction: Float
        get() {
            val target = miningTarget ?: return 0f
            val hardness = world.blockAt(target).hardness
            return if (hardness <= 0f) 0f else (miningProgress / hardness).coerceIn(0f, 1f)
        }

    /** Places the selected hotbar block, spending one from the inventory. */
    fun place(target: BlockPos): PlaceResult {
        val blockId = player.selectedBlockId
            ?: return PlaceResult.Rejected(PlaceRejection.UNKNOWN_BLOCK)
        val spent = player.consuming(blockId)
            ?: return PlaceResult.Rejected(PlaceRejection.UNKNOWN_BLOCK)

        val result = interaction.place(
            PlaceRequest(
                origin = player.blockPos,
                target = target,
                blockId = blockId,
                occupiedByActors = setOf(player.feet, player.feet.above()),
            ),
        )
        if (result is PlaceResult.Placed) {
            player = spent
        }
        return result
    }

    fun selectSlot(slot: Int) {
        player = player.selectingSlot(slot)
    }

    /** Drops the player if mining removed the ground from under them. */
    private fun settlePlayer() {
        val feet = player.blockPos
        if (feet.z <= 0) return
        if (streamingWorld.isSolid(feet.below())) return

        var z = feet.z
        while (z > 0 && !streamingWorld.isSolid(BlockPos(feet.x, feet.y, z - 1))) {
            z--
        }
        player = player.copy(position = player.position.copy(z = z.toFloat()))
    }

    /**
     * The biome under the player's feet. Asked of the generator rather than
     * stored on the chunk, because it is the same deterministic function of
     * position that produced the terrain in the first place.
     */
    val currentBiome: BiomeDefinition
        get() = generator.biomeAt(player.blockPos.x, player.blockPos.y)

    /** Immutable view for the UI layer. */
    fun snapshot(): SessionSnapshot = SessionSnapshot(
        player = player,
        focus = streamingWorld.focus,
        biome = currentBiome,
        miningTarget = miningTarget,
        miningFraction = miningFraction,
        worldRevision = streamingWorld.loadedChunks.sumOf { it.revision },
    )

    companion object {
        /** How far the player climbs without a jump. */
        const val STEP_UP = 1
        const val SPAWN_SEARCH_RADIUS = 12
        private val SPAWN_CHUNK = com.stratum.core.domain.world.ChunkPos(0, 0)
    }
}

data class MoveOutcome(val player: PlayerState, val moved: Boolean, val blocked: Boolean)

data class SessionSnapshot(
    val player: PlayerState,
    val focus: com.stratum.core.domain.world.ChunkPos,
    val biome: BiomeDefinition,
    val miningTarget: BlockPos?,
    val miningFraction: Float,
    /** Changes when any loaded chunk changes, so the renderer knows to redraw. */
    val worldRevision: Int,
)
