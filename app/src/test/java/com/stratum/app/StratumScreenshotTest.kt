package com.stratum.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.stratum.content.igbo.IgboContentPack
import com.stratum.core.designsystem.theme.StratumTheme
import com.stratum.core.domain.world.WorldConfig
import com.stratum.engine.world.IsometricProjection
import com.stratum.engine.world.WorldSession
import com.stratum.core.domain.ai.GeneratedPackDto
import com.stratum.core.domain.ai.toDomain
import com.stratum.feature.forge.ForgeScreenContent
import com.stratum.feature.forge.ForgeStatus
import com.stratum.feature.forge.ForgeUiState
import com.stratum.feature.play.PlayScreenContent
import com.stratum.feature.play.PlayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the new shell so a visual regression shows up as a changed file rather
 * than as a surprise on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class StratumScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun home_screen() {
        composeTestRule.setContent {
            StratumTheme(palette = IgboContentPack.palette, darkTheme = true) {
                StratumApp(modifier = Modifier.fillMaxSize())
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/home.png")
    }

    @Test
    fun play_screen() {
        val content = GameSetup.assemble()
        val session = WorldSession(content, WorldConfig(seed = 99L, simulationRadius = 2))

        // Run the world forward so the shot shows a live fight rather than an
        // empty field: monsters spawn, close in, and chip the player's health.
        repeat(40) { session.tick(0.25f) }
        // Put monsters in reach and land blows, so the shot shows the
        // feedback rather than an idle field.
        // The sturdiest monsters the pack defines, so they survive the blow
        // and the shot shows a fight rather than three corpses. Engine state is
        // internal to :engine:world, so this goes through the public spawn API.
        val sturdy = content.enemies.sortedByDescending { it.baseStats.maxHealth }
        repeat(3) { i ->
            session.spawn(
                sturdy[i % sturdy.size],
                session.player.position.translated(1f + i * 0.5f, -0.6f + i * 0.6f, 0f),
            )
        }
        session.attack()
        session.castSkill(content.skills.first().id)
        session.setMoveInput(1f, -0.4f)
        session.dodge()
        session.tick(0.05f)
        val slain = content.enemies.first()
        session.dropLoot(
            com.stratum.engine.world.LootRoller(content.weapons, content.affixes)
                .craft(
                    content.weapons.last(),
                    itemLevel = 24,
                    rarity = com.stratum.core.domain.item.ItemRarity.EPIC,
                    random = kotlin.random.Random(5),
                ),
            session.player.position.translated(2f, 1f, 0f),
        )
        session.spawn(slain, session.player.position.translated(3f, -1f, 0f))

        composeTestRule.setContent {
            StratumTheme(palette = content.palette, darkTheme = true) {
                PlayScreenContent(
                    state = PlayUiState(
                        player = session.player,
                        camera = session.player.position,
                        projection = IsometricProjection(zoom = 1f),
                        palette = content.palette,
                        biomeName = session.currentBiome.name,
                        enemies = session.enemies,
                        groundLoot = session.groundLoot,
                        skills = session.skills,
                        isRolling = session.isRolling,
                        isInvulnerable = session.isInvulnerable,
                        rollCooldownFraction = session.rollCooldownFraction,
                        feedback = session.feedback,
                        playerFlash = 0.7f,
                        flashFor = session::flashFor,
                    ),
                    world = session.world,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/play.png")
    }

    @Test
    fun build_mode() {
        val content = GameSetup.assemble()
        val session = WorldSession(content, WorldConfig(seed = 99L, simulationRadius = 2))
        repeat(20) { session.tick(0.2f) }

        // A room ghosted but not yet committed: the shape the player is about
        // to commit to is the whole point of the preview.
        val feet = session.player.blockPos
        session.selectBuildTool(com.stratum.engine.world.BuildTool.ROOM)
        session.previewBuild(
            com.stratum.core.domain.world.BlockPos(feet.x + 2, feet.y + 1, feet.z),
            com.stratum.core.domain.world.BlockPos(feet.x + 7, feet.y + 6, feet.z),
        )

        composeTestRule.setContent {
            StratumTheme(palette = content.palette, darkTheme = true) {
                PlayScreenContent(
                    state = PlayUiState(
                        player = session.player,
                        camera = session.player.position,
                        projection = IsometricProjection(zoom = 1f),
                        palette = content.palette,
                        biomeName = session.currentBiome.name,
                        enemies = session.enemies,
                        skills = session.skills,
                        buildMode = true,
                        buildPreview = session.buildPreview,
                        buildTool = com.stratum.engine.world.BuildTool.ROOM,
                        buildAffordable = true,
                    ),
                    world = session.world,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/build.png")
    }

    @Test
    fun anvil_screen() {
        val content = GameSetup.assemble()
        val session = WorldSession(content, WorldConfig(seed = 99L, simulationRadius = 2))
        repeat(20) { session.tick(0.2f) }

        // A relic with four sockets, two of them already filled, and a pouch
        // with something to put in the rest: the state the panel has to read
        // clearly is the half-finished one, not the empty one.
        val relic = com.stratum.engine.world.LootRoller(content.weapons, content.affixes, content.inserts)
            .craft(
                content.weapons.last(),
                itemLevel = 24,
                rarity = com.stratum.core.domain.item.ItemRarity.RELIC,
                random = kotlin.random.Random(5),
            )
        content.inserts.take(5).forEach { session.dropInsert(it.id, session.player.position) }
        session.dropLoot(relic, session.player.position)
        session.tick(0.05f)

        val socketed = session.player.equippedWeapon!!
        content.inserts.take(2).forEach { session.slotInsert(socketed.instanceId, it.id) }

        composeTestRule.setContent {
            StratumTheme(palette = content.palette, darkTheme = true) {
                PlayScreenContent(
                    state = PlayUiState(
                        player = session.player,
                        camera = session.player.position,
                        projection = IsometricProjection(zoom = 1f),
                        palette = content.palette,
                        biomeName = session.currentBiome.name,
                        enemies = session.enemies,
                        skills = session.skills,
                        heldInserts = session.heldInserts,
                        insertFor = session::insertOrNull,
                        rarityColors = content::rarityColor,
                        anvilOpen = true,
                    ),
                    world = session.world,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/anvil.png")
    }

    @Test
    fun forge_screen() {
        // Rendered with a result in hand, because the preview after generation
        // is the part of this screen worth guarding against regressions.
        val generated = GeneratedPackDto(
            id = "glasswake",
            name = "Glasswake",
            description = "A drowned city of glass beneath a frozen sea.",
            blocks = listOf(
                com.stratum.core.domain.ai.GeneratedBlockDto(
                    id = "glasswake:silt", name = "Black Silt", material = "SOIL",
                    hardness = 0.4f, topColor = "#2b2f3a", sideColor = "#1d2029",
                ),
                com.stratum.core.domain.ai.GeneratedBlockDto(
                    id = "glasswake:pane", name = "Cathedral Pane", material = "STONE",
                    hardness = 2.0f, opaque = false, topColor = "#5f8ea8", sideColor = "#3f6274",
                ),
                com.stratum.core.domain.ai.GeneratedBlockDto(
                    id = "glasswake:coldlight", name = "Coldlight Vein", material = "ORE",
                    hardness = 4.5f, requiredTier = 2, light = 10,
                    topColor = "#7fd4e0", sideColor = "#4a9aa6",
                ),
            ),
            biomes = listOf(
                com.stratum.core.domain.ai.GeneratedBiomeDto(
                    id = "glasswake:nave", name = "The Flooded Nave",
                    surfaceBlock = "glasswake:silt", subsurfaceBlock = "glasswake:silt",
                    fillerBlock = "glasswake:pane",
                ),
            ),
            heroClasses = listOf(
                com.stratum.core.domain.ai.GeneratedClassDto(
                    id = "glasswake:tidewright", name = "Tidewright", health = 220,
                ),
            ),
        ).toDomain("glasswake")

        composeTestRule.setContent {
            StratumTheme(palette = IgboContentPack.palette, darkTheme = true) {
                ForgeScreenContent(
                    state = ForgeUiState(
                        theme = "A drowned city of glass beneath a frozen sea",
                        status = ForgeStatus.READY,
                        result = generated,
                        providerConfigured = true,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/forge.png")
    }
}
