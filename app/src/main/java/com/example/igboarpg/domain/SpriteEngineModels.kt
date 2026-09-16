package com.example.igboarpg.domain

/**
 * CHAPTER 04: DOMAIN MODEL - SPRITE SHEET IMPORT & ANIMATION ENGINE
 *
 * This literate module provides data structures and animation controllers for
 * importing sprite sheets (PNG grid layouts, frame widths, animations) and
 * dynamically binding them to the player avatar or enemies in the ARPG game loop.
 */

enum class CharacterAnimationState {
    IDLE,
    WALK,
    ATTACK,
    CAST_SPELL,
    HURT,
    DEFEAT
}

data class SpriteFrame(
    val frameIndex: Int,
    val column: Int,
    val row: Int,
    val durationMs: Long = 125L // 8 frames per second default
)

data class SpriteAnimationTrack(
    val state: CharacterAnimationState,
    val startFrame: Int,
    val frameCount: Int,
    val loop: Boolean = true,
    val frameDurationMs: Long = 120L
)

enum class SpriteEntityCategory {
    HERO,
    ENEMY
}

data class SpriteSheetData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val frameWidth: Int = 48,
    val frameHeight: Int = 48,
    val columns: Int = 4,
    val rows: Int = 4,
    val animations: Map<CharacterAnimationState, SpriteAnimationTrack> = defaultAnimationTracks(),
    val isCustomImport: Boolean = false,
    val rawImageUriOrBase64: String? = null,
    val igboThemeRole: String = "Ozo Bronze Warrior",
    val entityCategory: SpriteEntityCategory = SpriteEntityCategory.HERO,
    val enemyArchetypeId: String? = null
) {
    val totalFrames: Int get() = columns * rows

    fun getFrameForTime(state: CharacterAnimationState, elapsedMs: Long): Int {
        val track = animations[state] ?: return 0
        if (track.frameCount <= 1) return track.startFrame
        val totalTrackDuration = track.frameCount * track.frameDurationMs
        val currentMs = if (track.loop) elapsedMs % totalTrackDuration else elapsedMs.coerceAtMost(totalTrackDuration - 1)
        val frameOffset = (currentMs / track.frameDurationMs).toInt().coerceIn(0, track.frameCount - 1)
        return track.startFrame + frameOffset
    }

    fun toJson(): String {
        val json = org.json.JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("description", description)
        json.put("frameWidth", frameWidth)
        json.put("frameHeight", frameHeight)
        json.put("columns", columns)
        json.put("rows", rows)
        json.put("isCustomImport", isCustomImport)
        json.put("igboThemeRole", igboThemeRole)
        json.put("entityCategory", entityCategory.name)
        if (enemyArchetypeId != null) {
            json.put("enemyArchetypeId", enemyArchetypeId)
        }
        if (rawImageUriOrBase64 != null) {
            json.put("rawImageUriOrBase64", rawImageUriOrBase64)
        }
        val animsArray = org.json.JSONArray()
        animations.forEach { (state, track) ->
            val obj = org.json.JSONObject()
            obj.put("state", state.name)
            obj.put("startFrame", track.startFrame)
            obj.put("frameCount", track.frameCount)
            obj.put("loop", track.loop)
            obj.put("frameDurationMs", track.frameDurationMs)
            animsArray.put(obj)
        }
        json.put("animations", animsArray)
        return json.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): SpriteSheetData? {
            return try {
                val json = org.json.JSONObject(jsonStr)
                val id = json.optString("id", java.util.UUID.randomUUID().toString())
                val name = json.optString("name", "Custom Sprite")
                val desc = json.optString("description", "Imported Sprite Sheet")
                val fw = json.optInt("frameWidth", 48)
                val fh = json.optInt("frameHeight", 48)
                val cols = json.optInt("columns", 4)
                val rows = json.optInt("rows", 4)
                val custom = json.optBoolean("isCustomImport", true)
                val raw = if (json.has("rawImageUriOrBase64")) json.getString("rawImageUriOrBase64") else null
                val role = json.optString("igboThemeRole", "Imported Hero")
                val catStr = json.optString("entityCategory", "HERO")
                val cat = SpriteEntityCategory.values().firstOrNull { it.name == catStr } ?: SpriteEntityCategory.HERO
                val enemyArch = if (json.has("enemyArchetypeId")) json.getString("enemyArchetypeId") else null

                val animsMap = mutableMapOf<CharacterAnimationState, SpriteAnimationTrack>()
                if (json.has("animations")) {
                    val arr = json.getJSONArray("animations")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val stName = o.getString("state")
                        val st = CharacterAnimationState.values().firstOrNull { it.name == stName } ?: continue
                        animsMap[st] = SpriteAnimationTrack(
                            state = st,
                            startFrame = o.optInt("startFrame", 0),
                            frameCount = o.optInt("frameCount", 4),
                            loop = o.optBoolean("loop", true),
                            frameDurationMs = o.optLong("frameDurationMs", 120L)
                        )
                    }
                }
                SpriteSheetData(
                    id = id,
                    name = name,
                    description = desc,
                    frameWidth = fw,
                    frameHeight = fh,
                    columns = cols,
                    rows = rows,
                    animations = if (animsMap.isNotEmpty()) animsMap else defaultAnimationTracks(),
                    isCustomImport = custom,
                    rawImageUriOrBase64 = raw,
                    igboThemeRole = role,
                    entityCategory = cat,
                    enemyArchetypeId = enemyArch
                )
            } catch (e: Exception) {
                null
            }
        }

        fun defaultAnimationTracks(): Map<CharacterAnimationState, SpriteAnimationTrack> {
            return mapOf(
                CharacterAnimationState.IDLE to SpriteAnimationTrack(CharacterAnimationState.IDLE, startFrame = 0, frameCount = 4, loop = true, frameDurationMs = 180L),
                CharacterAnimationState.WALK to SpriteAnimationTrack(CharacterAnimationState.WALK, startFrame = 4, frameCount = 4, loop = true, frameDurationMs = 120L),
                CharacterAnimationState.ATTACK to SpriteAnimationTrack(CharacterAnimationState.ATTACK, startFrame = 8, frameCount = 4, loop = false, frameDurationMs = 90L),
                CharacterAnimationState.CAST_SPELL to SpriteAnimationTrack(CharacterAnimationState.CAST_SPELL, startFrame = 12, frameCount = 4, loop = false, frameDurationMs = 110L),
                CharacterAnimationState.HURT to SpriteAnimationTrack(CharacterAnimationState.HURT, startFrame = 14, frameCount = 2, loop = false, frameDurationMs = 100L),
                CharacterAnimationState.DEFEAT to SpriteAnimationTrack(CharacterAnimationState.DEFEAT, startFrame = 15, frameCount = 1, loop = false, frameDurationMs = 200L)
            )
        }
    }
}

object PreloadedSpritePacks {

    val IGBO_OZO_WARRIOR = SpriteSheetData(
        id = "sprite_ozo_warrior",
        name = "Dike Ozo (Bronze Hero)",
        description = "Royal Igbo titleholder adorned in bronze pectoral plates, roped armlets, and wielding the sacred bronze sword.",
        frameWidth = 48,
        frameHeight = 48,
        columns = 4,
        rows = 4,
        isCustomImport = false,
        igboThemeRole = "Dike Ozo Warrior",
        entityCategory = SpriteEntityCategory.HERO
    )

    val IKENGA_BRONZE_GOLEM = SpriteSheetData(
        id = "sprite_ikenga_golem",
        name = "Ikenga Horned Automaton",
        description = "Ancient guardian statue sculpted from 9th-century Igbo-Ukwu roped bronze, charged with ancestral lightning.",
        frameWidth = 48,
        frameHeight = 48,
        columns = 4,
        rows = 4,
        isCustomImport = false,
        igboThemeRole = "Ikenga Golem",
        entityCategory = SpriteEntityCategory.HERO
    )

    val MMANWU_SPIRIT = SpriteSheetData(
        id = "sprite_mmanwu_spirit",
        name = "Mmanwu Masked Spirit",
        description = "Otherworldly entity from the ancestral grove cloaked in vivid raffia and intricate geometric Uli symbols.",
        frameWidth = 48,
        frameHeight = 48,
        columns = 4,
        rows = 4,
        isCustomImport = false,
        igboThemeRole = "Mmanwu Spirit",
        entityCategory = SpriteEntityCategory.HERO
    )

    // Preloaded Enemy Sprite Packs with authentic pixel animations
    val ENEMY_FOREST_MMO = SpriteSheetData(
        id = "sprite_enemy_forest_mmo",
        name = "Forest Mmo (Masked Ghoul)",
        description = "Wandering ancestral woodland spirit cloaked in woven raffia and green ghostfire.",
        frameWidth = 48,
        frameHeight = 48,
        columns = 4,
        rows = 4,
        isCustomImport = false,
        igboThemeRole = "Forest Spirit",
        entityCategory = SpriteEntityCategory.ENEMY,
        enemyArchetypeId = "arch_forest_mmo"
    )

    val ENEMY_OGU_BRUTE = SpriteSheetData(
        id = "sprite_enemy_ogu_brute",
        name = "Ogu Bronze Behemoth",
        description = "Massive armored warrior from the iron clans brandishing twin spiked bronze bludgeons.",
        frameWidth = 48,
        frameHeight = 48,
        columns = 4,
        rows = 4,
        isCustomImport = false,
        igboThemeRole = "Bronze Brute",
        entityCategory = SpriteEntityCategory.ENEMY,
        enemyArchetypeId = "arch_ogu_brute"
    )

    val ENEMY_LEOPARD_ASSASSIN = SpriteSheetData(
        id = "sprite_enemy_leopard_stalker",
        name = "Agu Shadow Leopard",
        description = "Sacred leopard hunter blessed by Idemili with shadowstep swiftness and razor claws.",
        frameWidth = 48,
        frameHeight = 48,
        columns = 4,
        rows = 4,
        isCustomImport = false,
        igboThemeRole = "Shadow Leopard",
        entityCategory = SpriteEntityCategory.ENEMY,
        enemyArchetypeId = "arch_shadow_leopard"
    )

    val ENEMY_AGBARA_HIGH_PRIEST = SpriteSheetData(
        id = "sprite_enemy_agbara_boss",
        name = "Agbara High Priest (Boss)",
        description = "Ancient ceremonial high priest channeling thunderbolts and solar pyres from the shrine altar.",
        frameWidth = 48,
        frameHeight = 48,
        columns = 4,
        rows = 4,
        isCustomImport = false,
        igboThemeRole = "Agbara High Priest",
        entityCategory = SpriteEntityCategory.ENEMY,
        enemyArchetypeId = "arch_boss_priest"
    )

    val ALL_PRELOADED = listOf(
        IGBO_OZO_WARRIOR,
        IKENGA_BRONZE_GOLEM,
        MMANWU_SPIRIT,
        ENEMY_FOREST_MMO,
        ENEMY_OGU_BRUTE,
        ENEMY_LEOPARD_ASSASSIN,
        ENEMY_AGBARA_HIGH_PRIEST
    )
}
