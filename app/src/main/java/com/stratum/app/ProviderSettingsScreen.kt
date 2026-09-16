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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.stratum.core.data.ai.ProviderConfig
import com.stratum.core.designsystem.component.ActionEmphasis
import com.stratum.core.designsystem.component.SectionLabel
import com.stratum.core.designsystem.component.StratumAction
import com.stratum.core.designsystem.component.StratumPanel
import com.stratum.core.designsystem.theme.Space
import com.stratum.core.designsystem.theme.StratumTheme

/**
 * Where the player points the game at a model provider.
 *
 * The key is their own credential for their own account. It is stored on the
 * device and sent only to the endpoint they configured here, which the screen
 * says plainly rather than burying in a policy.
 */
@Composable
fun ProviderSettingsScreen(
    initial: ProviderConfig,
    onSave: (ProviderConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumTheme.colors
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(Space.large),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("Model provider")
            StratumAction(label = "Back", onClick = onBack, emphasis = ActionEmphasis.QUIET)
        }

        Spacer(Modifier.height(Space.medium))

        Text(
            text = "Stratum works with any OpenAI-compatible endpoint: OpenRouter, a hosted " +
                "provider, or a model running on your own machine. Your key is kept on this " +
                "device and sent only to the endpoint below.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(Space.large))

        StratumPanel(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(Space.medium))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
            )
            Spacer(Modifier.height(Space.medium))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint") },
                singleLine = true,
            )

            Spacer(Modifier.height(Space.large))

            StratumAction(
                label = if (saved) "Saved" else "Save",
                onClick = {
                    onSave(
                        ProviderConfig(
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                            baseUrl = baseUrl.trim(),
                        ),
                    )
                    saved = true
                },
                emphasis = ActionEmphasis.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
