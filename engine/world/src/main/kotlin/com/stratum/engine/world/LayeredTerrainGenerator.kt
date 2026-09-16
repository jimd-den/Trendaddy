package com.stratum.engine.world

import com.stratum.core.domain.content.BiomeDefinition
import com.stratum.core.domain.world.BlockRegistry
import com.stratum.core.domain.world.BlockType
import com.stratum.core.domain.world.Chunk
import com.stratum.core.domain.world.ChunkPos
import com.stratum.core.domain.world.TerrainGenerator
import com.stratum.core.domain.world.WorldConfig

/**
 * Builds terrain in passes: pick a biome, raise a surface, fill the column,
 * carve caves, seed deposits, scatter decoration.
 *
 * Every pass reads only the chunk's own coordinates, so chunks can be generated
 * in any order, in parallel, and regenerate identically. No pass may look at a
 * neighbouring chunk -- that is the rule that keeps streaming seam-free.
 */
class LayeredTerrainGenerator(
    private val config: WorldConfig,
    private val biomes: List<BiomeDefinition>,
) : TerrainGenerator {

    init {
        require(biomes.isNotEmpty()) { "Terrain generation needs at least one biome" }
    }

    private val heightNoise = ValueNoise(config.seed)
    private val biomeNoise = ValueNoise(config.seed * 31 + 7)
    private val caveNoise = ValueNoise(config.seed * 17 + 13)

    override fun generate(pos: ChunkPos, registry: BlockRegistry): Chunk {
        val chunk = Chunk(pos)
        val bedrockIndex = registry.indexOf(BlockType.BEDROCK.id)

        for (localY in 0 until Chunk.SIZE) {
            for (localX in 0 until Chunk.SIZE) {
                val worldX = pos.originX + localX
                val worldY = pos.originY + localY
                val biome = biomeAt(worldX, worldY)
                val surfaceZ = surfaceHeight(worldX, worldY, biome)

                chunk.setBlock(localX, localY, 0, bedrockIndex)
                fillColumn(chunk, registry, localX, localY, worldX, worldY, surfaceZ, biome)
                carveCaves(chunk, registry, localX, localY, worldX, worldY, surfaceZ)
                placeDeposits(chunk, registry, localX, localY, worldX, worldY, surfaceZ, biome, bedrockIndex)
                scatterDecoration(chunk, registry, localX, localY, worldX, worldY, biome)
            }
        }
        return chunk
    }

    /**
     * Biomes are chosen by a low-frequency noise field so regions are large and
     * contiguous rather than a per-column lottery.
     */
    fun biomeAt(worldX: Int, worldY: Int): BiomeDefinition {
        if (biomes.size == 1) return biomes.first()
        val sample = biomeNoise.fractal(worldX * BIOME_SCALE, worldY * BIOME_SCALE, octaves = 2)
        val index = (sample * biomes.size).toInt().coerceIn(0, biomes.size - 1)
        return biomes[index]
    }

    fun surfaceHeight(worldX: Int, worldY: Int, biome: BiomeDefinition): Int {
        val sample = heightNoise.fractal(worldX * TERRAIN_SCALE, worldY * TERRAIN_SCALE, octaves = 4)
        // Summing octaves concentrates samples around the midpoint, so the raw
        // value only ever spends a fraction of the height budget and the world
        // comes out looking like a plain. Stretch it back out before use.
        val signed = (((sample - 0.5f) * 2f) * TERRAIN_GAIN).coerceIn(-1f, 1f)
        val variation = config.surfaceVariation * biome.roughness
        val raw = config.seaLevel + biome.heightBias + (signed * variation).toInt()
        return raw.coerceIn(2, Chunk.HEIGHT - TOP_MARGIN)
    }

    private fun fillColumn(
        chunk: Chunk,
        registry: BlockRegistry,
        localX: Int,
        localY: Int,
        worldX: Int,
        worldY: Int,
        surfaceZ: Int,
        biome: BiomeDefinition,
    ) {
        val filler = registry.indexOf(biome.bedrockFillerBlockId)
        val subsurface = registry.indexOf(biome.subsurfaceBlockId)
        val surface = registry.indexOf(biome.surfaceBlockId)

        for (z in 1 until surfaceZ) {
            val index = if (z >= surfaceZ - SUBSURFACE_DEPTH) subsurface else filler
            chunk.setBlock(localX, localY, z, index)
        }
        chunk.setBlock(localX, localY, surfaceZ, surface)
    }

    /**
     * Caves are a 3D noise threshold, kept below the surface so the terrain does
     * not dissolve into holes at ground level.
     */
    private fun carveCaves(
        chunk: Chunk,
        registry: BlockRegistry,
        localX: Int,
        localY: Int,
        worldX: Int,
        worldY: Int,
        surfaceZ: Int,
    ) {
        val ceiling = surfaceZ - CAVE_HEADROOM
        if (ceiling <= CAVE_FLOOR) return
        for (z in CAVE_FLOOR..ceiling) {
            val density = caveNoise.at(worldX * CAVE_SCALE, worldY * CAVE_SCALE, z * CAVE_SCALE_Z)
            if (density > config.caveDensity) {
                chunk.setBlock(localX, localY, z, BlockRegistry.AIR_INDEX)
            }
        }
    }

    private fun placeDeposits(
        chunk: Chunk,
        registry: BlockRegistry,
        localX: Int,
        localY: Int,
        worldX: Int,
        worldY: Int,
        surfaceZ: Int,
        biome: BiomeDefinition,
        bedrockIndex: Int,
    ) {
        biome.deposits.forEachIndexed { ruleIndex, rule ->
            val index = registry.indexOf(rule.blockId)
            val top = minOf(rule.maxZ, surfaceZ - 1)
            if (rule.minZ > top) return@forEachIndexed

            val roll = PositionalRandom.floatAt(config.seed, worldX, worldY, DEPOSIT_SALT + ruleIndex)
            if (roll >= rule.chance * config.oreRichness) return@forEachIndexed

            // The roll that selected this column also picks where in the band the
            // blob sits, so a deposit is a vein rather than a single cell.
            val anchor = rule.minZ + ((roll / rule.chance.coerceAtLeast(1e-6f)) * (top - rule.minZ + 1)).toInt()
            val half = (rule.clusterSize / 2).coerceAtLeast(0)
            for (z in (anchor - half)..(anchor + half)) {
                if (z <= 0 || z > top) continue
                val existing = chunk.blockAt(localX, localY, z)
                if (existing == BlockRegistry.AIR_INDEX || existing == bedrockIndex) continue
                chunk.setBlock(localX, localY, z, index)
            }
        }
    }

    private fun scatterDecoration(
        chunk: Chunk,
        registry: BlockRegistry,
        localX: Int,
        localY: Int,
        worldX: Int,
        worldY: Int,
        biome: BiomeDefinition,
    ) {
        val surfaceZ = chunk.surfaceAt(localX, localY)
        if (surfaceZ < 0) return

        biome.scatter.forEachIndexed { ruleIndex, rule ->
            val roll = PositionalRandom.floatAt(config.seed, worldX, worldY, SCATTER_SALT + ruleIndex)
            if (roll >= rule.chance) return@forEachIndexed
            val index = registry.indexOf(rule.blockId)
            for (offset in 1..rule.height) {
                val z = surfaceZ + offset
                if (z >= Chunk.HEIGHT) break
                chunk.setBlock(localX, localY, z, index)
            }
        }
    }

    private companion object {
        const val TERRAIN_SCALE = 0.035f
        /** Widens the noise's usable range without making cliffs unwalkable. */
        const val TERRAIN_GAIN = 2.2f
        const val BIOME_SCALE = 0.008f
        const val CAVE_SCALE = 0.12f
        const val CAVE_SCALE_Z = 0.22f
        const val SUBSURFACE_DEPTH = 3
        const val CAVE_FLOOR = 2
        const val CAVE_HEADROOM = 3
        const val TOP_MARGIN = 8
        const val DEPOSIT_SALT = 1000
        const val SCATTER_SALT = 2000
    }
}
