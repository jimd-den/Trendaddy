package com.stratum.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stratum.core.designsystem.component.ActionEmphasis
import com.stratum.core.designsystem.component.SectionLabel
import com.stratum.core.designsystem.component.StratumAction
import com.stratum.core.designsystem.component.StratumDivider
import com.stratum.core.designsystem.component.StratumPanel
import com.stratum.core.designsystem.component.StratumSection
import com.stratum.core.designsystem.component.StratumWell
import com.stratum.core.designsystem.theme.Space
import com.stratum.core.designsystem.theme.StratumTheme
import com.stratum.feature.play.PlayScreen
import com.stratum.feature.play.PlayViewModel

/** Top-level destinations. Deliberately few: the game is the app, not a tab in it. */
private enum class Destination { HOME, PLAY, STUDIO }

/**
 * The app shell.
 *
 * Navigation is a single state value rather than a nav graph: with three
 * destinations and no deep links, a graph would be ceremony. It becomes one when
 * the studio screens are broken into their own feature modules.
 */
@Composable
fun StratumApp(
    modifier: Modifier = Modifier,
    studioContent: @Composable (onBack: () -> Unit) -> Unit = {},
) {
    var destination by remember { mutableStateOf(Destination.HOME) }
    val content = remember { GameSetup.assemble() }
    val config = remember { GameSetup.worldConfig() }

    when (destination) {
        Destination.HOME -> HomeScreen(
            packName = content.packs.last().name,
            blockCount = content.registry.size,
            biomeCount = content.biomes.size,
            classCount = content.heroClasses.size,
            onDescend = { destination = Destination.PLAY },
            onStudio = { destination = Destination.STUDIO },
            modifier = modifier,
        )

        Destination.PLAY -> {
            val viewModel: PlayViewModel = viewModel(
                factory = PlayViewModel.factory(content, config),
            )
            PlayScreen(
                viewModel = viewModel,
                modifier = modifier,
                onOpenMenu = { destination = Destination.HOME },
            )
        }

        Destination.STUDIO -> studioContent { destination = Destination.HOME }
    }
}

/**
 * The landing screen. It reports what the loaded pack actually contains, so the
 * customization story is visible before the player ever enters a world.
 */
@Composable
private fun HomeScreen(
    packName: String,
    blockCount: Int,
    biomeCount: Int,
    classCount: Int,
    onDescend: () -> Unit,
    onStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(Space.large),
    ) {
        Spacer(Modifier.height(Space.huge))

        Text(
            text = "STRATUM",
            style = MaterialTheme.typography.displaySmall,
            color = colors.ink,
        )
        Text(
            text = "An isometric world you dig apart and rebuild.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(Space.wide))

        StratumSection(
            title = "Loaded pack",
            subtitle = packName,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Stat("Blocks", blockCount, Modifier.weight(1f))
                Stat("Regions", biomeCount, Modifier.weight(1f))
                Stat("Classes", classCount, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.medium))
            StratumDivider()
            Spacer(Modifier.height(Space.medium))
            Text(
                text = "Every block, region, class and line of lore above comes from a content " +
                    "pack. The engine ships with none of its own, so a generated pack sits beside " +
                    "the built-in one as an equal.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }

        Spacer(Modifier.height(Space.large))

        StratumPanel(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Begin")
            Spacer(Modifier.height(Space.medium))
            StratumAction(
                label = "Descend",
                onClick = onDescend,
                emphasis = ActionEmphasis.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = "Creator studio",
                onClick = onStudio,
                emphasis = ActionEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.huge))
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    StratumWell(modifier = modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = StratumTheme.colors.accent,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = StratumTheme.colors.inkMuted,
        )
    }
}
