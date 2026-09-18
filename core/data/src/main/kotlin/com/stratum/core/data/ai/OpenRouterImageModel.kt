package com.stratum.core.data.ai

import android.util.Base64
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.stratum.core.domain.ai.GeneratedImage
import com.stratum.core.domain.ai.GenerationAttempt
import com.stratum.core.domain.ai.GenerationException
import com.stratum.core.domain.ai.GenerationObserver
import com.stratum.core.domain.ai.GenerationStage
import com.stratum.core.domain.ai.ImageModelPort
import com.stratum.core.domain.ai.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Asks an OpenAI-compatible endpoint for an image.
 *
 * Providers disagree about how an image comes back even when they agree on the
 * request, so this tries the shapes in the order they actually occur: a base64
 * payload, a URL to fetch, or an image embedded in a chat reply. A sprite sheet
 * is worth the extra attempts -- failing because a provider answered in its own
 * dialect would be a poor reason to lose a generation.
 */
class OpenRouterImageModel(
    private val configProvider: () -> ProviderConfig,
    private val client: OkHttpClient = defaultClient(),
    private val moshi: Moshi = Moshi.Builder().build(),
) : ImageModelPort {

    override suspend fun generateImage(
        request: ImageRequest,
        observer: GenerationObserver,
    ): Result<GeneratedImage> =
        withContext(Dispatchers.IO) {
            observer.onStage(GenerationStage.PREPARING)
            val config = configProvider()
            if (config.apiKey.isBlank()) {
                observer.onStage(GenerationStage.FAILED)
                return@withContext Result.failure(
                    GenerationException("No API key is configured for ${config.displayName}"),
                )
            }

            val model = request.modelId ?: config.imageModel
            // Two different APIs, because they are two different operations.
            // Drawing from nothing is /images/generations; editing a picture
            // that already exists is a chat turn with the picture in it, which
            // is the only shape providers serve image editing in.
            val editing = request.references.isNotEmpty()
            val endpoint = if (editing) {
                "${config.baseUrl.trimEnd('/')}/chat/completions"
            } else {
                "${config.baseUrl.trimEnd('/')}/images/generations"
            }
            val payload = if (editing) {
                editPayload(model, request)
            } else {
                moshi.adapter(ImageRequestDto::class.java).toJson(
                    ImageRequestDto(
                        model = model,
                        prompt = request.prompt,
                        n = 1,
                        response_format = "b64_json",
                    ),
                )
            }
            // The key lives in a header and is never recorded. Everything else
            // is, because a rejection is only explicable if you can see exactly
            // what was asked for.
            val headers = mapOf(
                "Authorization" to "Bearer ****",
                "Content-Type" to "application/json",
                "HTTP-Referer" to config.refererUrl,
                "X-Title" to config.appTitle,
            )
            var status: Int? = null
            var responseBody: String? = null
            val startedAt = System.currentTimeMillis()

            val result = runCatching {
                val httpRequest = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", config.refererUrl)
                    .addHeader("X-Title", config.appTitle)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                observer.onStage(GenerationStage.SENDING)
                client.newCall(httpRequest).execute().use { response ->
                    observer.onStage(GenerationStage.READING)
                    status = response.code
                    val body = response.body?.string().orEmpty()
                    // Truncated: a successful reply is a megabyte of base64 and
                    // nobody needs to read that, but a failure is short.
                    responseBody = body.take(MAX_RECORDED_BODY)
                    if (!response.isSuccessful) {
                        throw failureFor(response.code, body, model)
                    }
                    observer.onStage(GenerationStage.DECODING)
                    val bytes = if (editing) editedImage(body, model) else drawnImage(body, model)
                    observer.onStage(GenerationStage.MEASURING)
                    toGeneratedImage(bytes, request)
                }
            }

            observer.onAttempt(
                GenerationAttempt(
                    id = "img_${'$'}startedAt",
                    label = if (editing) {
                        "Pose edit · ${'$'}{request.width}×${'$'}{request.height}"
                    } else {
                        "Image · ${'$'}{request.width}×${'$'}{request.height}"
                    },
                    endpoint = endpoint,
                    model = model,
                    // The reference image is a megabyte of base64 and the
                    // panel that shows this is for reading. The prompt is the
                    // part anyone debugging a bad pose needs to see.
                    requestBody = redactImageData(payload),
                    redactedHeaders = headers,
                    status = status,
                    responseBody = responseBody,
                    failure = result.exceptionOrNull()?.message,
                    durationMillis = System.currentTimeMillis() - startedAt,
                ),
            )
            observer.onStage(if (result.isSuccess) GenerationStage.DONE else GenerationStage.FAILED)
            // A socket that died halfway through is worth another go for the
            // same reason a 429 is: the request was fine, the moment was not.
            result.recoverCatching { cause ->
                throw if (cause is java.io.IOException) {
                    GenerationException(
                        cause.message ?: "The connection dropped.",
                        cause = cause,
                        retryable = true,
                    )
                } else {
                    cause
                }
            }
        }

    /**
     * A chat turn carrying the prompt and the picture to edit.
     *
     * `modalities` is what tells the provider an image is wanted back rather
     * than a description of one. Without it a perfectly capable image editor
     * answers a request to redraw a pose with a paragraph about how it would
     * redraw the pose.
     */
    private fun editPayload(model: String, request: ImageRequest): String {
        val parts = buildList {
            add(ContentPartDto(type = "text", text = request.prompt))
            request.references.forEach { reference ->
                val encoded = Base64.encodeToString(reference.bytes, Base64.NO_WRAP)
                add(
                    ContentPartDto(
                        type = "image_url",
                        image_url = ImageUrlDto("data:${reference.mimeType};base64,$encoded"),
                    ),
                )
            }
        }
        return moshi.adapter(EditRequestDto::class.java).toJson(
            EditRequestDto(
                model = model,
                messages = listOf(EditMessageDto(role = "user", content = parts)),
                modalities = listOf("image", "text"),
            ),
        )
    }

    /** The image from an /images/generations reply. */
    private fun drawnImage(body: String, model: String): ByteArray {
        val parsed = moshi.adapter(ImageResponseDto::class.java).fromJson(body)
        val first = parsed?.data?.firstOrNull()
            ?: throw GenerationException(
                "'$model' replied with no image. It is probably a text model.",
            )
        return when {
            !first.b64_json.isNullOrBlank() -> decodeBase64(first.b64_json)
            !first.url.isNullOrBlank() -> download(first.url)
            else -> throw GenerationException("The model's reply contained no image data")
        }
    }

    /**
     * The image from a chat reply.
     *
     * Providers put it in one of two places and there is no way to know which
     * in advance, so both are tried: the structured `images` array, and failing
     * that a data URI sitting in the text. The second is not a hypothetical --
     * it is what several providers do -- and falling back to it costs one
     * regular expression against a reply we already have.
     */
    private fun editedImage(body: String, model: String): ByteArray {
        val parsed = runCatching {
            moshi.adapter(EditResponseDto::class.java).fromJson(body)
        }.getOrNull()

        val message = parsed?.choices?.firstOrNull()?.message
        val fromImages = message?.images?.firstNotNullOfOrNull { it.image_url?.url }
        if (!fromImages.isNullOrBlank()) {
            return if (fromImages.startsWith("http")) download(fromImages) else decodeBase64(fromImages)
        }

        val inline = DATA_URI.find(body)?.value
        if (inline != null) return decodeBase64(inline)

        // A model that answered in words is the single most common way this
        // fails, and saying so beats "no image data" -- it names the fix, which
        // is to choose a model that edits images.
        val text = (message?.content as? String)?.take(MAX_RECORDED_BODY).orEmpty()
        throw GenerationException(
            if (text.isNotBlank()) {
                "'$model' replied with text rather than an image. It may not be an image " +
                    "editing model. It said: ${text.take(200)}"
            } else {
                "'$model' replied with no image data"
            },
        )
    }

    /**
     * Replaces embedded image payloads with their size.
     *
     * A recorded request is for a person to read. One that is 99% base64 is not
     * readable, and storing several of them per character is a real amount of
     * memory held for no purpose.
     */
    private fun redactImageData(payload: String): String =
        DATA_URI.replace(payload) { match ->
            val prefix = match.value.substringBefore("base64,") + "base64,"
            "$prefix<${match.value.length / 1024}KB of image omitted>"
        }

    private fun decodeBase64(encoded: String): ByteArray {
        // Providers variously send a bare payload or a full data URI.
        val payload = encoded.substringAfterLast("base64,", encoded)
        return runCatching { Base64.decode(payload, Base64.DEFAULT) }
            .getOrElse { throw GenerationException("The model's image could not be decoded", it) }
    }

    private fun download(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GenerationException("The image link the model returned could not be fetched")
            }
            return response.body?.bytes()
                ?: throw GenerationException("The image link returned nothing")
        }
    }

    /**
     * Measures the image rather than trusting the request: providers round to
     * their own supported sizes, and a sheet cut on the requested dimensions
     * would shear every frame.
     */
    private fun toGeneratedImage(bytes: ByteArray, request: ImageRequest): GeneratedImage {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth.takeIf { it > 0 } ?: request.width
        val height = options.outHeight.takeIf { it > 0 } ?: request.height
        return GeneratedImage(
            bytes = bytes,
            mimeType = options.outMimeType ?: "image/png",
            width = width,
            height = height,
        )
    }

    /**
     * The status code turned into something a run can act on.
     *
     * Three outcomes, not one. A rate limit or a provider wobble is worth
     * waiting out, because the next attempt usually works. A rejected key or a
     * model that does not exist will do exactly the same thing to every
     * remaining frame, so a long run should stop rather than spend four minutes
     * proving it. Everything else is this frame's problem alone: skip it, keep
     * going, report it at the end.
     */
    private fun failureFor(code: Int, body: String, model: String): GenerationException = when (code) {
        400 -> GenerationException("'$model' rejected the request. It may not be an image model.")
        401, 403 -> GenerationException(
            "The API key was rejected. Check it in settings.",
            fatal = true,
        )
        402 -> GenerationException(
            "The provider reports no remaining credit for this key.",
            fatal = true,
        )
        404 -> GenerationException("'$model' is not available on this provider.", fatal = true)
        408, 429 -> GenerationException(
            "The provider is rate limiting. Waiting before the next attempt.",
            retryable = true,
        )
        in 500..599 -> GenerationException(
            "The provider is having trouble (HTTP $code).",
            retryable = true,
        )
        else -> GenerationException(
            "The provider refused the request (HTTP $code): ${body.take(200)}",
        )
    }

    private companion object {
        /** Enough to read an error; far less than a base64 image. */
        const val MAX_RECORDED_BODY = 4000

        /** A data URI wherever it turns up: in a reply, or in our own request. */
        val DATA_URI = Regex("data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=]+")
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Image generation is slow even when it works.
            .readTimeout(240, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * No `size`.
 *
 * It looks like the obvious field to set and it is a trap. Providers round the
 * requested size to whatever they actually produce, so it was never load
 * bearing -- the returned image is measured rather than trusted, precisely
 * because of that. But a size a provider will not accept is not rounded, it is
 * refused: 512x512 comes back as a flat HTTP 400 from Google, which turns "an
 * image of a slightly different size" into "no image at all". Asking for
 * nothing and measuring what arrives cannot fail that way.
 */
@JsonClass(generateAdapter = true)
internal data class ImageRequestDto(
    val model: String,
    val prompt: String,
    val n: Int,
    val response_format: String,
)

@JsonClass(generateAdapter = true)
internal data class ImageResponseDto(val data: List<ImageDatumDto>?)

@JsonClass(generateAdapter = true)
internal data class ImageDatumDto(val b64_json: String?, val url: String?)

@JsonClass(generateAdapter = true)
internal data class EditRequestDto(
    val model: String,
    val messages: List<EditMessageDto>,
    /** Says an image is wanted back, not a description of one. */
    val modalities: List<String>,
)

@JsonClass(generateAdapter = true)
internal data class EditMessageDto(val role: String, val content: List<ContentPartDto>)

@JsonClass(generateAdapter = true)
internal data class ContentPartDto(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrlDto? = null,
)

@JsonClass(generateAdapter = true)
internal data class ImageUrlDto(val url: String)

@JsonClass(generateAdapter = true)
internal data class EditResponseDto(val choices: List<EditChoiceDto>?)

@JsonClass(generateAdapter = true)
internal data class EditChoiceDto(val message: EditReplyDto?)

// `content` is Any? because providers disagree about whether a reply's content
// is a string or a list of parts, and a wrong guess makes the whole reply
// unparseable -- including the image sitting next to it.
@JsonClass(generateAdapter = true)
internal data class EditReplyDto(
    val content: Any? = null,
    val images: List<ContentPartDto>? = null,
)
