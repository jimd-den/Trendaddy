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

        composeTestRule.setContent {
            StratumTheme(palette = content.palette, darkTheme = true) {
                PlayScreenContent(
                    state = PlayUiState(
                        player = session.player,
                        camera = session.player.position,
                        projection = IsometricProjection(zoom = 1f),
                        palette = content.palette,
                        biomeName = session.currentBiome.name,
                    ),
                    world = session.world,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/play.png")
    }
}
