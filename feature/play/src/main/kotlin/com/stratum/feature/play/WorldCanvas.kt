package com.stratum.feature.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.stratum.core.domain.actor.EnemyInstance
import com.stratum.core.domain.world.BlockPos
import com.stratum.core.domain.world.Direction
import com.stratum.core.domain.world.Chunk
import com.stratum.core.domain.world.World
import com.stratum.core.domain.world.WorldPoint
import com.stratum.engine.world.GroundLoot
import com.stratum.engine.world.IsometricProjection

/**
 * Draws the voxel world in isometric projection.
 *
 * The renderer is a pure function of the world and the camera. It holds no state
 * and mutates nothing, so a frame can be reasoned about entirely from its inputs.
 */
@Composable
fun WorldCanvas(
    world: World,
    camera: WorldPoint,
    projection: IsometricProjection,
    highlight: BlockPos?,
    playerPosition: WorldPoint,
    playerFacing: Direction = Direction.SOUTH,
    playerAccent: Color = PLAYER_RING,
    enemies: List<EnemyInstance> = emptyList(),
    groundLoot: List<GroundLoot> = emptyList(),
    /** Redrawn whenever this changes; the world itself is mutable and not a Compose state. */
    revision: Int,
    /** Changes every frame while a fight is running, to force a redraw. */
    frame: Int = 0,
    modifier: Modifier = Modifier,
    onTapBlock: (BlockPos) -> Unit = {},
    onLongPressBlock: (BlockPos) -> Unit = {},
) {
    Canvas(
        modifier = modifier.pointerInput(projection, revision) {
            detectTapGestures(
                onTap = { offset ->
                    pick(world, projection, camera, size.width.toFloat(), size.height.toFloat(), offset)
                        ?.let(onTapBlock)
                },
                onLongPress = { offset ->
                    pick(world, projection, camera, size.width.toFloat(), size.height.toFloat(), offset)
                        ?.let(onLongPressBlock)
                },
            )
        },
    ) {
        @Suppress("UNUSED_EXPRESSION")
        revision
        @Suppress("UNUSED_EXPRESSION")
        frame

        val originX = size.width / 2f - projection.project(camera).x
        val originY = size.height / 2f - projection.project(camera).y

        val range = projection.visibleRange(size.width, size.height, originX, originY)

        range.forEachColumnInDrawOrder { x, y ->
            val surface = world.surfaceAt(x, y)
            if (surface < 0) return@forEachColumnInDrawOrder

            // Draw a few levels below the surface so cliff faces have sides
            // rather than floating tops.
            val floor = maxOf(0, surface - VISIBLE_DEPTH)
            for (z in floor..surface) {
                val pos = BlockPos(x, y, z)
                val block = world.blockAt(pos)
                if (block.isAir) continue
                // Fully buried blocks are invisible; skipping them is the single
                // biggest saving in the draw loop.
                if (z < surface && isEnclosed(world, pos)) continue

                val screen = projection.project(pos)
                drawBlock(
                    centerX = originX + screen.x,
                    centerY = originY + screen.y,
                    projection = projection,
                    topColor = Color(block.topColor),
                    sideColor = Color(block.sideColor),
                    highlighted = pos == highlight,
                    accent = Color(block.accentColor),
                )
            }
        }

        // Actors are drawn after the terrain and sorted among themselves, so a
        // monster standing behind a pillar is still covered by it but a monster
        // in front of another draws over it.
        val actors = buildList {
            groundLoot.forEach { add(Actor.Loot(it)) }
            enemies.filter { it.isAlive }.forEach { add(Actor.Monster(it)) }
            add(Actor.Player(playerPosition))
        }.sortedBy { projection.depthKey(it.position) }

        actors.forEach { actor ->
            val screen = projection.project(actor.position)
            val x = originX + screen.x
            val y = originY + screen.y
            when (actor) {
                is Actor.Loot -> drawLoot(x, y, projection, Color(actor.loot.item.rarity.beamColor()))
                is Actor.Monster -> drawEnemy(x, y, projection, actor.enemy)
                is Actor.Player -> drawPlayer(x, y, projection, playerFacing, playerAccent)
            }
        }
    }
}

/** Anything drawn on top of the terrain, so they can be depth sorted together. */
private sealed interface Actor {
    val position: WorldPoint

    data class Player(override val position: WorldPoint) : Actor
    data class Monster(val enemy: EnemyInstance) : Actor {
        override val position: WorldPoint get() = enemy.position
    }
    data class Loot(val loot: GroundLoot) : Actor {
        override val position: WorldPoint get() = loot.position
    }
}

/**
 * A monster, with a health bar above it once it has been hurt.
 *
 * The bar only appears after the first hit: showing a full bar over every
 * monster turns the screen into a spreadsheet.
 */
private fun DrawScope.drawEnemy(
    x: Float,
    y: Float,
    projection: IsometricProjection,
    enemy: EnemyInstance,
) {
    val scale = projection.tileWidth * projection.zoom
    val radius = scale * 0.16f * enemy.rank.sizeMultiplier()
    val lift = projection.blockHeight * projection.zoom * 0.5f

    drawOval(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(x - radius, y - radius * 0.5f),
        size = Size(radius * 2f, radius),
    )
    drawCircle(Color(enemy.bodyColor), radius, Offset(x, y - lift))
    drawCircle(
        Color.Black.copy(alpha = 0.5f),
        radius,
        Offset(x, y - lift),
        style = Stroke(1.5f),
    )

    // Rank ring, so an elite is legible at a glance rather than by its name.
    enemy.rank.ringColor()?.let { ring ->
        drawCircle(Color(ring), radius * 1.35f, Offset(x, y - lift), style = Stroke(2f))
    }

    if (enemy.healthFraction < 1f) {
        val barWidth = radius * 2.4f
        val barTop = y - lift - radius - scale * 0.14f
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(x - barWidth / 2f, barTop),
            size = Size(barWidth, HEALTH_BAR_HEIGHT),
        )
        drawRect(
            color = ENEMY_HEALTH,
            topLeft = Offset(x - barWidth / 2f, barTop),
            size = Size(barWidth * enemy.healthFraction, HEALTH_BAR_HEIGHT),
        )
    }
}

/** A dropped item: a small diamond under a coloured beam in its rarity colour. */
private fun DrawScope.drawLoot(
    x: Float,
    y: Float,
    projection: IsometricProjection,
    color: Color,
) {
    val scale = projection.tileWidth * projection.zoom
    val size = scale * 0.11f

    drawRect(
        color = color.copy(alpha = 0.25f),
        topLeft = Offset(x - size * 0.35f, y - scale * 0.9f),
        size = Size(size * 0.7f, scale * 0.9f),
    )
    val diamond = Path().apply {
        moveTo(x, y - size)
        lineTo(x + size, y)
        lineTo(x, y + size)
        lineTo(x - size, y)
        close()
    }
    drawPath(diamond, color)
    drawPath(diamond, Color.Black.copy(alpha = 0.6f), style = Stroke(1.5f))
}

/** Bigger ranks are literally bigger, which reads faster than any label. */
private fun com.stratum.core.domain.actor.EnemyRank.sizeMultiplier(): Float = when (this) {
    com.stratum.core.domain.actor.EnemyRank.MINION -> 1f
    com.stratum.core.domain.actor.EnemyRank.ELITE -> 1.25f
    com.stratum.core.domain.actor.EnemyRank.CHAMPION -> 1.5f
    com.stratum.core.domain.actor.EnemyRank.BOSS -> 1.9f
}

private fun com.stratum.core.domain.actor.EnemyRank.ringColor(): Long? = when (this) {
    com.stratum.core.domain.actor.EnemyRank.MINION -> null
    com.stratum.core.domain.actor.EnemyRank.ELITE -> 0xFF42A5F5
    com.stratum.core.domain.actor.EnemyRank.CHAMPION -> 0xFFFFCA28
    com.stratum.core.domain.actor.EnemyRank.BOSS -> 0xFFFF7043
}

private fun com.stratum.core.domain.item.ItemRarity.beamColor(): Long = when (this) {
    com.stratum.core.domain.item.ItemRarity.COMMON -> 0xFFB0BEC5
    com.stratum.core.domain.item.ItemRarity.UNCOMMON -> 0xFF42A5F5
    com.stratum.core.domain.item.ItemRarity.RARE -> 0xFFFFCA28
    com.stratum.core.domain.item.ItemRarity.EPIC -> 0xFFFF7043
    com.stratum.core.domain.item.ItemRarity.RELIC -> 0xFF26A69A
}

private val ENEMY_HEALTH = Color(0xFFD2544B)
private const val HEALTH_BAR_HEIGHT = 3f

private fun pick(
    world: World,
    projection: IsometricProjection,
    camera: WorldPoint,
    width: Float,
    height: Float,
    offset: Offset,
): BlockPos? {
    val originX = width / 2f - projection.project(camera).x
    val originY = height / 2f - projection.project(camera).y
    return projection.pickColumn(
        screenX = offset.x - originX,
        screenY = offset.y - originY,
        isSolidAt = { pos -> !world.blockAt(pos).isAir },
        maxZ = Chunk.HEIGHT - 1,
    )
}

/** A block is invisible when every face that could be seen is covered. */
private fun isEnclosed(world: World, pos: BlockPos): Boolean =
    !world.blockAt(pos.above()).isAir &&
        !world.blockAt(BlockPos(pos.x + 1, pos.y, pos.z)).isAir &&
        !world.blockAt(BlockPos(pos.x, pos.y + 1, pos.z)).isAir

/**
 * One block: the top rhombus plus the two side faces the camera can see.
 *
 * Sides are drawn at fixed brightness rather than from a light source, which
 * keeps blocks readable at a glance and costs nothing per frame.
 */
private fun DrawScope.drawBlock(
    centerX: Float,
    centerY: Float,
    projection: IsometricProjection,
    topColor: Color,
    sideColor: Color,
    accent: Color,
    highlighted: Boolean,
) {
    val halfWidth = projection.tileWidth * projection.zoom / 2f
    val halfHeight = projection.tileHeight * projection.zoom / 2f
    val lift = projection.blockHeight * projection.zoom

    val top = Path().apply {
        moveTo(centerX, centerY - halfHeight)
        lineTo(centerX + halfWidth, centerY)
        lineTo(centerX, centerY + halfHeight)
        lineTo(centerX - halfWidth, centerY)
        close()
    }

    val leftFace = Path().apply {
        moveTo(centerX - halfWidth, centerY)
        lineTo(centerX, centerY + halfHeight)
        lineTo(centerX, centerY + halfHeight + lift)
        lineTo(centerX - halfWidth, centerY + lift)
        close()
    }

    val rightFace = Path().apply {
        moveTo(centerX + halfWidth, centerY)
        lineTo(centerX, centerY + halfHeight)
        lineTo(centerX, centerY + halfHeight + lift)
        lineTo(centerX + halfWidth, centerY + lift)
        close()
    }

    drawPath(leftFace, sideColor.scaleRgb(LEFT_FACE_SHADE))
    drawPath(rightFace, sideColor.scaleRgb(RIGHT_FACE_SHADE))
    drawPath(top, topColor)

    if (highlighted) {
        drawPath(top, accent.copy(alpha = 0.35f))
        drawPath(top, accent, style = Stroke(width = 2f))
    }
}

/**
 * The player.
 *
 * Deliberately larger than any monster and ringed in the pack accent, with a
 * wedge showing which way they face. In a crowd of monsters the one thing that
 * must never be ambiguous is which dot is you.
 */
private fun DrawScope.drawPlayer(
    x: Float,
    y: Float,
    projection: IsometricProjection,
    facing: Direction = Direction.SOUTH,
    accent: Color = PLAYER_RING,
) {
    val scale = projection.tileWidth * projection.zoom
    val radius = scale * 0.22f
    val lift = projection.blockHeight * projection.zoom * 0.5f
    val center = Offset(x, y - lift)

    drawOval(
        color = Color.Black.copy(alpha = 0.4f),
        topLeft = Offset(x - radius, y - radius * 0.5f),
        size = Size(radius * 2f, radius),
    )

    drawCircle(accent.copy(alpha = 0.25f), radius * 1.5f, center)
    drawCircle(PLAYER_BODY, radius, center)
    drawCircle(PLAYER_EDGE, radius, center, style = Stroke(2.5f))
    drawCircle(accent, radius * 1.5f, center, style = Stroke(2f))

    // Facing wedge, projected onto the isometric axes so "east" points where
    // east actually is on screen rather than to the right of the screen.
    val tip = projection.project(
        facing.dx.toFloat() * FACING_REACH,
        facing.dy.toFloat() * FACING_REACH,
        0f,
    )
    val origin = projection.project(0f, 0f, 0f)
    val dx = tip.x - origin.x
    val dy = tip.y - origin.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
    val nx = dx / length
    val ny = dy / length

    val wedge = Path().apply {
        moveTo(center.x + nx * radius * 2.1f, center.y + ny * radius * 2.1f)
        lineTo(center.x + (-ny) * radius * 0.5f, center.y + nx * radius * 0.5f)
        lineTo(center.x + ny * radius * 0.5f, center.y + (-nx) * radius * 0.5f)
        close()
    }
    drawPath(wedge, accent)
}

private fun Color.scaleRgb(factor: Float) = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha,
)

/** The south-west face reads as turned away from the light. */
private const val LEFT_FACE_SHADE = 0.72f
private const val RIGHT_FACE_SHADE = 0.52f
private const val VISIBLE_DEPTH = 6
internal val PLAYER_BODY = Color(0xFFF4EBDC)
internal val PLAYER_EDGE = Color(0xFF14110E)
internal val PLAYER_RING = Color(0xFFCD7F32)
private const val FACING_REACH = 1f
