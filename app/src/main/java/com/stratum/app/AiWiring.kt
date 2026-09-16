package com.stratum.app

import android.content.Context
import com.stratum.core.data.ai.OpenRouterLanguageModel
import com.stratum.core.data.settings.ProviderSettingsStore
import com.stratum.core.domain.ai.GenerateContentPackUseCase
import com.stratum.core.domain.ai.GenerateLoreUseCase

/**
 * Wires the generation use cases to a real provider.
 *
 * The settings store is read on every call rather than captured once, so
 * changing the key or the model in settings takes effect on the next
 * generation instead of the next app launch.
 */
class AiWiring(context: Context) {

    val settings = ProviderSettingsStore(context)

    private val languageModel = OpenRouterLanguageModel(configProvider = settings::load)

    val generateContentPack = GenerateContentPackUseCase(languageModel)

    val generateLore = GenerateLoreUseCase(languageModel)

    val modelCatalog = languageModel

    fun isConfigured(): Boolean = settings.isConfigured
}
