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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.designsystem.component.ActionEmphasis
import com.stratum.core.designsystem.component.SectionLabel
import com.stratum.core.designsystem.component.StratumAction
import com.stratum.core.designsystem.component.StratumChip
import com.stratum.core.designsystem.component.StratumDivider
import com.stratum.core.designsystem.component.StratumPanel
import com.stratum.core.designsystem.component.StratumSection
import com.stratum.core.designsystem.component.StratumWell
import com.stratum.core.designsystem.theme.Cut
import com.stratum.core.designsystem.theme.Space
import com.stratum.core.designsystem.theme.Stroke
import com.stratum.core.designsystem.theme.StratumTheme
import com.stratum.core.designsystem.theme.safeContent
import com.stratum.core.domain.sprite.WeaponKind

/**
 * Draw a weapon on its own, so anyone can carry it.
 *
 * The argument for a screen of its own is the same as the argument for the
 * mechanism: a weapon drawn into a character belongs to that character forever.
 * It cannot be dropped, cannot be swapped for a better one, and has to be drawn
 * again for every actor that carries one — which in a game whose loop is
 * picking up better loot is exactly backwards.
 *
 * It is also how the vanishing sword got fixed. A weapon held in a character's
 * reference pose disappeared the moment the pose changed, because an image
 * editor reads a held object as part of the pose and drops it with the old one.
 * A weapon that was never in the reference cannot be lost from it.
 */
@Composable
fun WeaponForgeScreen(
    viewModel: WeaponForgeViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEquip: (String?) -> Unit = {},
    equippedId: String? = null,
    previewFor: (String) -> ImageBitmap? = { null },
) {
    LaunchedEffect(Unit) { viewModel.refresh() }

    val state by viewModel.state.collectAsStateWithLifecycle()

    WeaponForgeContent(
        state = state.copy(equippedId = equippedId),
        modifier = modifier,
        onSubjectChange = viewModel::updateSubject,
        onKindChange = viewModel::selectKind,
        onStyleChange = viewModel::updateStyle,
        onGenerate = viewModel::generate,
        onDelete = viewModel::delete,
        onEquip = onEquip,
        onDismiss = viewModel::dismissMessage,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        previewFor = previewFor,
    )
}

@Composable
fun WeaponForgeContent(
    state: WeaponForgeUiState,
    modifier: Modifier = Modifier,
    onSubjectChange: (String) -> Unit = {},
    onKindChange: (WeaponKind) -> Unit = {},
    onStyleChange: (String) -> Unit = {},
    onGenerate: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onEquip: (String?) -> Unit = {},
    onDismiss: () -> Unit = {},
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Weapon forge")
            StratumAction(label = "Back", onClick = onBack, emphasis = ActionEmphasis.QUIET)
        }

        Spacer(Modifier.height(Space.medium))
        Text(
            text = "Weapons are drawn on their own, upright, and attached to the hand at " +
                "draw time. One sword serves every character, swings with every attack, and " +
                "can be swapped for a better one without redrawing anybody.",
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

        if (state.message != null || state.error != null) {
            Spacer(Modifier.height(Space.medium))
            StratumPanel(raised = false) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = state.error ?: state.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) colors.danger else colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    StratumAction(label = "OK", onClick = onDismiss, emphasis = ActionEmphasis.QUIET)
                }
            }
        }

        Spacer(Modifier.height(Space.large))

        StratumSection(
            title = "Draw one",
            subtitle = "The kind decides where the hand grips it and how far it reaches.",
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                WeaponKind.entries.forEach { kind ->
                    StratumChip(
                        label = kind.label,
                        selected = state.kind == kind,
                        onClick = { onKindChange(kind) },
                    )
                }
            }

            Spacer(Modifier.height(Space.medium))
            OutlinedTextField(
                value = state.subject,
                onValueChange = onSubjectChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Weapon") },
                placeholder = { Text("a bronze blade with Nsibidi glyphs down the fuller") },
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
                        onClick = {
                            onStyleChange(if (state.style == preset.direction) "" else preset.direction)
                        },
                    )
                }
            }

            Spacer(Modifier.height(Space.medium))
            StratumAction(
                label = if (state.busy) "Drawing" else "Draw weapon",
                onClick = onGenerate,
                emphasis = ActionEmphasis.PRIMARY,
                enabled = !state.busy && state.providerConfigured && state.subject.isNotBlank(),
            )
        }

        Spacer(Modifier.height(Space.medium))

        StratumSection(
            title = "Armoury",
            subtitle = if (state.weapons.isEmpty()) {
                "Nothing drawn yet."
            } else {
                "Tap one to put it in the player's hand."
            },
        ) {
            state.weapons.forEach { weapon ->
                val equipped = weapon.id == state.equippedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Cut.small)
                        .clickable { onEquip(if (equipped) null else weapon.id) }
                        .padding(vertical = Space.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.medium),
                ) {
                    Box(
                        modifier = Modifier
                            .size(WEAPON_THUMB)
                            .clip(Cut.tiny)
                            .background(colors.surfaceSunken)
                            .border(
                                Stroke.hairline,
                                if (equipped) colors.accent else colors.hairline,
                                Cut.tiny,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val image = previewFor(weapon.id)
                        if (image != null) {
                            Image(
                                bitmap = image,
                                contentDescription = weapon.name,
                                modifier = Modifier.fillMaxSize().padding(Space.tight),
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.Medium,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = weapon.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${weapon.kind.label} · ${weapon.width}x${weapon.height}" +
                                if (equipped) " · in hand" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (equipped) colors.accent else colors.inkMuted,
                        )
                    }
                    StratumAction(
                        label = "Delete",
                        onClick = { onDelete(weapon.id) },
                        emphasis = ActionEmphasis.DESTRUCTIVE,
                    )
                }
                StratumDivider()
            }

            if (state.weapons.isNotEmpty()) {
                Spacer(Modifier.height(Space.medium))
                StratumWell {
                    Text(
                        text = "A weapon is drawn upright and turned through the swing, so the " +
                            "same drawing reads as a wind-up, a strike and a follow-through. " +
                            "It passes behind the shoulder on the way up and across the front " +
                            "on the way down.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.huge))
    }
}

private val WEAPON_THUMB = 56.dp
