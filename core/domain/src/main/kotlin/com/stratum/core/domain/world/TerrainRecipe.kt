package com.stratum.core.domain.world

import com.stratum.core.domain.content.BiomeDefinition

/**
 * One octave of elevation noise.
 *
 * Exposed as data so a pack can describe the *shape* of its landscape —
 * broad hills, sharp ridges, a dead flat plain — without anyone writing a
 * generator. [scale] is how fast the terrain changes across the world;
 * [amplitude] is how much of the height budget this octave spends.
 */
data class NoiseLayer(
    val scale: Float,
    val amplitude: Float,
    /** Shifts this octave off the others so they do not line up into stripes. */
    val seedOffset: Int = 0,
) {
    init {
        require(scale > 0f) { "A noise layer with no scale produces a flat plane" }
    }
}

/** One band of material under the surface, from the top down. */
data class Stratum(val blockId: String, val thickness: Int) {
    init {
        require(thickness > 0) { "A stratum with no thickness is not a layer" }
    }
}

/**
 * How a world is shaped, as data.
 *
 * A pack ships one of these instead of a generator, which is what lets a world
 * be described by something that cannot write Kotlin — the AI pack forge, a
 * JSON file, a slider in a menu. [generatorId] names which algorithm reads it,
 * so a pack that wants an entirely different generator says so here and the
 * rest of the recipe becomes that generator's business.
 */
data class TerrainRecipe(
    val generatorId: String = LAYERED,
    /** Summed to produce the surface. Empty means a flat world at sea level. */
    val elevation: List<NoiseLayer> = DEFAULT_ELEVATION,
    /**
     * Surface heights snap to multiples of this.
     *
     * The single most important knob for readability. Smooth noise gives a
     * landscape of one-block steps that reads as texture and is miserable to
     * walk or build on. Snapping to 2 or 3 produces plateaus with clean ledges,
     * so you can see at a glance which level you are on and where you can climb.
     */
    val terraceStep: Int = 2,
    /**
     * Material bands below the surface, top first. Empty falls back to the
     * biome's own subsurface and filler blocks.
     */
    val strata: List<Stratum> = emptyList(),
    /** Overrides [WorldConfig.caveDensity] when set. */
    val caveDensity: Float? = null,
    /** Anything a third-party generator wants; the built-in one ignores it. */
    val options: Map<String, String> = emptyMap(),
) {
    init {
        require(terraceStep >= 1) { "terraceStep of $terraceStep would divide by zero" }
    }

    /** Snaps a height to the recipe's terraces. */
    fun terraced(height: Int): Int =
        if (terraceStep <= 1) height else (height / terraceStep) * terraceStep

    companion object {
        const val LAYERED = "stratum:layered"

        /**
         * Two octaves: one broad landform, one finer detail at a third the
         * amplitude. Enough to read as terrain, not so much that it becomes
         * noise.
         */
        val DEFAULT_ELEVATION = listOf(
            NoiseLayer(scale = 0.012f, amplitude = 1f),
            NoiseLayer(scale = 0.05f, amplitude = 0.35f, seedOffset = 101),
        )

        /** A perfectly flat world, for testing and for building sandboxes. */
        val FLAT = TerrainRecipe(elevation = emptyList(), terraceStep = 1)
    }
}

/**
 * A generator that can also say which biome a column belongs to.
 *
 * Optional on purpose. Naming the region you are standing in is a nicety, and
 * requiring it would make "write your own generator" a bigger job than it needs
 * to be — a dungeon generator has no biomes and should not have to pretend.
 */
interface BiomeSource {
    fun biomeAt(worldX: Int, worldY: Int): BiomeDefinition
}

/** Everything a generator needs to build itself. */
data class TerrainContext(
    val config: WorldConfig,
    val biomes: List<BiomeDefinition>,
    val recipe: TerrainRecipe,
)

/** Builds a generator from a recipe. This is the seam a new algorithm plugs into. */
fun interface TerrainGeneratorFactory {
    fun create(context: TerrainContext): TerrainGenerator
}

/**
 * The generators this build knows about, by id.
 *
 * Open by construction: register a factory and any pack naming that id gets it.
 * A completely different world generator — caves-only, dungeon rooms, a
 * heightmap loaded from a file — needs nothing from this class but an id.
 */
class TerrainGeneratorRegistry {

    private val factories = LinkedHashMap<String, TerrainGeneratorFactory>()

    val ids: Set<String> get() = factories.keys

    fun register(id: String, factory: TerrainGeneratorFactory): TerrainGeneratorRegistry {
        factories[id] = factory
        return this
    }

    fun has(id: String): Boolean = id in factories

    /**
     * Builds the generator a recipe asks for.
     *
     * An unknown id is an error rather than a silent fallback: a pack that asks
     * for a generator this build does not have should say so, not quietly hand
     * the player a different world and let them wonder why it looks wrong.
     */
    fun create(context: TerrainContext): TerrainGenerator {
        val factory = factories[context.recipe.generatorId]
            ?: throw IllegalArgumentException(
                "No terrain generator named '${context.recipe.generatorId}'. " +
                    "Known generators: ${factories.keys.joinToString()}",
            )
        return factory.create(context)
    }
}
