package com.stratum.core.domain.ai

/**
 * The capability of asking a model for text. One method, because that is all the
 * domain needs: which provider answers, how it authenticates and how it retries
 * are transport concerns that belong in the data layer.
 */
interface LanguageModelPort {
    suspend fun complete(
        request: CompletionRequest,
        observer: GenerationObserver = GenerationObserver.None,
    ): Result<String>
}

/**
 * The capability of asking a model for an image, returned as raw bytes rather
 * than a bitmap so the domain stays free of Android graphics.
 */
interface ImageModelPort {
    /**
     * [observer] is how the adapter reports what it is doing and exactly what it
     * sent. Defaulted so a caller that does not care is unaffected, but the
     * sprite forge passes a real one: an image model that rejects a sheet is
     * only debuggable if you can read the request and the provider's reply.
     */
    suspend fun generateImage(
        request: ImageRequest,
        observer: GenerationObserver = GenerationObserver.None,
    ): Result<GeneratedImage>
}

data class CompletionRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val temperature: Float = 0.8f,
    /** Null lets the adapter use whichever model the player configured. */
    val modelId: String? = null,
    val maxTokens: Int = 4096,
)

data class ImageRequest(
    val prompt: String,
    val modelId: String? = null,
    val width: Int = 512,
    val height: Int = 512,
    /** Transparent output is non-negotiable for sprite sheets. */
    val requireTransparency: Boolean = true,
    /**
     * Images handed to the model along with the prompt.
     *
     * This is what turns a generator into an editor, and it is the only
     * reliable way to get a *character* rather than a series of strangers. Ask
     * a model for eight poses of a bronze warrior in eight calls and you get
     * eight different warriors; hand it the warrior each time and ask only for
     * the pose to change, and it is the same one.
     */
    val references: List<ImageReference> = emptyList(),
)

/** An image sent to the model, rather than one it sent back. */
data class ImageReference(val bytes: ByteArray, val mimeType: String = "image/png") {
    // ByteArray compares by identity, which would make two copies of the same
    // reference unequal and quietly break every test that checks what was sent.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ImageReference && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
}

data class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
) {
    // ByteArray uses identity equality, which would make two identical images
    // compare unequal and quietly break caching and tests.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedImage) return false
        return mimeType == other.mimeType &&
            width == other.width &&
            height == other.height &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/** Describes a model the player can pick, as reported by the provider. */
data class ModelDescriptor(
    val id: String,
    val name: String,
    val description: String = "",
    val isFree: Boolean = false,
    val producesImages: Boolean = false,
    val acceptsImages: Boolean = false,
    val pricingLabel: String = "",
)

/** Lets the settings screen list what the configured provider actually offers. */
interface ModelCatalogPort {
    suspend fun availableModels(): Result<List<ModelDescriptor>>
}

/** Raised when a model answers with something that is not usable content. */
class GenerationException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
