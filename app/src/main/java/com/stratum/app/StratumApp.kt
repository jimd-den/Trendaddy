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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stratum.core.designsystem.component.ActionEmphasis
import com.stratum.core.designsystem.component.SectionLabel
import com.stratum.core.designsystem.component.StratumAction
import com.stratum.core.designsystem.component.StratumDivider
import com.stratum.core.designsystem.component.StratumPanel
import com.stratum.core.designsystem.component.StratumSection
import com.stratum.core.designsystem.component.StratumWell
import com.stratum.core.designsystem.theme.Space
import com.stratum.core.designsystem.theme.safeContent
import com.stratum.core.designsystem.theme.StratumTheme
import com.stratum.feature.play.PlayScreen
import com.stratum.core.domain.content.ContentPack
import com.stratum.feature.forge.ForgeScreen
import com.stratum.feature.forge.ForgeViewModel
import com.stratum.feature.forge.SpriteForgeScreen
import com.stratum.feature.forge.SpriteForgeViewModel
import com.stratum.feature.play.DrawableSprite
import com.stratum.feature.play.SpriteKey
import com.stratum.feature.play.PlayScreen as PlayScreenRoute
import com.stratum.feature.play.PlayViewModel

/** Top-level destinations. Deliberately few: the game is the app, not a tab in it. */
private enum class Destination { HOME, PLAY, FORGE, SPRITES, SETTINGS, STUDIO }

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

    // Packs the player has forged this session. Adding one rebuilds the
    // assembled content, so the next world is made of the new material.
    var forgedPacks by remember { mutableStateOf(emptyList<ContentPack>()) }
    val content = remember(forgedPacks) { GameSetup.assemble(forgedPacks) }

    // A new seed per run, but stable across recomposition so walking around does
    // not regenerate the world under the player.
    var seed by remember { mutableStateOf(System.currentTimeMillis()) }
    val config = remember(seed) { GameSetup.worldConfig(seed) }

    val context = LocalContext.current
    val ai = remember(context) { AiWiring(context) }

    // Sheets generated this session join the loaded packs, so a drawing made
    // five minutes ago is used by the world exactly like one a pack shipped.
    var spriteRevision by remember { mutableStateOf(0) }
    val spriteSheets = remember(spriteRevision) { ai.sprites.all() }
    val contentWithSprites = remember(content, spriteSheets) {
        content.withSpriteSheets(spriteSheets)
    }

    val spriteResolver = remember(spriteRevision, contentWithSprites) {
        { key: SpriteKey ->
            val sheet = when (key) {
                SpriteKey.Player ->
                    contentWithSprites.spriteSheets.firstOrNull { it.id.startsWith("hero:") }
                is SpriteKey.Monster ->
                    contentWithSprites.sheetForEnemy(key.definitionId)
                        ?: contentWithSprites.spriteSheets.firstOrNull { it.id.startsWith("monster:") }
            }
            sheet?.let { found ->
                ai.sprites.bitmapFor(found.id)?.let { bitmap ->
                    DrawableSprite(found, bitmap.asImageBitmap())
                }
            }
        }
    }

    when (destination) {
        Destination.HOME -> HomeScreen(
            packName = content.packs.joinToString(" + ") { it.name },
            blockCount = content.registry.size,
            biomeCount = content.biomes.size,
            classCount = content.heroClasses.size,
            onDescend = {
                seed = System.currentTimeMillis()
                destination = Destination.PLAY
            },
            onForge = { destination = Destination.FORGE },
            onSprites = { destination = Destination.SPRITES },
            spriteCount = spriteSheets.size,
            onSettings = { destination = Destination.SETTINGS },
            onStudio = { destination = Destination.STUDIO },
            modifier = modifier,
        )

        Destination.PLAY -> {
            // Keyed so forging a pack or starting a new run builds a fresh
            // session rather than reusing the previous world.
            key(contentWithSprites, config) {
                val viewModel: PlayViewModel = viewModel(
                    factory = PlayViewModel.factory(
                        contentWithSprites, config,
                        spriteResolver = spriteResolver,
                    ),
                )
                PlayScreenRoute(
                    viewModel = viewModel,
                    modifier = modifier,
                    onOpenMenu = { destination = Destination.HOME },
                )
            }
        }

        Destination.FORGE -> {
            val forgeViewModel: ForgeViewModel = viewModel(
                factory = ForgeViewModel.factory(
                    generatePack = ai.generateContentPack,
                    generateLore = ai.generateLore,
                    isProviderConfigured = ai::isConfigured,
                    onPackAccepted = { pack -> forgedPacks = forgedPacks + pack },
                ),
            )
            ForgeScreen(
                viewModel = forgeViewModel,
                modifier = modifier,
                onBack = { destination = Destination.HOME },
                onOpenSettings = { destination = Destination.SETTINGS },
            )
        }

        Destination.SPRITES -> {
            val spriteViewModel: SpriteForgeViewModel = viewModel(
                factory = SpriteForgeViewModel.factory(
                    generateSheet = ai.generateSpriteSheet,
                    saveSheet = { sheet, bytes ->
                        ai.sprites.save(sheet, bytes)
                        spriteRevision++
                    },
                    loadSheets = ai.sprites::all,
                    deleteSheet = { id ->
                        ai.sprites.delete(id)
                        spriteRevision++
                    },
                    isProviderConfigured = ai::isConfigured,
                ),
            )
            SpriteForgeScreen(
                viewModel = spriteViewModel,
                modifier = modifier,
                onBack = { destination = Destination.HOME },
                onOpenSettings = { destination = Destination.SETTINGS },
                previewFor = { id -> ai.sprites.bitmapFor(id)?.asImageBitmap() },
            )
        }

        Destination.SETTINGS -> ProviderSettingsScreen(
            initial = ai.settings.load(),
            onSave = ai.settings::save,
            onBack = { destination = Destination.HOME },
            modifier = modifier,
        )

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
    onForge: () -> Unit,
    onSprites: () -> Unit,
    spriteCount: Int,
    onSettings: () -> Unit,
    onStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // Inset before the scroll, so the content scrolls under nothing and
            // the first line is never behind the status bar on a tall phone.
            .safeContent()
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
                label = "Forge a pack with AI",
                onClick = onForge,
                emphasis = ActionEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = if (spriteCount > 0) "Sprite forge ($spriteCount)" else "Sprite forge",
                onClick = onSprites,
                emphasis = ActionEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = "Creator studio",
                onClick = onStudio,
                emphasis = ActionEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = "Model provider",
                onClick = onSettings,
                emphasis = ActionEmphasis.QUIET,
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
