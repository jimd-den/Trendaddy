package com.stratum.feature.forge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.designsystem.component.ActionEmphasis
import com.stratum.core.designsystem.component.SectionLabel
import com.stratum.core.designsystem.component.StratumAction
import com.stratum.core.designsystem.component.StratumChip
import com.stratum.core.designsystem.component.StratumDivider
import com.stratum.core.designsystem.component.StratumPanel
import com.stratum.core.designsystem.component.StratumWell
import com.stratum.core.designsystem.theme.Space
import com.stratum.core.designsystem.theme.safeContent
import com.stratum.core.designsystem.theme.StratumTheme
import com.stratum.core.domain.sprite.SpriteSheet

/**
 * Prompt a model for a sprite sheet, and see what came back.
 *
 * Showing the raw sheet is the point. Image models produce inconsistent art, so
 * the player needs to look at the grid to decide whether to keep it, and
 * regenerating is one button rather than a settings trip.
 */
@Composable
fun SpriteForgeScreen(
    viewModel: SpriteForgeViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    previewFor: (String) -> ImageBitmap? = { null },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SpriteForgeContent(
        state = state,
        modifier = modifier,
        onSubjectChange = viewModel::updateSubject,
        onStyleChange = viewModel::updateStyle,
        onTargetChange = viewModel::selectTarget,
        onGenerate = viewModel::generate,
        onDelete = viewModel::delete,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        previewFor = previewFor,
    )
}

@Composable
fun SpriteForgeContent(
    state: SpriteForgeUiState,
    modifier: Modifier = Modifier,
    onSubjectChange: (String) -> Unit = {},
    onStyleChange: (String) -> Unit = {},
    onTargetChange: (SpriteTarget) -> Unit = {},
    onGenerate: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    previewFor: (String) -> ImageBitmap? = { null },
) {
    val colors = StratumTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeContent()
            .verticalScroll(rememberScrollState())
            .padding(Space.large),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("Sprite forge")
            StratumAction(label = "Back", onClick = onBack, emphasis = ActionEmphasis.QUIET)
        }

        Spacer(Modifier.height(Space.medium))
        Text(
            text = "Describe a character. A model draws the frames, and the world uses them " +
                "instead of coloured markers. Whatever the quality, it is kept — regenerate " +
                "until one looks right.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(Space.large))

        StratumPanel(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                SpriteTarget.entries.forEach { target ->
                    StratumChip(
                        label = target.label,
                        selected = state.target == target,
                        onClick = { onTargetChange(target) },
                    )
                }
            }

            Spacer(Modifier.height(Space.medium))

            OutlinedTextField(
                value = state.subject,
                onValueChange = onSubjectChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Subject") },
                placeholder = { Text("a bronze-masked warrior with a curved blade") },
                enabled = !state.busy,
                minLines = 2,
            )

            Spacer(Modifier.height(Space.medium))

            OutlinedTextField(
                value = state.style,
                onValueChange = onStyleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Style (optional)") },
                placeholder = { Text("chunky pixel art, limited palette") },
                enabled = !state.busy,
                singleLine = true,
            )

            Spacer(Modifier.height(Space.medium))

            if (!state.providerConfigured) {
                Text(
                    text = "No model provider is configured yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                )
                Spacer(Modifier.height(Space.small))
                StratumAction(
                    label = "Open settings",
                    onClick = onOpenSettings,
                    emphasis = ActionEmphasis.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                StratumAction(
                    label = if (state.busy) "Drawing" else "Draw sheet",
                    onClick = onGenerate,
                    emphasis = ActionEmphasis.PRIMARY,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.busy) {
                Spacer(Modifier.height(Space.medium))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.surfaceSunken,
                )
                Spacer(Modifier.height(Space.small))
                Text(
                    text = "Image models are slow. This can take a minute.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                )
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(Space.medium))
            StratumPanel(modifier = Modifier.fillMaxWidth(), raised = false) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                )
            }
        }

        if (state.sheets.isNotEmpty()) {
            Spacer(Modifier.height(Space.large))
            SectionLabel("Drawn so far")
            Spacer(Modifier.height(Space.small))
            state.sheets.forEach { sheet ->
                SheetRow(
                    sheet = sheet,
                    isNewest = sheet.id == state.lastGenerated?.id,
                    preview = previewFor(sheet.id),
                    onDelete = { onDelete(sheet.id) },
                )
                Spacer(Modifier.height(Space.small))
            }
        }

        Spacer(Modifier.height(Space.huge))
    }
}

@Composable
private fun SheetRow(
    sheet: SpriteSheet,
    isNewest: Boolean,
    preview: ImageBitmap?,
    onDelete: () -> Unit,
) {
    val colors = StratumTheme.colors

    StratumPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sheet.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isNewest) colors.accent else colors.ink,
                )
                Text(
                    text = "${sheet.columns}x${sheet.rows} · ${sheet.frameWidth}px frames · " +
                        sheet.clips.joinToString(", ") { it.state.name.lowercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                )
            }
            StratumAction(
                label = "Delete",
                onClick = onDelete,
                emphasis = ActionEmphasis.DESTRUCTIVE,
            )
        }

        Spacer(Modifier.height(Space.medium))
        StratumDivider()
        Spacer(Modifier.height(Space.medium))

        // The sheet itself, at nearest-neighbour, so the player can judge
        // whether the frames line up before trusting it in the world.
        StratumWell(modifier = Modifier.fillMaxWidth()) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = "Sprite sheet for ${sheet.name}",
                    modifier = Modifier.fillMaxWidth().height(SHEET_PREVIEW_HEIGHT),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(SHEET_PREVIEW_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Image missing",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted,
                    )
                }
            }
        }
    }
}

private val SHEET_PREVIEW_HEIGHT = 140.dp
