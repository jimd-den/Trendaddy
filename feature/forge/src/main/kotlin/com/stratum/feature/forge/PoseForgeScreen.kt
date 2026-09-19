package com.stratum.feature.forge

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.designsystem.component.ActionEmphasis
import com.stratum.core.designsystem.component.SectionLabel
import com.stratum.core.designsystem.component.StratumAction
import com.stratum.core.designsystem.component.StratumChip
import com.stratum.core.designsystem.component.StratumMeter
import com.stratum.core.designsystem.component.StratumPanel
import com.stratum.core.designsystem.component.StratumSection
import com.stratum.core.designsystem.component.StratumWell
import com.stratum.core.designsystem.theme.Cut
import com.stratum.core.designsystem.theme.Space
import com.stratum.core.designsystem.theme.Stroke
import com.stratum.core.designsystem.theme.StratumTheme
import com.stratum.core.designsystem.theme.safeContent
import com.stratum.core.domain.ai.PoseStep
import com.stratum.core.domain.sprite.PoseGuideMode
import com.stratum.core.domain.sprite.PoseGuideStyle
import com.stratum.core.domain.sprite.SpriteValidation

/**
 * Builds a character from one good drawing.
 *
 * The premise is that the two hard problems are separable, and that only one of
 * them belongs to the model. Drawing a striking character once is something an
 * image model is genuinely good at. Drawing the *same* character forty times in
 * forty consistent poses is something it is bad at, and no prompt fixes that,
 * because nothing ties the forty calls together.
 *
 * So this screen draws the character once, large, in a T-pose where nothing is
 * hidden — and then hands that picture back to the model forty times, changing
 * only the pose. The identity comes from the pixels rather than from a
 * description, which is the one thing a model cannot misremember.
 *
 * Everything else here follows from the cost. Forty generations is minutes and
 * money, so: the reference is a separate step you approve before spending the
 * rest, poses are written to disk the instant they arrive, a failed frame does
 * not end the run, and a half-finished character can be packed into a playable
 * sheet at any point.
 */
@Composable
fun PoseForgeScreen(
    viewModel: PoseForgeViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** Asks the host to pick a pose file for this step. */
    onImportPose: (PoseStep) -> Unit = {},
    /** The reference drawing, decoded once per character. */
    referenceFor: (String) -> ImageBitmap? = { null },
    /** The packed sheet, once there is one. */
    sheetPreviewFor: (String) -> ImageBitmap? = { null },
) {
    LaunchedEffect(Unit) { viewModel.refresh() }

    val state by viewModel.state.collectAsStateWithLifecycle()

    // A 1024 pixel PNG decoded on every recomposition would make typing in the
    // subject field stutter. It changes only when the character does.
    val setId = state.setId
    val reference = remember(setId, state.hasReference) {
        if (setId != null && state.hasReference) referenceFor(setId) else null
    }
    val sheetImage = remember(state.savedSheet?.id, state.savedSheet) {
        state.savedSheet?.let { sheetPreviewFor(it.id) }
    }

    PoseForgeContent(
        state = state,
        reference = reference,
        sheetImage = sheetImage,
        modifier = modifier,
        onSubjectChange = viewModel::updateSubject,
        onStyleChange = viewModel::updateStyle,
        onScopeChange = viewModel::selectScope,
        onCellSizeChange = viewModel::selectCellSize,
        onDrawReference = viewModel::drawReferencePose,
        onBuildAnimations = viewModel::buildAnimations,
        onStop = viewModel::stop,
        onBuildSheet = viewModel::buildSheet,
        onRedrawPose = viewModel::redrawPose,
        onImportPose = onImportPose,
        onImportJson = viewModel::importGuideJson,
        onGuideModeChange = viewModel::selectGuideMode,
        onGuideStyleChange = viewModel::selectGuideStyle,
        onClearImported = viewModel::clearImported,
        onDismiss = viewModel::dismissMessage,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun PoseForgeContent(
    state: PoseForgeUiState,
    reference: ImageBitmap?,
    sheetImage: ImageBitmap?,
    modifier: Modifier = Modifier,
    onSubjectChange: (String) -> Unit = {},
    onStyleChange: (String) -> Unit = {},
    onScopeChange: (PoseScope) -> Unit = {},
    onCellSizeChange: (Int) -> Unit = {},
    onDrawReference: () -> Unit = {},
    onBuildAnimations: () -> Unit = {},
    onStop: () -> Unit = {},
    onBuildSheet: () -> Unit = {},
    onRedrawPose: (String) -> Unit = {},
    onGuideModeChange: (PoseGuideMode) -> Unit = {},
    onGuideStyleChange: (PoseGuideStyle) -> Unit = {},
    onClearImported: (PoseStep) -> Unit = {},
    onImportPose: (PoseStep) -> Unit = {},
    onImportJson: (PoseStep, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Pose forge")
            StratumAction(label = "Back", onClick = onBack, emphasis = ActionEmphasis.QUIET)
        }

        Spacer(Modifier.height(Space.medium))
        Text(
            text = "Draw the character once, big, in a T-pose. Then every animation frame is " +
                "that same picture handed back to the model with only the pose changed — " +
                "which is what keeps it the same character across forty frames.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )

        if (!state.providerConfigured) {
            Spacer(Modifier.height(Space.medium))
            StratumPanel(raised = false) {
                Text(
                    text = "No provider key is set, so nothing can be drawn yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                )
                Spacer(Modifier.height(Space.small))
                StratumAction(
                    label = "Settings",
                    onClick = onOpenSettings,
                    emphasis = ActionEmphasis.SECONDARY,
                )
            }
        }

        Notice(state.message, state.error, onDismiss)

        Spacer(Modifier.height(Space.large))
        CharacterPanel(state, onSubjectChange, onStyleChange, onScopeChange)

        Spacer(Modifier.height(Space.medium))
        ReferencePanel(state, reference, onDrawReference)

        Spacer(Modifier.height(Space.medium))
        GuidePanel(state, onGuideModeChange, onGuideStyleChange)

        Spacer(Modifier.height(Space.medium))
        AnimationPanel(
            state, onBuildAnimations, onStop, onRedrawPose, onImportPose, onClearImported,
            onImportJson,
        )

        Spacer(Modifier.height(Space.medium))
        SheetPanel(state, sheetImage, onCellSizeChange, onBuildSheet)

        Spacer(Modifier.height(Space.huge))
    }
}

@Composable
private fun CharacterPanel(
    state: PoseForgeUiState,
    onSubjectChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onScopeChange: (PoseScope) -> Unit,
) {
    val colors = StratumTheme.colors

    StratumSection(title = "Character") {
        OutlinedTextField(
            value = state.subject,
            onValueChange = onSubjectChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Subject") },
            placeholder = { Text("a bronze-masked warrior with a curved blade") },
            enabled = !state.busy,
            minLines = 2,
        )

        Spacer(Modifier.height(Space.small))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.small),
        ) {
            SpriteStyle.entries.forEach { preset ->
                StratumChip(
                    label = preset.label,
                    selected = state.style == preset.direction,
                    onClick = { onStyleChange(if (state.style == preset.direction) "" else preset.direction) },
                )
            }
        }

        Spacer(Modifier.height(Space.small))
        OutlinedTextField(
            value = state.style,
            onValueChange = onStyleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Style") },
            enabled = !state.busy,
            singleLine = false,
        )

        Spacer(Modifier.height(Space.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            PoseScope.entries.forEach { scope ->
                StratumChip(
                    label = scope.label,
                    selected = state.scope == scope,
                    onClick = { onScopeChange(scope) },
                )
            }
        }

        Spacer(Modifier.height(Space.small))
        // Said in generations rather than in states, because that is the number
        // that costs money and takes minutes.
        Text(
            text = "${state.scope.script.steps.size} poses, one generation each, " +
                "plus the reference.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun ReferencePanel(
    state: PoseForgeUiState,
    reference: ImageBitmap?,
    onDrawReference: () -> Unit,
) {
    val colors = StratumTheme.colors

    StratumSection(
        title = "Reference pose",
        subtitle = if (state.hasReference) {
            "Every frame below is edited from this drawing."
        } else {
            "A T-pose, so nothing is hidden. An editor can only keep what it can see."
        },
    ) {
        if (reference != null) {
            StratumWell {
                Image(
                    bitmap = reference,
                    contentDescription = "Reference pose",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = REFERENCE_HEIGHT)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium,
                )
            }
            Spacer(Modifier.height(Space.medium))
        }

        StratumAction(
            label = if (state.hasReference) "Draw it again" else "Draw reference",
            onClick = onDrawReference,
            emphasis = if (state.hasReference) ActionEmphasis.QUIET else ActionEmphasis.PRIMARY,
            enabled = !state.busy && state.providerConfigured && state.subject.isNotBlank(),
        )

        if (state.hasReference) {
            Spacer(Modifier.height(Space.small))
            Text(
                text = "Drawing it again replaces the reference but keeps the poses already " +
                    "made, which will then be of the old character. Redraw those too.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted,
            )
        }
    }
}

/**
 * Where the poses come from, and how they are drawn.
 *
 * Both sources stay because neither is strictly better. The built-in skeletons
 * know this game's camera, its frame counts and which hand holds the weapon; a
 * pose library knows real anatomy, observed from photographs, in far greater
 * quantity than anyone here is going to author. Words alone stays too, because
 * it is the only mode that works with a provider accepting one input image.
 */
@Composable
private fun GuidePanel(
    state: PoseForgeUiState,
    onGuideModeChange: (PoseGuideMode) -> Unit,
    onGuideStyleChange: (PoseGuideStyle) -> Unit,
) {
    val colors = StratumTheme.colors

    StratumSection(
        title = "Pose guides",
        subtitle = "A drawing of the pose alongside the character. Prose is a poor way to " +
            "specify a body.",
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.small),
        ) {
            PoseGuideMode.entries.forEach { mode ->
                StratumChip(
                    label = mode.label,
                    selected = state.guides.mode == mode,
                    onClick = { onGuideModeChange(mode) },
                )
            }
        }

        if (state.guides.mode != PoseGuideMode.NONE) {
            Spacer(Modifier.height(Space.small))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Text(
                    text = "Drawn as",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                )
                PoseGuideStyle.entries.forEach { style ->
                    StratumChip(
                        label = style.label,
                        selected = state.guides.style == style,
                        onClick = { onGuideStyleChange(style) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.small))
        Text(
            text = when (state.guides.mode) {
                PoseGuideMode.NONE ->
                    "Only the written instruction is sent. The weapon still gets rigged from " +
                        "the built-in skeleton."
                PoseGuideMode.BUILT_IN ->
                    "The built-in skeletons, which know this camera and which hand holds the " +
                        "weapon."
                PoseGuideMode.IMPORTED ->
                    "${state.importedCount} of ${state.total} frames have an imported pose. " +
                        "The rest fall back to the built-in one."
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
        )

        if (state.guides.style == PoseGuideStyle.OPENPOSE) {
            Spacer(Modifier.height(Space.small))
            Text(
                text = "The canonical OpenPose rendering: coloured limbs on black. Every model " +
                    "trained alongside a ControlNet preprocessor has seen this exact palette.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun AnimationPanel(
    state: PoseForgeUiState,
    onBuildAnimations: () -> Unit,
    onStop: () -> Unit,
    onRedrawPose: (String) -> Unit,
    onImportPose: (PoseStep) -> Unit = {},
    onClearImported: (PoseStep) -> Unit = {},
    onImportJson: (PoseStep, String) -> Unit = { _, _ -> },
) {
    // Which frame a pasted pose is destined for. Pasting is the exact route:
    // reading a rendered skeleton back depends on the library having used the
    // canonical palette, and a real one checked does not.
    var pasteInto by remember { mutableStateOf<PoseStep?>(null) }
    var pasted by remember { mutableStateOf("") }

    val target = pasteInto
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pasteInto = null },
            title = { Text("Paste pose keypoints") },
            text = {
                Column {
                    Text(
                        text = "OpenPose JSON for ${target.key}. Seventeen or eighteen " +
                            "keypoints, however the library writes them.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(Space.small))
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onImportJson(target, pasted)
                    pasted = ""
                    pasteInto = null
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pasteInto = null }) { Text("Cancel") }
            },
        )
    }
    val colors = StratumTheme.colors

    StratumSection(
        title = "Animations",
        subtitle = state.currentLabel?.let { "Drawing $it…" }
            ?: "${state.completed} of ${state.total} poses drawn",
    ) {
        StratumMeter(
            label = "Progress",
            value = state.completed,
            max = state.total.coerceAtLeast(1),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.medium))

        state.script.states.forEach { animation ->
            val steps = state.script.stepsFor(animation)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = SpriteValidation.name(animation),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                    modifier = Modifier.weight(WIDTH_OF_LABEL),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
                    steps.forEach { step ->
                        PoseDot(
                            label = "${step.index + 1}" + if (state.isImported(step)) "*" else "",
                            drawn = step.key in state.drawn,
                            failed = step.key in state.failures,
                            active = state.currentStep?.key == step.key,
                            // In imported mode a tap picks a pose file for the
                            // frame; otherwise it throws the drawing away, which
                            // is how one bad frame out of forty gets fixed
                            // without redrawing the other thirty-nine.
                            onClick = {
                                when {
                                    state.guides.mode == PoseGuideMode.IMPORTED &&
                                        state.isImported(step) -> onClearImported(step)
                                    state.guides.mode == PoseGuideMode.IMPORTED ->
                                        onImportPose(step)
                                    step.key in state.drawn -> onRedrawPose(step.key)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (state.guides.mode == PoseGuideMode.IMPORTED) {
            Spacer(Modifier.height(Space.small))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Text(
                    text = "Import into",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                )
                state.script.steps.forEach { step ->
                    StratumChip(
                        label = step.key,
                        selected = state.isImported(step),
                        onClick = { pasteInto = step },
                    )
                }
            }
            Spacer(Modifier.height(Space.tight))
            Text(
                text = "Tap a frame above to pick a skeleton PNG, or a name here to paste its " +
                    "keypoints. Pasting is exact; reading a PNG back only works when the " +
                    "library drew it in the standard OpenPose colours.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted,
            )
        }

        Spacer(Modifier.height(Space.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            StratumAction(
                label = if (state.completed == 0) "Build animations" else "Draw what is missing",
                onClick = onBuildAnimations,
                emphasis = ActionEmphasis.PRIMARY,
                enabled = state.canBuildAnimations,
            )
            if (state.busy) {
                StratumAction(
                    label = "Stop",
                    onClick = onStop,
                    emphasis = ActionEmphasis.DESTRUCTIVE,
                )
            }
        }

        if (state.failures.isNotEmpty()) {
            Spacer(Modifier.height(Space.small))
            Text(
                text = state.failures.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.danger,
            )
        }
    }
}

/** One pose, as a square that says drawn, failed, being drawn, or not yet. */
@Composable
private fun PoseDot(
    label: String,
    drawn: Boolean,
    failed: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = StratumTheme.colors
    val tint = when {
        active -> colors.accentAlt
        failed -> colors.danger
        drawn -> colors.accent
        else -> colors.hairline
    }

    Box(
        modifier = Modifier
            .clip(Cut.tiny)
            .background(if (drawn) tint.copy(alpha = 0.22f) else colors.surfaceSunken)
            .border(Stroke.hairline, tint, Cut.tiny)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.small, vertical = Space.tight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (drawn) colors.ink else colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SheetPanel(
    state: PoseForgeUiState,
    sheetImage: ImageBitmap?,
    onCellSizeChange: (Int) -> Unit,
    onBuildSheet: () -> Unit,
) {
    val colors = StratumTheme.colors

    StratumSection(
        title = "Sheet",
        subtitle = "Every pose keyed, scaled by one factor and stood on one baseline.",
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.small),
        ) {
            Text(
                text = "Frame height",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted,
            )
            PoseForgeViewModel.CELL_SIZES.forEach { pixels ->
                StratumChip(
                    label = "${pixels}px",
                    selected = state.cellSize == pixels,
                    onClick = { onCellSizeChange(pixels) },
                )
            }
        }

        Spacer(Modifier.height(Space.small))
        Text(
            text = "Frame width is taken from the character once the poses have been " +
                "measured, so a tall figure is not padded out with empty background. " +
                "The poses are kept at full size, so this can be packed again at another " +
                "height later without drawing anything twice.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
        )

        if (sheetImage != null) {
            Spacer(Modifier.height(Space.medium))
            StratumWell {
                Image(
                    bitmap = sheetImage,
                    contentDescription = "Packed sheet",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SHEET_HEIGHT),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            }
        }

        Spacer(Modifier.height(Space.medium))
        StratumAction(
            label = "Build sheet",
            onClick = onBuildSheet,
            emphasis = ActionEmphasis.PRIMARY,
            enabled = state.canBuildSheet,
        )

        if (state.completed in 1 until state.total) {
            Spacer(Modifier.height(Space.small))
            Text(
                text = "Packing a half-finished set is fine: a character with an idle and a " +
                    "walk is playable, and the rest can be added later.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun Notice(message: String?, error: String?, onDismiss: () -> Unit) {
    if (message == null && error == null) return
    val colors = StratumTheme.colors

    Spacer(Modifier.height(Space.medium))
    StratumPanel(raised = false) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = error ?: message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) colors.danger else colors.ink,
                modifier = Modifier.weight(1f),
            )
            StratumAction(label = "OK", onClick = onDismiss, emphasis = ActionEmphasis.QUIET)
        }
    }
}

private val REFERENCE_HEIGHT = 320.dp
private val SHEET_HEIGHT = 240.dp
/** Enough for "special" without pushing the pose squares off a narrow phone. */
private const val WIDTH_OF_LABEL = 0.3f
