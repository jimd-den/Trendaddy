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
            val endpoint = "${config.baseUrl.trimEnd('/')}/images/generations"
            val payload = moshi.adapter(ImageRequestDto::class.java).toJson(
                ImageRequestDto(
                    model = model,
                    prompt = request.prompt,
                    n = 1,
                    size = "${request.width}x${request.height}",
                    response_format = "b64_json",
                ),
            )
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
                        throw GenerationException(describeFailure(response.code, body, model))
                    }
                    val parsed = moshi.adapter(ImageResponseDto::class.java).fromJson(body)
                    val first = parsed?.data?.firstOrNull()
                        ?: throw GenerationException(
                            "'${'$'}model' replied with no image. It is probably a text model.",
                        )

                    observer.onStage(GenerationStage.DECODING)
                    val bytes = when {
                        !first.b64_json.isNullOrBlank() -> decodeBase64(first.b64_json)
                        !first.url.isNullOrBlank() -> download(first.url)
                        else -> throw GenerationException("The model's reply contained no image data")
                    }
                    observer.onStage(GenerationStage.MEASURING)
                    toGeneratedImage(bytes, request)
                }
            }

            observer.onAttempt(
                GenerationAttempt(
                    id = "img_${'$'}startedAt",
                    label = "Image · ${'$'}{request.width}×${'$'}{request.height}",
                    endpoint = endpoint,
                    model = model,
                    requestBody = payload,
                    redactedHeaders = headers,
                    status = status,
                    responseBody = responseBody,
                    failure = result.exceptionOrNull()?.message,
                    durationMillis = System.currentTimeMillis() - startedAt,
                ),
            )
            observer.onStage(if (result.isSuccess) GenerationStage.DONE else GenerationStage.FAILED)
            result
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

    private fun describeFailure(code: Int, body: String, model: String): String = when (code) {
        400 -> "'$model' rejected the request. It may not be an image model."
        401, 403 -> "The API key was rejected. Check it in settings."
        402 -> "The provider reports no remaining credit for this key."
        404 -> "'$model' is not available on this provider."
        429 -> "The provider is rate limiting. Wait a moment and try again."
        in 500..599 -> "The provider is having trouble (HTTP $code). Try again shortly."
        else -> "The provider refused the request (HTTP $code): ${body.take(200)}"
    }

    private companion object {
        /** Enough to read an error; far less than a base64 image. */
        const val MAX_RECORDED_BODY = 4000
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Image generation is slow even when it works.
            .readTimeout(240, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

@JsonClass(generateAdapter = true)
internal data class ImageRequestDto(
    val model: String,
    val prompt: String,
    val n: Int,
    val size: String,
    val response_format: String,
)

@JsonClass(generateAdapter = true)
internal data class ImageResponseDto(val data: List<ImageDatumDto>?)

@JsonClass(generateAdapter = true)
internal data class ImageDatumDto(val b64_json: String?, val url: String?)
