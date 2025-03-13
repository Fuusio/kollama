package org.fuusio.kollama.exceptions

/**
 * Base exception class for all Ollama SDK exceptions.
 */
open class OllamaException(message: String? = null, cause: Throwable? = null) : 
    Exception(message, cause)

/**
 * Exception thrown when there is an error in the response from the Ollama API.
 */
class OllamaResponseException(
    val error: String,
    val statusCode: Int,
    cause: Throwable? = null
) : OllamaException("Error $statusCode: $error", cause)

/**
 * Exception thrown when there is a network error communicating with the Ollama API.
 */
class OllamaNetworkException(
    message: String? = null,
    cause: Throwable? = null
) : OllamaException(message ?: "Network error communicating with Ollama API", cause)

/**
 * Exception thrown when there is an error configuring the Ollama SDK client.
 */
class OllamaConfigurationException(
    message: String? = null,
    cause: Throwable? = null
) : OllamaException(message ?: "Error configuring Ollama SDK client", cause)

/**
 * Exception thrown when there is an error parsing JSON response from the Ollama API.
 */
class OllamaSerializationException(
    message: String? = null,
    cause: Throwable? = null
) : OllamaException(message ?: "Error parsing JSON response from Ollama API", cause)

/**
 * Exception thrown when an operation is requested but streaming is not supported for it.
 */
class OllamaStreamingNotSupportedException(operation: String) : 
    OllamaException("Streaming is not supported for operation: $operation") 