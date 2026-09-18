package com.stratum.core.domain.sprite

/** Where one generated pose lands on the finished sheet. */
data class PoseCell(
    /** Matches the pose step that asked for it, so a retry replaces the right cell. */
    val key: String,
    val state: AnimationState,
    val index: Int,
    val column: Int,
    val row: Int,
) {
    fun cellIndex(columns: Int): Int = row * columns + column

    companion object {
        /**
         * How a pose is named, in one place.
         *
         * The step that asks for a pose and the cell that receives it are built
         * by different layers at different times, and they find each other by
         * this string. Two copies of the format would drift, and the symptom
         * would be a run that generates forty frames and composites none of
         * them.
         */
        fun keyOf(state: AnimationState, index: Int): String =
            "${state.name.lowercase()}_$index"
    }
}

/**
 * The sheet a set of separately generated poses will be composited into.
 *
 * Planned before a single pose has been drawn, which is what makes the whole
 * pipeline restartable. Each generated frame knows the cell it belongs in from
 * the moment it is asked for, so a run that fails on frame nine can be resumed
 * at frame nine, and a frame that came back wrong can be regenerated on its own
 * and dropped back into place without disturbing the other thirty-nine.
 */
data class PoseSheetPlan(
    val cellSize: Int,
    val columns: Int,
    val rows: Int,
    val cells: List<PoseCell>,
    val sheet: SpriteSheet,
) {
    val width: Int get() = columns * cellSize
    val height: Int get() = rows * cellSize

    fun cellFor(key: String): PoseCell? = cells.firstOrNull { it.key == key }

    /** Where a cell sits on the composited image, for the layer that has pixels. */
    fun rectFor(cell: PoseCell): FrameRect = FrameRect(
        left = cell.column * cellSize,
        top = cell.row * cellSize,
        width = cellSize,
        height = cellSize,
    )
}

/**
 * A packed sheet, and the cells nothing could be put in.
 *
 * [missing] is almost always empty and matters entirely when it is not. A pose
 * whose file is unreadable leaves a hole in the middle of an animation, and a
 * hole looks exactly like a frame the character is invisible for -- which is
 * the one failure nobody can diagnose from watching it happen.
 */
data class PackedSheet(val sheet: SpriteSheet, val missing: List<String>)

/**
 * Lays generated poses out as a sheet the engine can already draw.
 *
 * One state per row, short rows padded, exactly as [AtlasBaker] does — and for
 * the same reason. It costs a few empty cells and buys a sheet a person can
 * open in an image editor and understand at a glance, which matters more for an
 * asset that took forty generations to make than the kilobytes do.
 *
 * Square cells, because every pose arrives as a square canvas and is fitted
 * into its cell by its own content rather than by the canvas it came on.
 */
object PoseSheetPlanner {

    fun plan(
        id: String,
        name: String,
        frameCounts: Map<AnimationState, Int>,
        cellSize: Int = DEFAULT_CELL,
        origin: SpriteOrigin = SpriteOrigin.AI_GENERATED,
    ): PoseSheetPlan? {
        val rows = AnimationState.generatedRowOrder
            .mapNotNull { state -> frameCounts[state]?.takeIf { it > 0 }?.let { state to it } }
        if (rows.isEmpty()) return null

        val columns = rows.maxOf { it.second }
        val size = cellSize.coerceIn(MIN_CELL, MAX_CELL)

        val cells = mutableListOf<PoseCell>()
        val clips = mutableListOf<AnimationClip>()

        rows.forEachIndexed { row, (state, count) ->
            for (index in 0 until count) {
                cells += PoseCell(
                    key = PoseCell.keyOf(state, index),
                    state = state,
                    index = index,
                    column = index,
                    row = row,
                )
            }
            clips += AnimationClip(
                state = state,
                firstFrame = row * columns,
                frameCount = count,
                frameDurationMs = state.defaultFrameDurationMs,
                loops = state !in AnimationState.oneShot,
            )
        }

        return PoseSheetPlan(
            cellSize = size,
            columns = columns,
            rows = rows.size,
            cells = cells,
            sheet = SpriteSheet(
                id = id,
                name = name,
                columns = columns,
                rows = rows.size,
                frameWidth = size,
                frameHeight = size,
                clips = clips,
                origin = origin,
            ),
        )
    }

    /**
     * 96 pixels a cell.
     *
     * The sprite is drawn about a tile and a half wide on screen, so 96 is
     * already more than is shown and leaves room for a player who zooms in.
     * Going to 128 doubles the sheet's memory for detail nobody sees at this
     * camera; going to 64 starts to lose the outline that makes a character
     * read against terrain.
     */
    const val DEFAULT_CELL = 96

    /** Below this a character has no silhouette; above it, no sheet fits in a texture. */
    const val MIN_CELL = 32
    const val MAX_CELL = 256
}
