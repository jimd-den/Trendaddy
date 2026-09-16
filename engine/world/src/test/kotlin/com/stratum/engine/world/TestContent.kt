package com.stratum.engine.world

import com.stratum.core.domain.content.BiomeDefinition
import com.stratum.core.domain.content.ContentPack
import com.stratum.core.domain.content.ContentPackAssembler
import com.stratum.core.domain.content.DepositRule
import com.stratum.core.domain.content.HeroClassDefinition
import com.stratum.core.domain.content.ScatterRule
import com.stratum.core.domain.world.BlockMaterial
import com.stratum.core.domain.world.BlockType

/**
 * A deliberately small pack. Engine tests assert on behaviour, so the content
 * only needs one block per behaviour the engine is supposed to have.
 */
object TestContent {

    val soil = BlockType(id = "test:soil", displayName = "Soil", material = BlockMaterial.SOIL, hardness = 0.5f)
    val stone = BlockType(id = "test:stone", displayName = "Stone", hardness = 2f, requiredTier = 1)
    val sand = BlockType(id = "test:sand", displayName = "Sand", hardness = 0.4f, hasGravity = true)
    val torch = BlockType(
        id = "test:torch",
        displayName = "Torch",
        hardness = 0.1f,
        isSolid = false,
        isOpaque = false,
        needsSupport = true,
        lightEmission = 14,
    )
    val ore = BlockType(
        id = "test:ore",
        displayName = "Ore",
        material = BlockMaterial.ORE,
        hardness = 4f,
        requiredTier = 2,
        dropId = "test:ingot",
    )
    val leaves = BlockType(id = "test:leaves", displayName = "Leaves", hardness = 0.2f, isOpaque = false)

    val plains = BiomeDefinition(
        id = "test:plains",
        name = "Plains",
        surfaceBlockId = soil.id,
        subsurfaceBlockId = soil.id,
        bedrockFillerBlockId = stone.id,
        deposits = listOf(DepositRule(ore.id, minZ = 2, maxZ = 8, chance = 0.25f, clusterSize = 3)),
        scatter = listOf(ScatterRule(leaves.id, chance = 0.1f, height = 2)),
    )

    val highlands = plains.copy(
        id = "test:highlands",
        name = "Highlands",
        surfaceBlockId = stone.id,
        heightBias = 6,
        roughness = 1.5f,
        scatter = emptyList(),
    )

    val digger = HeroClassDefinition(
        id = "test:digger",
        name = "Digger",
        baseHealth = 120,
        baseResource = 40,
        startingBlockIds = listOf(soil.id, stone.id),
    )

    val pack = ContentPack(
        id = "test",
        name = "Engine Test Pack",
        author = "test",
        blocks = listOf(soil, stone, sand, torch, ore, leaves),
        biomes = listOf(plains, highlands),
        heroClasses = listOf(digger),
    )

    val assembled = ContentPackAssembler().assemble(listOf(pack))

    val registry get() = assembled.registry
}
