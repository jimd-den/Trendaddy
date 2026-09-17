package com.stratum.engine.world

import com.stratum.core.domain.world.BlockRegistry
import com.stratum.core.domain.world.Chunk
import com.stratum.core.domain.world.ChunkPos
import com.stratum.core.domain.world.NoiseLayer
import com.stratum.core.domain.world.Stratum
import com.stratum.core.domain.world.TerrainContext
import com.stratum.core.domain.world.TerrainGenerator
import com.stratum.core.domain.world.TerrainGeneratorFactory
import com.stratum.core.domain.world.TerrainGeneratorRegistry
import com.stratum.core.domain.world.TerrainRecipe
import com.stratum.core.domain.world.WorldConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * World generation is meant to be replaceable two ways: describe a different
 * landscape in data, or drop in an entirely different algorithm. Both are
 * exercised here, because "pluggable" is only true if something else is
 * actually plugged in.
 */
class PluggableTerrainTest {

    private val config = WorldConfig(seed = 42L, simulationRadius = 1)
    private val biomes = TestContent.assembled.biomes

    private fun context(recipe: TerrainRecipe) = TerrainContext(config, biomes, recipe)

    // ---- a completely different generator --------------------------------

    /** Nothing to do with the built-in one: every column the same height. */
    private class SlabGenerator(private val height: Int) : TerrainGenerator {
        override fun generate(pos: ChunkPos, registry: BlockRegistry): Chunk {
            val chunk = Chunk(pos)
            val stone = registry.indexOf(TestContent.stone.id)
            for (y in 0 until Chunk.SIZE) {
                for (x in 0 until Chunk.SIZE) {
                    for (z in 0..height) chunk.setBlock(x, y, z, stone)
                }
            }
            return chunk
        }
    }

    @Test
    fun `a third party generator can be registered and asked for by id`() {
        val registry = TerrainGeneratorRegistry()
            .register("test:slab", TerrainGeneratorFactory { SlabGenerator(height = 9) })

        val generator = registry.create(context(TerrainRecipe(generatorId = "test:slab")))

        assertTrue(generator is SlabGenerator)
        val chunk = generator.generate(ChunkPos(0, 0), TestContent.registry)
        assertEquals(9, chunk.surfaceAt(0, 0), "the plugged-in generator did not shape the world")
    }

    @Test
    fun `a session runs on a generator it was handed`() {
        val session = WorldSession(
            content = TestContent.assembled,
            config = config,
            terrainGenerator = SlabGenerator(height = 9),
        )

        // Every column is the same height, so the player stands on the slab.
        assertEquals(9, session.world.surfaceAt(0, 0))
        assertTrue(session.player.position.z > 9f, "the player did not land on the custom terrain")
    }

    @Test
    fun `a generator that knows nothing of biomes still runs`() {
        val session = WorldSession(
            content = TestContent.assembled,
            config = config,
            terrainGenerator = SlabGenerator(height = 9),
        )

        // SlabGenerator is not a BiomeSource; the session must not demand one.
        assertTrue(session.currentBiome.name.isNotBlank())
    }

    @Test
    fun `an unknown generator id fails loudly rather than silently substituting`() {
        val registry = TerrainGeneratorRegistry()
        val failure = assertFailsWith<IllegalArgumentException> {
            registry.create(context(TerrainRecipe(generatorId = "nobody:here")))
        }
        assertTrue(failure.message!!.contains("nobody:here"), failure.message!!)
    }

    @Test
    fun `the built-in generator is registered out of the box`() {
        assertTrue(StratumTerrain.registry.has(TerrainRecipe.LAYERED))
    }

    // ---- the same generator, described differently ------------------------

    /**
     * The terrain height, not the highest block: scatter stacks trees on top of
     * the ground, so reading the chunk back measures foliage rather than the
     * landscape the recipe describes.
     */
    private fun surfaceProfile(recipe: TerrainRecipe, span: Int = Chunk.SIZE): List<Int> {
        val generator = LayeredTerrainGenerator(config, biomes, recipe)
        return (0 until span).map { x ->
            generator.surfaceHeight(x, 0, generator.biomeAt(x, 0))
        }
    }

    @Test
    fun `a flat recipe makes flat ground`() {
        val profile = surfaceProfile(TerrainRecipe.FLAT)
        assertEquals(1, profile.distinct().size, "a flat recipe produced hills: $profile")
    }

    @Test
    fun `changing the elevation recipe changes the landscape`() {
        val calm = surfaceProfile(
            TerrainRecipe(elevation = listOf(NoiseLayer(scale = 0.01f, amplitude = 0.2f)), terraceStep = 1),
        )
        val wild = surfaceProfile(
            TerrainRecipe(elevation = listOf(NoiseLayer(scale = 0.2f, amplitude = 1f)), terraceStep = 1),
        )
        assertNotEquals(calm, wild, "two different recipes produced the same terrain")
    }

    @Test
    fun `terracing snaps heights to readable steps`() {
        val step = 3
        val recipe = TerrainRecipe(terraceStep = step)
        val generator = LayeredTerrainGenerator(config, biomes, recipe)

        val heights = (0 until 40).map { x ->
            generator.surfaceHeight(x, 0, generator.biomeAt(x, 0))
        }

        // Clamping at the world floor can produce one off-grid value; everything
        // the generator is free to choose must land on a terrace.
        val offGrid = heights.filter { it % step != 0 }
        assertTrue(
            offGrid.size <= 1,
            "terracing left heights off the grid: $offGrid out of $heights",
        )
    }

    @Test
    fun `terracing collapses the landscape into fewer, readable levels`() {
        val smooth = surfaceProfile(TerrainRecipe(terraceStep = 1), span = 80).distinct()
        val stepped = surfaceProfile(TerrainRecipe(terraceStep = 3), span = 80).distinct()

        assertTrue(
            stepped.size < smooth.size,
            "terracing left as many distinct levels as smooth terrain: $stepped vs $smooth",
        )
    }

    @Test
    fun `strata put the recipe's materials under the surface`() {
        val recipe = TerrainRecipe(
            strata = listOf(Stratum(TestContent.sand.id, thickness = 2)),
            terraceStep = 1,
        )
        val generator = LayeredTerrainGenerator(config, biomes, recipe)
        val chunk = generator.generate(ChunkPos(0, 0), TestContent.registry)
        // The terrain surface, not the top of whatever grew on it.
        val surface = generator.surfaceHeight(0, 0, generator.biomeAt(0, 0))

        val justBelow = TestContent.registry.typeOf(chunk.blockAt(0, 0, surface - 1))
        assertEquals(TestContent.sand.id, justBelow.id, "the recipe's stratum was not laid down")
    }

    @Test
    fun `generation stays deterministic whatever the recipe`() {
        val recipe = TerrainRecipe(terraceStep = 2)
        val first = LayeredTerrainGenerator(config, biomes, recipe)
            .generate(ChunkPos(1, -1), TestContent.registry)
        val second = LayeredTerrainGenerator(config, biomes, recipe)
            .generate(ChunkPos(1, -1), TestContent.registry)

        val a = (0 until Chunk.SIZE).map { first.surfaceAt(it, 0) }
        val b = (0 until Chunk.SIZE).map { second.surfaceAt(it, 0) }
        assertEquals(a, b, "the same seed and recipe produced different terrain")
    }
}
