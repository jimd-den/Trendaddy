package com.stratum.feature.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import com.stratum.core.domain.world.BlockPos
import com.stratum.core.domain.world.Chunk
import com.stratum.core.domain.world.World
import com.stratum.core.domain.world.WorldPoint
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
    /** Redrawn whenever this changes; the world itself is mutable and not a Compose state. */
    revision: Int,
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

        val playerScreen = projection.project(playerPosition)
        drawPlayer(originX + playerScreen.x, originY + playerScreen.y, projection)
    }
}

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
        drawPath(top, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

private fun DrawScope.drawPlayer(x: Float, y: Float, projection: IsometricProjection) {
    val radius = projection.tileWidth * projection.zoom * 0.18f
    val lift = projection.blockHeight * projection.zoom * 0.5f

    // A grounded ellipse plus a raised body: enough to read position and height
    // unambiguously until sprite sheets are wired in.
    drawOval(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(x - radius, y - radius * 0.5f),
        size = androidx.compose.ui.geometry.Size(radius * 2f, radius),
    )
    drawCircle(PLAYER_BODY, radius * 0.85f, Offset(x, y - lift))
    drawCircle(PLAYER_EDGE, radius * 0.85f, Offset(x, y - lift), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
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
private val PLAYER_BODY = Color(0xFFF4EBDC)
private val PLAYER_EDGE = Color(0xFF14110E)
