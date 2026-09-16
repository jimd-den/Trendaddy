package com.stratum.content.igbo

import com.stratum.core.domain.content.ContentPack
import com.stratum.core.domain.content.HeroClassDefinition
import com.stratum.core.domain.content.LoreCategory
import com.stratum.core.domain.content.LoreEntry
import com.stratum.core.domain.content.PackOrigin
import com.stratum.core.domain.content.PackPalette

/**
 * The pack the game ships with.
 *
 * It is an ordinary content pack: the loader treats it exactly like one an AI
 * generated or a player imported, and a later pack can override any block,
 * biome or class in it by id.
 */
object IgboContentPack {

    const val ID = "igbo"

    private const val NS = "igbo"

    /**
     * Bronze, laterite and deep indigo. The interface reads these too, so a
     * different pack restyles the HUD along with the terrain.
     */
    val palette = PackPalette(
        surface = 0xFF14110E,
        surfaceRaised = 0xFF211C16,
        ink = 0xFFF4EBDC,
        inkMuted = 0xFFA1907A,
        accent = 0xFFCD7F32,
        accentAlt = 0xFF00B8A9,
        danger = 0xFFC1453B,
    )

    private val heroClasses = listOf(
        HeroClassDefinition(
            id = "$NS:dike_ozo",
            name = "Dike Ozo",
            title = "Titled Bladesman",
            description = "A titled warrior of the Ozo society who answers a problem with reach and " +
                "a wide arc. Digs well, dies slowly.",
            baseHealth = 260,
            baseResource = 90,
            resourceName = "Resolve",
            strength = 16,
            agility = 11,
            insight = 8,
            startingBlockIds = listOf(IgboPackBlocks.redEarth.id, IgboPackBlocks.graniteStone.id),
            abilityIds = listOf("$NS:mma_nkwu_cleave", "$NS:ikenga_tremor"),
        ),
        HeroClassDefinition(
            id = "$NS:amadioha_invoker",
            name = "Amadioha Invoker",
            title = "Caller of the Storm",
            description = "Pulls the storm down onto a line of ground and everything standing on it. " +
                "Fragile until the first crystal is cut.",
            baseHealth = 180,
            baseResource = 160,
            resourceName = "Charge",
            strength = 8,
            agility = 12,
            insight = 18,
            startingBlockIds = listOf(IgboPackBlocks.obsidianCrag.id),
            abilityIds = listOf("$NS:thunder_spear", "$NS:shockwave_spark"),
        ),
        HeroClassDefinition(
            id = "$NS:dibia_nzu",
            name = "Dibia Nzu",
            title = "Chalk Diviner",
            description = "Reads the ground before breaking it. Sees ore through stone and turns " +
                "poison back on whoever sent it.",
            baseHealth = 200,
            baseResource = 140,
            resourceName = "Communion",
            strength = 9,
            agility = 13,
            insight = 17,
            startingBlockIds = listOf(IgboPackBlocks.nsibidiSeal.id, IgboPackBlocks.groveTurf.id),
            abilityIds = listOf("$NS:venom_geyser", "$NS:solar_supernova"),
        ),
        HeroClassDefinition(
            id = "$NS:ikenga_berserker",
            name = "Ikenga Berserker",
            title = "Horned Right Hand",
            description = "Trades armour for speed and swings until the room is quiet. The fastest " +
                "hands underground.",
            baseHealth = 300,
            baseResource = 70,
            resourceName = "Fury",
            strength = 19,
            agility = 15,
            insight = 6,
            startingBlockIds = listOf(IgboPackBlocks.catacombMasonry.id),
            abilityIds = listOf("$NS:ikenga_tremor", "$NS:mma_nkwu_cleave"),
        ),
    )

    private val lore = listOf(
        LoreEntry(
            id = "$NS:lore_bronze",
            title = "The Roped Vessels",
            body = "The bronzes of Igbo-Ukwu were cast before most of the world had a word for the " +
                "technique. Rope, insect, and leaf were pressed into wax and lost to the fire, and " +
                "what came out was metal that remembered them. What you pull out of a vein down here " +
                "is the raw form of that memory.",
            category = LoreCategory.ARTIFACT,
            subjectId = "$NS:bronze_ore",
        ),
        LoreEntry(
            id = "$NS:lore_amadioha",
            title = "Amadioha's Judgement",
            body = "Thunder is not weather. It is a verdict delivered without appeal, and the crags " +
                "on the peak are where it has been delivered most often. Crystal grows where the " +
                "charge had nowhere left to go.",
            category = LoreCategory.DEITY,
            subjectId = "$NS:thunder_peak",
        ),
        LoreEntry(
            id = "$NS:lore_idemili",
            title = "Idemili's Grove",
            body = "The python passes and the ground is not disturbed. Iroko stands where it was " +
                "planted and outlives the argument about who planted it. Cut the canopy if you must, " +
                "but the roots are older than your claim.",
            category = LoreCategory.PLACE,
            subjectId = "$NS:sacred_grove",
        ),
        LoreEntry(
            id = "$NS:lore_nsibidi",
            title = "Marks That Are Not Letters",
            body = "Nsibidi was never an alphabet. A mark settles a debt, names a union, or warns a " +
                "stranger away from a doorway, and the reading depends on who is entitled to read. " +
                "A seal cut into the ground here refuses the pick for the same reason.",
            category = LoreCategory.RITUAL,
            subjectId = "$NS:nsibidi_seal",
        ),
        LoreEntry(
            id = "$NS:lore_ofo",
            title = "The Ofo Staff",
            body = "Ofo is held by the one entitled to speak. It is not a weapon and it does not make " +
                "its holder right, only answerable. The shrines keep that weight in stone.",
            category = LoreCategory.RITUAL,
            subjectId = "$NS:ofo_shrine",
        ),
        LoreEntry(
            id = "$NS:lore_ikenga",
            title = "The Right Hand",
            body = "Ikenga is carved for a man's own achievement, horned because strength should be " +
                "visible and answerable. It is retired when he is, and broken when he is buried.",
            category = LoreCategory.DEITY,
            subjectId = "$NS:ikenga_berserker",
        ),
    )

    val pack = ContentPack(
        id = ID,
        name = "Igbo-Ukwu Bronze",
        author = "Stratum",
        version = "2.0.0",
        description = "The built-in pack: five regions of Igbo mythology rendered as a mineable " +
            "voxel world, with bronze at the bottom of it.",
        origin = PackOrigin.BUILT_IN,
        palette = palette,
        blocks = IgboPackBlocks.all,
        biomes = IgboPackBiomes.all,
        heroClasses = heroClasses,
        loreEntries = lore,
    )
}
