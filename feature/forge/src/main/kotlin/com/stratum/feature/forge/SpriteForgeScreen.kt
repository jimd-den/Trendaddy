package com.stratum.feature.forge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.stratum.core.domain.ai.GenerationStage
import com.stratum.core.domain.sprite.KeyStrategy
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
    onToggleDetails: () -> Unit = {},
    previewFor: (String) -> ImageBitmap? = { null },
) {
    // Re-read the provider on every visit. The view model outlives this screen,
    // so without this a key saved in settings a moment ago is still reported as
    // missing until the app is restarted.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    SpriteForgeContent(
        state = state,
        modifier = modifier,
        onSubjectChange = viewModel::updateSubject,
        onStyleChange = viewModel::updateStyle,
        onStylePreset = viewModel::selectStyle,
        onTargetChange = viewModel::selectTarget,
        onGenerate = viewModel::generate,
        onDelete = viewModel::delete,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onToggleDetails = viewModel::toggleDetails,
        previewFor = previewFor,
    )
}

@Composable
fun SpriteForgeContent(
    state: SpriteForgeUiState,
    modifier: Modifier = Modifier,
    onSubjectChange: (String) -> Unit = {},
    onStyleChange: (String) -> Unit = {},
    onStylePreset: (SpriteStyle) -> Unit = {},
    onTargetChange: (SpriteTarget) -> Unit = {},
    onGenerate: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleDetails: () -> Unit = {},
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

            // A model given no style draws a picture, and a picture is not a
            // sprite. Presets make the useful answer one tap rather than a
            // blank field the player is expected to know how to fill.
            Row(
                // Four chips do not fit across a narrow phone, and a wrapped
                // chip row is worse than a scrolled one here: the field below
                // must stay where the eye expects it.
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                SpriteStyle.entries.forEach { preset ->
                    StratumChip(
                        label = preset.label,
                        selected = state.style == preset.direction,
                        onClick = { onStylePreset(preset) },
                    )
                }
            }

            Spacer(Modifier.height(Space.small))

            OutlinedTextField(
                value = state.style,
                onValueChange = onStyleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Style") },
                placeholder = { Text("chunky pixel art, limited palette") },
                enabled = !state.busy,
                minLines = 2,
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
                // The stage the adapter reported, not a guess. Which step it is
                // stuck on is the difference between "slow" and "broken".
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.surfaceSunken,
                )
                Spacer(Modifier.height(Space.small))
                Text(
                    text = state.stageLabel?.let { "$it…" }
                        ?: "Image models are slow. This can take a minute.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink,
                )
                if (state.stage == GenerationStage.WAITING || state.stage == GenerationStage.SENDING) {
                    Text(
                        text = "Image models are slow. This can take a minute.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted,
                    )
                }
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

        // Models answer a request for transparency by drawing a checkerboard
        // often enough that the player should be told when we had to undo it.
        state.keyStrategy?.let { strategy ->
            val note = when (strategy) {
                KeyStrategy.CHECKERBOARD ->
                    "The model drew a checkerboard instead of being transparent. Removed."
                KeyStrategy.SOLID -> "A solid background was removed."
                KeyStrategy.ALREADY_TRANSPARENT -> null
                KeyStrategy.NONE -> "No background could be identified; the sheet was kept as drawn."
            }
            note?.let {
                Spacer(Modifier.height(Space.small))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (strategy == KeyStrategy.NONE) colors.danger else colors.inkMuted,
                )
            }
        }

        // What was actually sent and what actually came back. A model that
        // rejects a sheet almost always says why, and summarising that into
        // "the request was rejected" throws away the only useful part.
        state.attempt?.let { attempt ->
            Spacer(Modifier.height(Space.medium))
            StratumPanel(modifier = Modifier.fillMaxWidth(), raised = false) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (attempt.succeeded) "Provider call" else "Provider rejected it",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (attempt.succeeded) colors.inkMuted else colors.danger,
                        )
                        Text(
                            text = attempt.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink,
                        )
                    }
                    StratumAction(
                        label = if (state.detailsOpen) "Hide" else "Details",
                        onClick = onToggleDetails,
                        emphasis = ActionEmphasis.QUIET,
                    )
                }

                if (state.detailsOpen) {
                    Spacer(Modifier.height(Space.small))
                    DetailBlock("Endpoint", attempt.endpoint)
                    DetailBlock("Model", attempt.model)
                    DetailBlock(
                        "Headers",
                        attempt.redactedHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                    )
                    DetailBlock("Request", attempt.requestBody)
                    attempt.responseBody?.let { DetailBlock("Response", it) }
                    attempt.failure?.let { DetailBlock("Failure", it) }
                }
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

/**
 * One labelled block of raw provider text, in a monospace-ish slab.
 *
 * Deliberately selectable and unwrapped-looking: this is evidence, not prose,
 * and the useful thing to do with it is read it or paste it somewhere.
 */
@Composable
private fun DetailBlock(label: String, value: String) {
    val colors = StratumTheme.colors
    Spacer(Modifier.height(Space.small))
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.inkMuted,
    )
    SelectionContainer {
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink,
            // No inner scroll: the page already scrolls, and nesting two
            // vertical scrollers makes the content fight over gestures and
            // bleed through itself. Payloads are truncated at the source.
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceSunken)
                .padding(Space.small),
        )
    }
}
