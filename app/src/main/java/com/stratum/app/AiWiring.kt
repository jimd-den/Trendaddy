package com.stratum.app

import android.content.Context
import com.stratum.core.data.ai.OpenRouterImageModel
import com.stratum.core.data.ai.OpenRouterLanguageModel
import com.stratum.core.data.sprite.SpriteLibrary
import com.stratum.core.data.sprite.PoseLibrary
import com.stratum.core.data.sprite.SpriteProjectStore
import com.stratum.core.data.settings.ProviderSettingsStore
import com.stratum.core.domain.ai.GenerateContentPackUseCase
import com.stratum.core.domain.ai.GenerateLoreUseCase
import com.stratum.core.domain.ai.GenerateBasePoseUseCase
import com.stratum.core.domain.ai.GeneratePoseFrameUseCase
import com.stratum.core.domain.ai.GenerateSpriteSheetUseCase

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

    private val imageModel = OpenRouterImageModel(configProvider = settings::load)

    /** Generated sheets live on the device, keyed by id. */
    val sprites = SpriteLibrary(context)

    /**
     * Hand-mapped atlases and the art they were mapped from, kept apart from
     * the baked sheets so re-cutting one never destroys the image it came from.
     */
    val spriteProjects = SpriteProjectStore(context)

    /**
     * The full-size poses a character was built from, kept so a set can be
     * resumed after a failure and re-packed at another frame size without
     * paying for a single generation twice.
     */
    val poses = PoseLibrary(context)

    val generateContentPack = GenerateContentPackUseCase(languageModel)

    val generateLore = GenerateLoreUseCase(languageModel)

    val generateSpriteSheet = GenerateSpriteSheetUseCase(imageModel)

    /** The one drawing a character is built from, and the edits that animate it. */
    val generateBasePose = GenerateBasePoseUseCase(imageModel)

    val generatePoseFrame = GeneratePoseFrameUseCase(imageModel)

    val modelCatalog = languageModel

    fun isConfigured(): Boolean = settings.isConfigured
}
