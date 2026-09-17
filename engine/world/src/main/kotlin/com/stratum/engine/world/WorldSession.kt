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
import com.stratum.core.domain.sprite.AnimationPlayback
import com.stratum.core.domain.sprite.AnimationSelector
import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.world.BlockPos
import com.stratum.core.domain.world.Chunk
import com.stratum.core.domain.world.Direction
import com.stratum.core.domain.world.World
import com.stratum.core.domain.world.WorldConfig
import com.stratum.core.domain.world.WorldPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
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

    private val feedbackLog = FeedbackLog()
    private val hitFlashes = HitFlashes()

    /** Short-lived visuals: damage numbers, misses, level-ups. */
    val feedback: List<FeedbackMark> get() = feedbackLog.active

    /**
     * Animation clocks, per actor id. Kept here rather than in the renderer so
     * two monsters in the same state stay independent and a recomposition does
     * not restart every walk cycle.
     */
    private val playbacks = HashMap<String, AnimationPlayback>()

    /** Seconds left of an actor's attack animation, so a swing is not instant. */
    private val attackHolds = HashMap<String, Float>()

    fun animationFor(actorId: String): AnimationPlayback =
        playbacks[actorId] ?: AnimationPlayback()

    /** How lit an actor is from a recent hit, 0..1. */
    fun flashFor(actorId: String): Float = hitFlashes.intensity(actorId)
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
     * The direction the player is being pushed, from the joystick. Length 0..1,
     * so a half-deflected stick walks at half speed.
     *
     * Held as intent rather than applied immediately: movement is integrated in
     * [tick] so that speed is measured in blocks per second and does not depend
     * on how often the UI happens to call in.
     */
    private var moveInput: WorldPoint = WorldPoint.ZERO

    /** Seconds left of the current dodge roll, and the direction it is going. */
    private var rollRemaining: Float = 0f
    private var rollDirection: WorldPoint = WorldPoint.ZERO

    /** Seconds left of invulnerability. Longer than nothing, shorter than the roll. */
    private var invulnerableFor: Float = 0f

    /** Seconds until another roll is allowed. */
    private var rollCooldown: Float = 0f

    val isRolling: Boolean get() = rollRemaining > 0f

    val isInvulnerable: Boolean get() = invulnerableFor > 0f

    val rollCooldownFraction: Float
        get() = (rollCooldown / ROLL_COOLDOWN).coerceIn(0f, 1f)

    /**
     * Sets the direction the player wants to go, as a vector from the joystick.
     * Zero stops them.
     */
    fun setMoveInput(dx: Float, dy: Float) {
        val length = sqrt(dx * dx + dy * dy)
        moveInput = if (length <= INPUT_DEADZONE) {
            WorldPoint.ZERO
        } else {
            // Clamp to the unit circle so a diagonal is not faster than a
            // cardinal, which is the classic bug with square joystick input.
            val scale = (if (length > 1f) 1f / length else 1f)
            WorldPoint(dx * scale, dy * scale, 0f)
        }
        if (moveInput != WorldPoint.ZERO) {
            player = player.copy(facing = facingFor(moveInput.x, moveInput.y))
        }
    }

    /**
     * Starts a dodge roll: a burst of speed in the current direction with a
     * window of invulnerability.
     *
     * Rolls in the facing direction when the stick is neutral, so a dodge is
     * always available rather than requiring the player to be already moving.
     */
    fun dodge(): DodgeResult {
        // Most specific reason first: a roll always sets the cooldown, so
        // checking cooldown first would report every mid-roll press as
        // "on cooldown" and hide what is actually happening.
        if (!player.isAlive) return DodgeResult.Rejected
        if (isRolling) return DodgeResult.AlreadyRolling
        if (rollCooldown > 0f) return DodgeResult.OnCooldown

        val direction = if (moveInput == WorldPoint.ZERO) {
            WorldPoint(player.facing.dx.toFloat(), player.facing.dy.toFloat(), 0f)
        } else {
            moveInput
        }

        rollDirection = direction
        rollRemaining = ROLL_DURATION
        invulnerableFor = ROLL_INVULNERABILITY
        rollCooldown = ROLL_COOLDOWN
        return DodgeResult.Rolling
    }

    /**
     * Integrates movement for one frame.
     *
     * Axes are resolved separately so that walking into a wall at an angle
     * slides along it instead of stopping dead. Sticking on geometry is the
     * single most felt movement bug in an isometric game, because the player
     * cannot see the wall they are caught on.
     */
    private fun advanceMovement(deltaSeconds: Float) {
        rollCooldown = (rollCooldown - deltaSeconds).coerceAtLeast(0f)
        invulnerableFor = (invulnerableFor - deltaSeconds).coerceAtLeast(0f)

        val velocity: WorldPoint
        if (rollRemaining > 0f) {
            rollRemaining = (rollRemaining - deltaSeconds).coerceAtLeast(0f)
            velocity = WorldPoint(rollDirection.x * ROLL_SPEED, rollDirection.y * ROLL_SPEED, 0f)
        } else if (moveInput != WorldPoint.ZERO) {
            velocity = WorldPoint(moveInput.x * WALK_SPEED, moveInput.y * WALK_SPEED, 0f)
        } else {
            settlePlayer()
            return
        }

        val stepX = velocity.x * deltaSeconds
        val stepY = velocity.y * deltaSeconds

        var position = player.position
        position = tryAxis(position, stepX, 0f) ?: position
        position = tryAxis(position, 0f, stepY) ?: position

        player = player.copy(position = position)
        streamingWorld.focusOn(player.blockPos)
        settlePlayer()
    }

    /**
     * Attempts one axis of movement, returning the new position or null when
     * the way is blocked. Climbing a single step is free; anything taller is a
     * wall.
     */
    private fun tryAxis(from: WorldPoint, dx: Float, dy: Float): WorldPoint? {
        if (dx == 0f && dy == 0f) return from

        val target = from.translated(dx, dy, 0f)
        val column = BlockPos(floor(target.x).toInt(), floor(target.y).toInt(), 0)
        val currentZ = from.toBlockPos().z

        var highestSolid = -1
        for (z in (currentZ + STEP_UP) downTo 0) {
            if (streamingWorld.isSolid(BlockPos(column.x, column.y, z))) {
                highestSolid = z
                break
            }
        }

        val standingZ = highestSolid + 1
        if (standingZ - currentZ > STEP_UP) return null
        if (standingZ >= Chunk.HEIGHT) return null
        return WorldPoint(target.x, target.y, standingZ.toFloat())
    }

    /**
     * Single-shot movement, kept for tests and for anything that wants to nudge
     * the player a fixed distance rather than hold a direction.
     */
    fun move(dx: Float, dy: Float): MoveOutcome {
        if (dx == 0f && dy == 0f) return MoveOutcome(player, moved = false, blocked = false)

        val facing = facingFor(dx, dy)
        player = player.copy(facing = facing)

        var position = player.position
        val afterX = tryAxis(position, dx, 0f)
        val afterY = tryAxis(afterX ?: position, 0f, dy)
        val resolved = afterY ?: afterX

        if (resolved == null || resolved == player.position) {
            return MoveOutcome(player, moved = false, blocked = true)
        }

        player = player.copy(position = resolved)
        streamingWorld.focusOn(player.blockPos)
        settlePlayer()
        return MoveOutcome(player, moved = true, blocked = false)
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

        feedbackLog.advance(deltaSeconds)
        hitFlashes.advance(deltaSeconds)
        advanceAttackHolds(deltaSeconds)

        // Movement first: a roll should be able to carry the player out of
        // reach before the monsters around them take their swing.
        advanceMovement(deltaSeconds)

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
            // Whoever swung is mid-attack for a beat, so the animation reads.
            incoming.enemies.filter { it.attackCooldown > 0f && it.isAlive }
                .forEach { attackHolds[it.instanceId] = ATTACK_ANIMATION_HOLD }
            enemies = incoming.enemies
            if (incoming.totalDamage > 0) {
                if (isInvulnerable) {
                    // The swing happened and went on cooldown; it simply did not
                    // land. Reporting it is what makes a well-timed roll legible.
                    produced += CombatEvent.PlayerDodged(incoming.totalDamage)
                    feedbackLog.add(
                        kind = FeedbackKind.DODGED,
                        text = "DODGED",
                        origin = player.position,
                        color = FEEDBACK_DODGE,
                        emphasis = 1.1f,
                    )
                } else {
                    player = player.damaged(incoming.totalDamage)
                    produced += CombatEvent.PlayerHurt(incoming.totalDamage, incoming.results)
                    feedbackLog.add(
                        kind = FeedbackKind.DAMAGE_TAKEN,
                        text = "-${incoming.totalDamage}",
                        origin = player.position,
                        color = FEEDBACK_HURT,
                        emphasis = 1.2f,
                    )
                    hitFlashes.strike(PLAYER_ACTOR_ID)
                    if (!player.isAlive) {
                        produced += CombatEvent.PlayerDied
                        feedbackLog.add(
                            kind = FeedbackKind.KILL,
                            text = "FALLEN",
                            origin = player.position,
                            color = FEEDBACK_HURT,
                            emphasis = 1.8f,
                            lifetime = 2.5f,
                        )
                    }
                }
            }
        }

        produced += collectLoot()
        advanceAnimations(deltaSeconds)

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
        attackHolds[PLAYER_ACTOR_ID] = ATTACK_ANIMATION_HOLD
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
        attackHolds[PLAYER_ACTOR_ID] = ATTACK_ANIMATION_HOLD
        return applyOutcome(outcome, skill)
    }

    private fun applyOutcome(outcome: AttackOutcome, skill: SkillDefinition? = null): AttackReport {
        if (outcome !is AttackOutcome.Hits) return AttackReport.Missed

        val byId = outcome.hits.associateBy { it.enemyId }
        enemies = enemies.map { byId[it.instanceId]?.enemy ?: it }

        outcome.hits.forEach { hit ->
            val typeColor = content.damageType(hit.result.damageTypeId).color
            when {
                hit.result.wasBlocked -> feedbackLog.add(
                    kind = FeedbackKind.BLOCKED,
                    text = "BLOCKED",
                    origin = hit.enemy.position,
                    color = FEEDBACK_BLOCKED,
                    emphasis = 0.85f,
                )
                hit.result.wasCritical -> feedbackLog.add(
                    kind = FeedbackKind.CRITICAL,
                    text = "${hit.result.amount}!",
                    origin = hit.enemy.position,
                    color = typeColor,
                    // Crits read as bigger and last longer. A critical the
                    // player cannot distinguish from a normal hit is a stat
                    // they have no reason to build for.
                    emphasis = 1.7f,
                    lifetime = 1.2f,
                )
                else -> feedbackLog.add(
                    kind = FeedbackKind.DAMAGE_DEALT,
                    text = hit.result.amount.toString(),
                    origin = hit.enemy.position,
                    color = typeColor,
                )
            }
            hitFlashes.strike(hit.enemyId)
        }

        val healed = outcome.hits.sumOf { it.result.healedAttacker }
        if (healed > 0) {
            player = player.healed(healed)
            feedbackLog.add(
                kind = FeedbackKind.HEAL,
                text = "+$healed",
                origin = player.position,
                color = FEEDBACK_HEAL,
            )
        }

        val slain = enemies.filterNot { it.isAlive }
        if (slain.isNotEmpty()) {
            enemies = enemies.filter { it.isAlive }
            slain.forEach { enemy ->
                hitFlashes.forget(enemy.instanceId)
                dropLootFor(enemy)
            }
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
            feedbackLog.add(
                kind = FeedbackKind.LEVEL_UP,
                text = "LEVEL ${result.level}",
                origin = player.position,
                color = FEEDBACK_LEVEL,
                emphasis = 1.9f,
                lifetime = 1.8f,
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
            feedbackLog.add(
                kind = FeedbackKind.LOOT,
                text = loot.item.name,
                origin = player.position,
                color = content.rarityColor(loot.item.rarity),
                emphasis = if (autoEquipped) 1.3f else 1f,
                lifetime = 1.4f,
            )
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

    private fun advanceAttackHolds(deltaSeconds: Float) {
        val iterator = attackHolds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val remaining = entry.value - deltaSeconds
            if (remaining <= 0f) iterator.remove() else entry.setValue(remaining)
        }
    }

    /**
     * Picks each actor's animation state from what it is actually doing, and
     * advances its clock.
     *
     * Derived every frame rather than stored, so state can never drift out of
     * step with the simulation that produced it.
     */
    private fun advanceAnimations(deltaSeconds: Float) {
        val deltaMs = (deltaSeconds * 1000f).toLong()

        val playerState = AnimationSelector.select(
            isDead = !player.isAlive,
            isRolling = isRolling,
            wasHitRecently = hitFlashes.intensity(PLAYER_ACTOR_ID) > 0f,
            isAttacking = attackHolds.containsKey(PLAYER_ACTOR_ID),
            isMoving = moveInput != WorldPoint.ZERO,
        )
        playbacks[PLAYER_ACTOR_ID] = animate(PLAYER_ACTOR_ID, playerState, deltaMs)

        enemies.forEach { enemy ->
            val state = AnimationSelector.select(
                isDead = !enemy.isAlive,
                wasHitRecently = hitFlashes.intensity(enemy.instanceId) > 0f,
                isAttacking = attackHolds.containsKey(enemy.instanceId),
                isMoving = enemy.state == com.stratum.core.domain.actor.EnemyState.CHASING ||
                    enemy.state == com.stratum.core.domain.actor.EnemyState.FLEEING,
            )
            playbacks[enemy.instanceId] = animate(enemy.instanceId, state, deltaMs)
        }

        // Forget actors that no longer exist, or the map grows for the whole run.
        val living = enemies.map { it.instanceId }.toSet() + PLAYER_ACTOR_ID
        playbacks.keys.retainAll(living)
    }

    private fun animate(actorId: String, state: AnimationState, deltaMs: Long): AnimationPlayback =
        (playbacks[actorId] ?: AnimationPlayback())
            .transitionTo(state)
            .advanced(deltaMs)

    // ---- building ---------------------------------------------------------

    private val roomScanner = RoomScanner(streamingWorld)

    /**
     * Blocks a pending build would place, for the ghost preview. Empty when not
     * building.
     */
    var buildPreview: List<BlockPos> = emptyList()
        private set

    var buildTool: BuildTool = BuildTool.SINGLE
        private set

    fun selectBuildTool(tool: BuildTool) {
        buildTool = tool
        buildPreview = emptyList()
    }

    /**
     * Previews what a drag from [from] to [to] would build. Nothing is placed;
     * this exists so the player sees the shape before spending the blocks.
     */
    fun previewBuild(from: BlockPos, to: BlockPos): BuildPreview {
        val blockId = player.selectedBlockId
            ?: return BuildPreview(emptyList(), 0, 0, false).also { buildPreview = emptyList() }

        val planned = BuildPlanner.plan(buildTool, from, to)
        // Only cells that are actually free: the preview should show what will
        // happen, not what was asked for.
        val placeable = planned.filter { pos ->
            pos.z in 0 until Chunk.HEIGHT &&
                streamingWorld.isLoaded(pos.chunkPos) &&
                streamingWorld.blockAt(pos).isAir &&
                pos != player.feet &&
                pos != player.feet.above()
        }
        buildPreview = placeable

        val held = player.countOf(blockId)
        return BuildPreview(
            positions = placeable,
            required = placeable.size,
            held = held,
            affordable = held >= placeable.size,
        )
    }

    fun cancelBuild() {
        buildPreview = emptyList()
    }

    /**
     * Commits the previewed build, spending one held block per cell.
     *
     * Partial builds are allowed: running out halfway through leaves what was
     * afforded rather than refusing the whole thing, which is what a player
     * expects from a drag that was slightly too ambitious.
     */
    fun commitBuild(): BuildResult {
        val blockId = player.selectedBlockId ?: return BuildResult.NothingSelected
        val planned = buildPreview
        if (planned.isEmpty()) return BuildResult.NothingToBuild

        val index = content.registry.indexOrNull(blockId) ?: return BuildResult.NothingSelected
        var placed = 0

        for (pos in planned) {
            val spent = player.consuming(blockId) ?: break
            if (!streamingWorld.setBlock(pos, index)) continue
            player = spent
            placed++
        }

        buildPreview = emptyList()
        if (placed == 0) return BuildResult.OutOfBlocks

        feedbackLog.add(
            kind = FeedbackKind.LOOT,
            text = "Built $placed",
            origin = player.position,
            color = FEEDBACK_BUILT,
        )
        return BuildResult.Built(placed, planned.size - placed)
    }

    /**
     * The room the player is standing in, if any. Recomputed on demand rather
     * than cached: walls change constantly while building, and a stale answer
     * would be worse than none.
     */
    fun shelter(): RoomScan = roomScanner.scan(player.blockPos)

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
        isRolling = isRolling,
        isInvulnerable = isInvulnerable,
        rollCooldownFraction = rollCooldownFraction,
        feedback = feedback,
        playerFlash = hitFlashes.intensity(PLAYER_ACTOR_ID),
        playerAnimation = animationFor(PLAYER_ACTOR_ID),
        buildPreview = buildPreview,
        buildTool = buildTool,
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

        /** Blocks per second at full stick deflection. */
        const val WALK_SPEED = 4.2f
        /** A roll is a burst, not a sprint: fast and over quickly. */
        const val ROLL_SPEED = 11f
        const val ROLL_DURATION = 0.28f
        /**
         * Shorter than the roll, so the end of a roll is vulnerable. Rolling
         * through an attack has to be timed rather than held.
         */
        const val ROLL_INVULNERABILITY = 0.2f
        const val ROLL_COOLDOWN = 1.1f
        /** Below this the stick is treated as centred. */
        const val INPUT_DEADZONE = 0.12f

        const val PLAYER_ACTOR_ID = "player"
        /** How long an actor is considered mid-swing, for animation only. */
        const val ATTACK_ANIMATION_HOLD = 0.3f
        private const val FEEDBACK_HURT = 0xFFD2544BL
        private const val FEEDBACK_DODGE = 0xFF7FD4E0L
        private const val FEEDBACK_BLOCKED = 0xFF9A96A8L
        private const val FEEDBACK_HEAL = 0xFF7BC67EL
        private const val FEEDBACK_LEVEL = 0xFFFFC107L
        private const val FEEDBACK_BUILT = 0xFF8FB8DEL
        private val SPAWN_CHUNK = com.stratum.core.domain.world.ChunkPos(0, 0)
    }
}

data class MoveOutcome(val player: PlayerState, val moved: Boolean, val blocked: Boolean)

sealed interface DodgeResult {
    data object Rolling : DodgeResult
    data object OnCooldown : DodgeResult
    data object AlreadyRolling : DodgeResult
    data object Rejected : DodgeResult
}

data class SessionSnapshot(
    val player: PlayerState,
    val focus: com.stratum.core.domain.world.ChunkPos,
    val biome: BiomeDefinition,
    val miningTarget: BlockPos?,
    val miningFraction: Float,
    val isRolling: Boolean = false,
    val isInvulnerable: Boolean = false,
    val rollCooldownFraction: Float = 0f,
    val feedback: List<FeedbackMark> = emptyList(),
    val playerFlash: Float = 0f,
    val playerAnimation: AnimationPlayback = AnimationPlayback(),
    val buildPreview: List<BlockPos> = emptyList(),
    val buildTool: BuildTool = BuildTool.SINGLE,
    /** Changes when any loaded chunk changes, so the renderer knows to redraw. */
    val worldRevision: Int,
    val enemies: List<EnemyInstance> = emptyList(),
    val groundLoot: List<GroundLoot> = emptyList(),
    val skills: List<SkillDefinition> = emptyList(),
)

/** What a pending build would cost and cover. */
data class BuildPreview(
    val positions: List<BlockPos>,
    val required: Int,
    val held: Int,
    val affordable: Boolean,
) {
    val isEmpty: Boolean get() = positions.isEmpty()
}

sealed interface BuildResult {
    /** [short] is how many cells were skipped for want of blocks. */
    data class Built(val placed: Int, val short: Int) : BuildResult

    data object NothingSelected : BuildResult
    data object NothingToBuild : BuildResult
    data object OutOfBlocks : BuildResult
}

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

    /** An attack that would have landed but did not, because of a roll. */
    data class PlayerDodged(val amountAvoided: Int) : CombatEvent
    data class LootTaken(val item: ItemInstance, val equipped: Boolean) : CombatEvent
    data object PlayerDied : CombatEvent
}
