package com.stratum.engine.world

import com.stratum.core.domain.content.AssembledContent
import com.stratum.core.domain.actor.EnemyInstance
import com.stratum.core.domain.actor.Progression
import com.stratum.core.domain.actor.SkillDefinition
import com.stratum.core.domain.combat.DamageResult
import com.stratum.core.domain.content.BiomeDefinition
import com.stratum.core.domain.item.ItemInstance
import com.stratum.core.domain.item.ItemRarity
import com.stratum.core.domain.session.PlayerState
import com.stratum.core.domain.world.BlockPos
import com.stratum.core.domain.world.Chunk
import com.stratum.core.domain.world.Direction
import com.stratum.core.domain.world.World
import com.stratum.core.domain.world.WorldConfig
import com.stratum.core.domain.world.WorldPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

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

    /**
     * One RNG for the whole run, seeded from the world seed, so a session
     * replays identically given the same inputs.
     */
    private val random = Random(config.seed)

    private val combat = CombatResolver()
    private val lootRoller = LootRoller(content.weapons, content.affixes)
    private val director = EnemyDirector(streamingWorld, content.enemies)

    val world: World get() = streamingWorld

    private val hero = heroClassId
        ?.let { id -> content.heroClasses.firstOrNull { it.id == id } }
        ?: content.heroClasses.firstOrNull()

    /**
     * Writable inside the engine only. Feature modules see an immutable value,
     * so the UI cannot reach in and change the world behind the simulation.
     * Persistence and tests live in this module and legitimately need the seam.
     */
    var player: PlayerState internal set

    /** Effort spent on the block currently being mined, reset when the target changes. */
    private var miningTarget: BlockPos? = null
    private var miningProgress: Float = 0f

    var enemies: List<EnemyInstance> = emptyList()
        internal set

    /** Loot lying on the ground, waiting to be walked over. */
    var groundLoot: List<GroundLoot> = emptyList()
        internal set

    /** Events produced by the last tick, for the UI to draw and then forget. */
    var events: List<CombatEvent> = emptyList()
        private set

    init {
        streamingWorld.focusOn(SPAWN_CHUNK)
        val spawn = findSpawn()
        player = hero
            ?.let { PlayerState.from(it, spawn) }
            ?: PlayerState(heroClassId = "none", position = spawn)

        // Arm the class with its starting weapon so the first fight is winnable.
        // Common, so the first upgrade is an upgrade.
        val startingBase = hero?.startingWeaponId?.let(content::weapon)
            ?: content.weapons.minByOrNull { it.minItemLevel }
        if (startingBase != null) {
            player = player.equipping(
                lootRoller.craft(startingBase, itemLevel = 1, rarity = ItemRarity.COMMON, random = random),
            )
        }
        player = player.copy(health = player.maxHealthWithGear)
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

    // ---- the fight ------------------------------------------------------

    /**
     * Advances the world by one frame: spawns, moves and resolves monsters,
     * collects loot the player is standing on, and ticks cooldowns.
     *
     * Returns the events it produced rather than mutating a log, so a caller
     * that drops a frame loses the floating numbers and nothing else.
     */
    fun tick(deltaSeconds: Float): List<CombatEvent> {
        if (!player.isAlive) {
            events = emptyList()
            return events
        }

        val produced = mutableListOf<CombatEvent>()

        if (content.enemies.isNotEmpty()) {
            enemies = director.maintainPopulation(
                current = enemies,
                focus = player.position,
                biomeId = currentBiome.id,
                playerLevel = player.level,
                random = random,
            ).map { director.advance(it, player.position, deltaSeconds) }

            val incoming = combat.enemyAttacks(
                enemies = enemies,
                defender = player.combatStats,
                defenderPosition = player.position,
                cooldownFor = { it.stats.secondsBetweenAttacks },
                random = random,
            )
            enemies = incoming.enemies
            if (incoming.totalDamage > 0) {
                player = player.damaged(incoming.totalDamage)
                produced += CombatEvent.PlayerHurt(incoming.totalDamage, incoming.results)
                if (!player.isAlive) produced += CombatEvent.PlayerDied
            }
        }

        produced += collectLoot()

        player = player.copy(
            cooldowns = player.cooldowns.advanced(deltaSeconds),
            attackCooldown = (player.attackCooldown - deltaSeconds).coerceAtLeast(0f),
        )

        events = produced
        return produced
    }

    /** A basic swing. Refused while the weapon is still recovering. */
    fun attack(): AttackReport {
        if (player.attackCooldown > 0f) return AttackReport.NotReady
        val stats = player.combatStats
        val outcome = combat.playerAttack(
            attacker = stats,
            attackerPosition = player.position,
            facing = player.facing,
            enemies = enemies,
            damageTypeId = player.equippedWeapon?.damageTypeId ?: DEFAULT_DAMAGE_TYPE,
            random = random,
        )
        player = player.copy(attackCooldown = stats.secondsBetweenAttacks)
        return applyOutcome(outcome)
    }

    /** Casts one of the class's skills, spending resource and starting its cooldown. */
    fun castSkill(skillId: String): AttackReport {
        val skill = content.skill(skillId) ?: return AttackReport.UnknownSkill
        if (!player.cooldowns.isReady(skillId)) return AttackReport.OnCooldown
        if (player.resource < skill.resourceCost) return AttackReport.NotEnoughResource

        val outcome = combat.castSkill(
            attacker = player.combatStats,
            attackerPosition = player.position,
            facing = player.facing,
            enemies = enemies,
            skill = skill,
            random = random,
        )

        // Cost and cooldown are paid whether or not anything was standing there,
        // so a skill cannot be spammed to scout for targets for free.
        player = player.copy(
            resource = (player.resource - skill.resourceCost).coerceAtLeast(0),
            cooldowns = player.cooldowns.started(skill),
        )
        return applyOutcome(outcome, skill)
    }

    private fun applyOutcome(outcome: AttackOutcome, skill: SkillDefinition? = null): AttackReport {
        if (outcome !is AttackOutcome.Hits) return AttackReport.Missed

        val byId = outcome.hits.associateBy { it.enemyId }
        enemies = enemies.map { byId[it.instanceId]?.enemy ?: it }

        val healed = outcome.hits.sumOf { it.result.healedAttacker }
        if (healed > 0) player = player.healed(healed)

        val slain = enemies.filterNot { it.isAlive }
        if (slain.isNotEmpty()) {
            enemies = enemies.filter { it.isAlive }
            slain.forEach { dropLootFor(it) }
            awardExperience(slain.sumOf { it.experience })
        }

        return AttackReport.Landed(
            hits = outcome.hits,
            slain = slain,
            skill = skill,
        )
    }

    /**
     * Rolls a drop for a slain monster. Rank feeds the rarity roll, so an elite
     * is worth walking over to rather than just being tougher.
     */
    private fun dropLootFor(enemy: EnemyInstance): GroundLoot? {
        val definition = content.enemies.firstOrNull { it.id == enemy.definitionId }
        val chance = BASE_DROP_CHANCE + (definition?.bonusDropChance ?: 0f) + enemy.rank.extraAffixChance
        if (random.nextFloat() > chance) return null

        val depth = (config.seaLevel - enemy.blockPos.z).coerceAtLeast(0)
        val item = lootRoller.roll(
            itemLevel = Progression.itemLevelFor(player.level, depth),
            random = random,
            rarityBonus = enemy.rank.extraAffixChance,
        ) ?: return null

        val loot = GroundLoot(item, enemy.position)
        groundLoot = groundLoot + loot
        return loot
    }

    private fun awardExperience(amount: Int) {
        if (amount <= 0) return
        val result = Progression.apply(player.level, player.experience, amount)
        player = player.copy(level = result.level, experience = result.experience)
        if (result.leveledUp) {
            // A level restores the character, which is what makes pushing one
            // more fight at low health a real decision rather than a mistake.
            player = player.copy(
                health = player.maxHealthWithGear,
                resource = player.maxResource,
            )
        }
    }

    /** Picks up anything the player is standing on. */
    private fun collectLoot(): List<CombatEvent> {
        if (groundLoot.isEmpty()) return emptyList()

        val (reached, remaining) = groundLoot.partition {
            it.position.horizontalDistanceTo(player.position) <= PICKUP_RADIUS
        }
        if (reached.isEmpty()) return emptyList()

        groundLoot = remaining
        return reached.map { loot ->
            // Upgrades equip themselves. Making the player open a bag to feel a
            // drop is the fastest way to make loot stop feeling like a reward.
            val autoEquipped = player.isUpgrade(loot.item)
            player = if (autoEquipped) player.equipping(loot.item) else player.collecting(loot.item)
            CombatEvent.LootTaken(loot.item, autoEquipped)
        }
    }

    /**
     * Places a monster deliberately, for a scripted encounter or a shrine that
     * wakes something up. The director fills the world on its own; this is for
     * when the world should contain something specific.
     */
    fun spawn(
        definition: com.stratum.core.domain.actor.EnemyDefinition,
        position: WorldPoint,
    ): EnemyInstance {
        val enemy = director.instantiate(definition, position, player.level, random)
        enemies = enemies + enemy
        return enemy
    }

    /** Puts an item on the ground, for a chest or a quest reward. */
    fun dropLoot(item: ItemInstance, position: WorldPoint) {
        groundLoot = groundLoot + GroundLoot(item, position)
    }

    /** Environmental damage: a fall, a trap, a hazard block. */
    fun hurtPlayer(amount: Int): Boolean {
        if (amount <= 0) return player.isAlive
        player = player.damaged(amount)
        return player.isAlive
    }

    /** Skills the class has, resolved against the loaded packs. */
    val skills: List<SkillDefinition> get() = player.skillIds.mapNotNull(content::skill)

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
        enemies = enemies,
        groundLoot = groundLoot,
        skills = skills,
    )

    companion object {
        /** How far the player climbs without a jump. */
        const val STEP_UP = 1
        const val SPAWN_SEARCH_RADIUS = 12
        const val PICKUP_RADIUS = 1.6f
        const val BASE_DROP_CHANCE = 0.35f
        const val DEFAULT_DAMAGE_TYPE = "stratum:physical"
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
    val enemies: List<EnemyInstance> = emptyList(),
    val groundLoot: List<GroundLoot> = emptyList(),
    val skills: List<SkillDefinition> = emptyList(),
)

/** An item lying in the world. */
data class GroundLoot(val item: ItemInstance, val position: WorldPoint)

/** What a swing did. */
sealed interface AttackReport {
    data class Landed(
        val hits: List<EnemyHit>,
        val slain: List<EnemyInstance>,
        val skill: SkillDefinition? = null,
    ) : AttackReport {
        val totalDamage: Int get() = hits.sumOf { it.result.amount }
    }

    data object Missed : AttackReport
    data object NotReady : AttackReport
    data object OnCooldown : AttackReport
    data object NotEnoughResource : AttackReport
    data object UnknownSkill : AttackReport
}

/** Something worth showing the player. Produced per tick and not retained. */
sealed interface CombatEvent {
    data class PlayerHurt(val amount: Int, val results: List<DamageResult>) : CombatEvent
    data class LootTaken(val item: ItemInstance, val equipped: Boolean) : CombatEvent
    data object PlayerDied : CombatEvent
}
