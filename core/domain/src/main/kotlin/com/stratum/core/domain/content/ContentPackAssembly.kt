package com.stratum.core.domain.content

import com.stratum.core.domain.world.BlockRegistry
import kotlinx.coroutines.flow.Flow

/**
 * Merges the packs the player has enabled into the single immutable view the
 * engine runs against.
 *
 * Later packs win on id collisions, which is what lets a player drop an
 * AI-generated pack on top of the built-in one and reskin a handful of blocks
 * without forking the whole thing.
 */
class ContentPackAssembler {

    fun assemble(packs: List<ContentPack>): AssembledContent {
        require(packs.isNotEmpty()) { "Cannot assemble an empty pack list" }

        val blocks = LinkedHashMap<String, com.stratum.core.domain.world.BlockType>()
        val biomes = LinkedHashMap<String, BiomeDefinition>()
        val classes = LinkedHashMap<String, HeroClassDefinition>()
        val lore = LinkedHashMap<String, LoreEntry>()
        val overrides = mutableListOf<PackOverride>()

        packs.forEach { pack ->
            pack.blocks.forEach { block ->
                blocks.put(block.id, block)?.let {
                    overrides += PackOverride(pack.id, block.id, OverrideKind.BLOCK)
                }
            }
            pack.biomes.forEach { biome ->
                biomes.put(biome.id, biome)?.let {
                    overrides += PackOverride(pack.id, biome.id, OverrideKind.BIOME)
                }
            }
            pack.heroClasses.forEach { hero ->
                classes.put(hero.id, hero)?.let {
                    overrides += PackOverride(pack.id, hero.id, OverrideKind.HERO_CLASS)
                }
            }
            pack.loreEntries.forEach { entry -> lore[entry.id] = entry }
        }

        val registry = BlockRegistry.build(blocks.values.toList())
        val resolvedBiomes = biomes.values.toList()
        validate(registry, resolvedBiomes)

        return AssembledContent(
            packs = packs,
            registry = registry,
            biomes = resolvedBiomes,
            heroClasses = classes.values.toList(),
            lore = lore.values.toList(),
            palette = packs.last().palette,
            overrides = overrides,
        )
    }

    /**
     * A biome that names a block nobody defined would crash mid-generation, far
     * from the pack that caused it. Catch it at load time instead.
     */
    private fun validate(registry: BlockRegistry, biomes: List<BiomeDefinition>) {
        val missing = mutableListOf<String>()
        biomes.forEach { biome ->
            listOf(biome.surfaceBlockId, biome.subsurfaceBlockId, biome.bedrockFillerBlockId)
                .filterNot(registry::contains)
                .forEach { missing += "biome '${biome.id}' references unknown block '$it'" }
            biome.scatter.filterNot { registry.contains(it.blockId) }
                .forEach { missing += "biome '${biome.id}' scatters unknown block '${it.blockId}'" }
            biome.deposits.filterNot { registry.contains(it.blockId) }
                .forEach { missing += "biome '${biome.id}' deposits unknown block '${it.blockId}'" }
        }
        if (missing.isNotEmpty()) {
            throw ContentPackException(missing.joinToString("; "))
        }
    }
}

/** The flattened, validated result the engine actually runs on. */
data class AssembledContent(
    val packs: List<ContentPack>,
    val registry: BlockRegistry,
    val biomes: List<BiomeDefinition>,
    val heroClasses: List<HeroClassDefinition>,
    val lore: List<LoreEntry>,
    val palette: PackPalette,
    /** Reported to the player so a pack silently reskinning another is visible. */
    val overrides: List<PackOverride> = emptyList(),
) {
    fun biome(id: String): BiomeDefinition =
        biomes.firstOrNull { it.id == id } ?: throw ContentPackException("Unknown biome '$id'")

    fun heroClass(id: String): HeroClassDefinition =
        heroClasses.firstOrNull { it.id == id } ?: throw ContentPackException("Unknown class '$id'")

    fun loreFor(subjectId: String): List<LoreEntry> = lore.filter { it.subjectId == subjectId }
}

data class PackOverride(val packId: String, val targetId: String, val kind: OverrideKind)

enum class OverrideKind { BLOCK, BIOME, HERO_CLASS }

class ContentPackException(message: String) : IllegalStateException(message)

/** Storage port. Implemented over Room in `:core:data`. */
interface ContentPackRepository {
    fun observePacks(): Flow<List<ContentPack>>

    suspend fun allPacks(): List<ContentPack>

    suspend fun packById(id: String): ContentPack?

    suspend fun save(pack: ContentPack)

    suspend fun delete(id: String)

    suspend fun enabledPackIds(): List<String>

    suspend fun setEnabledPackIds(ids: List<String>)
}
