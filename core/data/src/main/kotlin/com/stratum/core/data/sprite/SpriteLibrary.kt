package com.stratum.core.data.sprite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.stratum.core.domain.sprite.AnimationClip
import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.sprite.SpriteOrigin
import com.stratum.core.domain.sprite.SpriteSheet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores generated sprite sheets on the device.
 *
 * A sheet is two things: pixels and a description of how to cut them. They are
 * written side by side under one id so a half-written pair cannot be loaded --
 * the metadata is written last, so an image with no metadata is simply ignored
 * and regenerated rather than crashing a world load.
 */
class SpriteLibrary(context: Context) {

    private val root: File = File(context.applicationContext.filesDir, DIRECTORY).apply { mkdirs() }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val bitmaps = HashMap<String, Bitmap?>()

    /** Sheets on disk, newest first. */
    fun all(): List<SpriteSheet> =
        root.listFiles { file -> file.name.endsWith(METADATA_SUFFIX) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { readMetadata(it) }

    fun save(sheet: SpriteSheet, imageBytes: ByteArray) {
        val slug = slugFor(sheet.id)
        File(root, "$slug$IMAGE_SUFFIX").writeBytes(imageBytes)
        // Metadata last: an image without it is ignored, which is recoverable.
        // Metadata without an image would be a sheet that cannot be drawn.
        File(root, "$slug$METADATA_SUFFIX").writeText(json.encodeToString(sheet.toDto()))
        bitmaps.remove(sheet.id)
    }

    fun delete(sheetId: String) {
        val slug = slugFor(sheetId)
        File(root, "$slug$METADATA_SUFFIX").delete()
        File(root, "$slug$IMAGE_SUFFIX").delete()
        bitmaps.remove(sheetId)
    }

    /**
     * The decoded sheet, or null when it is missing or unreadable.
     *
     * Cached, including the null: a sheet whose file is corrupt should be
     * attempted once, not on every frame.
     */
    fun bitmapFor(sheetId: String): Bitmap? = bitmaps.getOrPut(sheetId) {
        val file = File(root, "${slugFor(sheetId)}$IMAGE_SUFFIX")
        if (!file.isFile) return@getOrPut null
        runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                // Sprite art must not be smoothed on load; the whole point is
                // hard pixel edges.
                inScaled = false
            })
        }.getOrNull()
    }

    fun clearCache() = bitmaps.clear()

    private fun readMetadata(file: File): SpriteSheet? = runCatching {
        json.decodeFromString(SheetDto.serializer(), file.readText()).toDomain()
    }.getOrNull()

    /** Ids are namespaced with a colon, which is not safe in a file name. */
    private fun slugFor(sheetId: String): String = sheetId.replace(NON_FILE_SAFE, "_")

    private companion object {
        const val DIRECTORY = "sprite_sheets"
        const val IMAGE_SUFFIX = ".png"
        const val METADATA_SUFFIX = ".json"
        val NON_FILE_SAFE = Regex("[^A-Za-z0-9._-]")
    }
}

// The domain model is not serializable on purpose: persistence is this module's
// concern, and a stored format that tracks the domain type would break every
// saved sheet the moment the domain changed.
@Serializable
private data class SheetDto(
    val id: String,
    val name: String,
    val columns: Int,
    val rows: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val origin: String,
    val clips: List<ClipDto>,
)

@Serializable
private data class ClipDto(
    val state: String,
    val firstFrame: Int,
    val frameCount: Int,
    val frameDurationMs: Int,
    val loops: Boolean,
)

private fun SpriteSheet.toDto() = SheetDto(
    id = id,
    name = name,
    columns = columns,
    rows = rows,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
    origin = origin.name,
    clips = clips.map {
        ClipDto(it.state.name, it.firstFrame, it.frameCount, it.frameDurationMs, it.loops)
    },
)

private fun SheetDto.toDomain() = SpriteSheet(
    id = id,
    name = name,
    columns = columns,
    rows = rows,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
    origin = runCatching { SpriteOrigin.valueOf(origin) }.getOrDefault(SpriteOrigin.IMPORTED),
    clips = clips.mapNotNull { dto ->
        val state = runCatching { AnimationState.valueOf(dto.state) }.getOrNull()
            ?: return@mapNotNull null
        runCatching {
            AnimationClip(state, dto.firstFrame, dto.frameCount, dto.frameDurationMs, dto.loops)
        }.getOrNull()
    },
)
