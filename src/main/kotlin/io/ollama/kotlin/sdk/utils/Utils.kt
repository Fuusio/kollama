package io.ollama.kotlin.sdk.utils

import io.ollama.kotlin.sdk.Constants
import io.ollama.kotlin.sdk.exceptions.OllamaResponseException
import io.ollama.kotlin.sdk.model.ErrorResponse
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.Base64
import kotlin.coroutines.coroutineContext

/**
 * Format the host URL to ensure it has the correct format.
 *
 * @param host The host URL to format
 * @return The formatted host URL
 */
fun formatHost(host: String?): String {
    if (host.isNullOrBlank()) {
        return Constants.DEFAULT_HOST
    }
    
    var formattedHost = host.trim()
    if (!formattedHost.startsWith("http://") && !formattedHost.startsWith("https://")) {
        formattedHost = "http://$formattedHost"
    }
    
    if (formattedHost.endsWith("/")) {
        formattedHost = formattedHost.substring(0, formattedHost.length - 1)
    }
    
    // Ensure the host does not already include /api
    if (!formattedHost.endsWith("/api")) {
        return formattedHost
    } else {
        return formattedHost.substring(0, formattedHost.length - 4)
    }
}

/**
 * Encode an image file or byte array as a base64 string.
 *
 * @param image The image as File, ByteArray, or base64 String
 * @return The base64-encoded image string
 */
suspend fun encodeImage(image: Any): String {
    return when (image) {
        is ByteArray -> Base64.getEncoder().encodeToString(image)
        is File -> {
            if (image.exists()) {
                Base64.getEncoder().encodeToString(image.readBytes())
            } else {
                throw IllegalArgumentException("Image file does not exist: ${image.absolutePath}")
            }
        }
        is String -> {
            val file = File(image)
            if (file.exists()) {
                Base64.getEncoder().encodeToString(file.readBytes())
            } else {
                // Assume it's already base64 encoded
                image
            }
        }
        else -> throw IllegalArgumentException("Unsupported image type: ${image::class.java.name}")
    }
}

/**
 * Creates a cancellable flow from a regular flow.
 * This is useful for streaming responses that need to be cancelled when no longer needed.
 *
 * @param T The type of data in the flow
 * @param block The suspend lambda that produces values for the flow
 * @return A new Flow that can be cancelled
 */
fun <T> cancellableFlow(block: suspend FlowCollector<T>.() -> Unit): Flow<T> = flow {
    try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) {
            coroutineContext.cancel()
        } else {
            throw e
        }
    }
}

/**
 * A custom exception for Flow cancellations.
 */
class CancellationException(message: String? = null, cause: Throwable? = null) : 
    Exception(message, cause)

/**
 * Parse the error response and throw an appropriate exception.
 *
 * @param status The HTTP status code
 * @param errorResponse The error response body
 * @throws OllamaResponseException with details from the error response
 */
fun handleErrorResponse(status: Int, errorResponse: ErrorResponse?) {
    val errorMessage = errorResponse?.error ?: "Unknown error occurred"
    throw OllamaResponseException(errorMessage, status)
} 