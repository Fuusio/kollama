package io.ollama.kotlin.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ollama.kotlin.sdk.api.OllamaAPI
import io.ollama.kotlin.sdk.exceptions.*
import io.ollama.kotlin.sdk.model.*
import io.ollama.kotlin.sdk.utils.*
import io.ollama.kotlin.sdk.utils.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * A serializer for Any type that handles JSON conversion
 */
object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")
    
    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("This serializer can only be used with JSON")
        
        when (value) {
            is String -> jsonEncoder.encodeJsonElement(JsonPrimitive(value))
            is Number -> jsonEncoder.encodeJsonElement(JsonPrimitive(value))
            is Boolean -> jsonEncoder.encodeJsonElement(JsonPrimitive(value))
            is Map<*, *> -> {
                val jsonObject = value.entries.associate { 
                    it.key.toString() to JsonPrimitive(it.value.toString())
                }
                jsonEncoder.encodeJsonElement(JsonObject(jsonObject))
            }
            is List<*> -> {
                val jsonArray = value.map { JsonPrimitive(it.toString()) }
                jsonEncoder.encodeJsonElement(JsonArray(jsonArray))
            }
            null -> jsonEncoder.encodeJsonElement(JsonPrimitive("null"))
            else -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.toString()))
        }
    }
    
    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer can only be used with JSON")
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.content == "true" -> true
                element.content == "false" -> false
                element.content == "null" -> "null"
                else -> try {
                    element.content.toDouble()
                } catch (e: NumberFormatException) {
                    element.content
                }
            }
            is JsonObject -> element.toMap()
            is JsonArray -> element.toList()
            else -> element.toString()
        }
    }
}

/**
 * Main implementation of the Ollama API client.
 * Provides methods to interact with the Ollama API.
 */
class OllamaClient(
    config: OllamaConfig? = null
) : OllamaAPI {
    private val config: OllamaConfig = config ?: OllamaConfig(
        host = Constants.DEFAULT_HOST
    )
    private val client: HttpClient
    private val utils = object {
        suspend fun encodeImage(image: Any): String = io.ollama.kotlin.sdk.utils.encodeImage(image)
    }

    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
        allowStructuredMapKeys = true
        classDiscriminator = "type"
        useArrayPolymorphism = false
        serializersModule = SerializersModule {
            contextual(Any::class, AnySerializer)
        }
    }
    private val activeStreams = mutableListOf<Job>()
    
    init {
        client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = this@OllamaClient.config.timeoutMs
                connectTimeoutMillis = this@OllamaClient.config.timeoutMs
                socketTimeoutMillis = this@OllamaClient.config.timeoutMs
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 3)
                exponentialDelay(
                    maxDelayMs = 5000L,
                    randomizationMs = 1000L
                )
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                this@OllamaClient.config.headers.forEach { (key, value) ->
                    header(key, value)
                }
                headers {
                    append(HttpHeaders.UserAgent, "ollama-kotlin/${Constants.VERSION}")
                }
            }
            expectSuccess = true
        }
    }

    /**
     * Creates the full API endpoint URL.
     *
     * @param endpoint The API endpoint path
     * @return The complete URL string
     */
    private fun createUrl(endpoint: String): String {
        val host = config.host
        return if (host.endsWith("/")) {
            "${host}${endpoint.removePrefix("/")}"
        } else {
            "${host}/${endpoint.removePrefix("/")}"
        }
    }

    /**
     * Makes a POST request to the Ollama API.
     *
     * @param endpoint The API endpoint
     * @param body The request body
     * @return The HttpResponse
     */
    private suspend inline fun <reified T, reified R> makeRequest(
        method: HttpMethod,
        endpoint: String,
        body: T? = null,
        noinline onDownloadProgress: ((Long, Long) -> Unit)? = null,
        noinline block: (suspend () -> R)? = null
    ): R {
        val url = createUrl(endpoint)
        
        logger.debug { "Making $method request to $url" }
        
        try {
            if (body != null) {
                // Log request body based on type for better debugging
                when (body) {
                    is ChatRequest -> logger.debug { "Request body for chat: ${json.encodeToString(ChatRequest.serializer(), body)}" }
                    is GenerateRequest -> logger.debug { "Request body for generate: ${json.encodeToString(GenerateRequest.serializer(), body)}" }
                    is EmbeddingRequest -> logger.debug { "Request body for embedding: ${json.encodeToString(EmbeddingRequest.serializer(), body)}" }
                    is ShowRequest -> logger.debug { "Request body for show: ${json.encodeToString(ShowRequest.serializer(), body)}" }
                    is CreateRequest -> logger.debug { "Request body for create: ${json.encodeToString(CreateRequest.serializer(), body)}" }
                    is PullRequest -> logger.debug { "Request body for pull: ${json.encodeToString(PullRequest.serializer(), body)}" }
                    is PushRequest -> logger.debug { "Request body for push: ${json.encodeToString(PushRequest.serializer(), body)}" }
                    is CopyRequest -> logger.debug { "Request body for copy: ${json.encodeToString(CopyRequest.serializer(), body)}" }
                    is DeleteRequest -> logger.debug { "Request body for delete: ${json.encodeToString(DeleteRequest.serializer(), body)}" }
                    else -> logger.debug { "Request body type: ${body.javaClass.simpleName}" }
                }
            }
            
            val result: R = when (method) {
                HttpMethod.Get -> {
                    val response = client.get(url) { block?.let { it() } }
                    logger.debug { "Received HTTP ${response.status.value} response from $url" }
                    response.body()
                }
                HttpMethod.Post -> {
                    if (body != null) {
                        val response = client.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(body)
                            block?.let { it() }
                        }
                        logger.debug { "Received HTTP ${response.status.value} response from $url" }
                        response.body()
                    } else {
                        val response = client.post(url) { block?.let { it() } }
                        logger.debug { "Received HTTP ${response.status.value} response from $url" }
                        response.body()
                    }
                }
                HttpMethod.Delete -> {
                    val response = client.delete(url) { block?.let { it() } }
                    logger.debug { "Received HTTP ${response.status.value} response from $url" }
                    response.body()
                }
                else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
            }
            return result
        } catch (e: ClientRequestException) {
            val errorBody = e.response.bodyAsText()
            logger.error { "HTTP ${e.response.status.value} error calling $url: $errorBody" }
            throw OllamaResponseException(
                error = errorBody, 
                statusCode = e.response.status.value, 
                cause = e
            )
        } catch (e: Exception) {
            logger.error(e) { "Error calling $url: ${e.message}" }
            throw OllamaException("Error calling $url: ${e.message}", e)
        }
    }

    /**
     * Handles the HTTP response and parses it to the expected type.
     *
     * @param response The HTTP response
     * @return The parsed response object
     */
    private suspend inline fun <reified T> handleResponse(response: T): T {
        return response
    }
    
    /**
     * Creates a flow from a streaming response.
     *
     * @param endpoint The API endpoint
     * @param body The request body
     * @return A flow of the parsed response objects
     */
    private inline fun <reified T> createStreamingFlow(
        endpoint: String,
        body: Any
    ): Flow<T> {
        return cancellableFlow {
            coroutineScope {
                val job = launch {
                    try {
                        logger.debug { "Starting streaming request to $endpoint" }
                        val response = client.post(createUrl(endpoint)) {
                            contentType(ContentType.Application.Json)
                            setBody(body)
                        }

                        if (!response.status.isSuccess()) {
                            try {
                                val errorResponse = response.body<ErrorResponse>()
                                handleErrorResponse(response.status.value, errorResponse)
                            } catch (e: Exception) {
                                throw OllamaResponseException(
                                    error = response.bodyAsText().ifEmpty { "Unknown error" },
                                    statusCode = response.status.value,
                                    cause = e
                                )
                            }
                        }

                        response.bodyAsChannel().apply {
                            while (!isClosedForRead) {
                                val line = readUTF8Line(READ_LIMIT) ?: continue
                                if (line.isBlank()) continue
                                
                                try {
                                    val chunk = json.decodeFromString<T>(line)
                                    emit(chunk)
                                    
                                    // Check if this is the final message (done=true or status=success)
                                    val isDone = when (chunk) {
                                        is GenerateResponse -> chunk.done
                                        is ChatResponse -> chunk.done
                                        is ProgressResponse -> chunk.status == "success"
                                        else -> false
                                    }
                                    
                                    if (isDone) {
                                        break
                                    }
                                } catch (e: Exception) {
                                    logger.warn { "Error parsing streaming response: $line" }
                                    // Skip invalid JSON and continue
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            logger.debug { "Stream was cancelled" }
                            throw e
                        } else {
                            logger.error(e) { "Error in streaming request: ${e.message}" }
                            throw OllamaNetworkException("Error in streaming request", e)
                        }
                    }
                }
                
                synchronized(activeStreams) {
                    activeStreams.add(job)
                }
                
                try {
                    job.join()
                } finally {
                    synchronized(activeStreams) {
                        activeStreams.remove(job)
                    }
                }
            }
        }
    }

    override suspend fun generate(request: GenerateRequest): GenerateResponse {
        val modifiedRequest = request.copy(stream = false)
        val response = makeRequest<GenerateRequest, GenerateResponse>(
            HttpMethod.Post,
            "api/generate",
            modifiedRequest
        )
        return handleResponse(response)
    }

    override fun generateStream(request: GenerateRequest): Flow<GenerateResponse> {
        val modifiedRequest = request.copy(stream = true)
        return flow {
            try {
                logger.debug { "Starting generate streaming request to api/generate" }
                logger.debug { "Request body: ${json.encodeToString(GenerateRequest.serializer(), modifiedRequest)}" }
                
                val response = client.post(createUrl("api/generate")) {
                    contentType(ContentType.Application.Json)
                    setBody(modifiedRequest)
                }
                
                if (!response.status.isSuccess()) {
                    try {
                        val errorResponse = response.body<ErrorResponse>()
                        handleErrorResponse(response.status.value, errorResponse)
                    } catch (e: Exception) {
                        throw OllamaResponseException(
                            error = response.bodyAsText().ifEmpty { "Unknown error" },
                            statusCode = response.status.value,
                            cause = e
                        )
                    }
                }
                
                response.bodyAsChannel().apply {
                    while (!isClosedForRead) {
                        val line = readUTF8Line(READ_LIMIT) ?: continue
                        if (line.isBlank()) continue
                        
                        try {
                            // logger.debug { "Received streaming line: $line" }
                            val generateResponse = json.decodeFromString<GenerateResponse>(line)
                            emit(generateResponse)
                            
                            if (generateResponse.done) {
                                break
                            }
                        } catch (e: Exception) {
                            logger.warn { "Error parsing streaming response: $line. Error: ${e.message}" }
                            logger.debug { "Attempting to parse with manual JSON extraction..." }
                            
                            // Try alternative parsing approaches
                            try {
                                val generateResponse = json.decodeFromJsonElement(
                                    GenerateResponse.serializer(),
                                    json.parseToJsonElement(line)
                                )
                                emit(generateResponse)
                                
                                if (generateResponse.done) {
                                    break
                                }
                            } catch (e2: Exception) {
                                logger.error { "Failed alternative parsing: ${e2.message}" }
                                // Continue to next line if we couldn't parse this one
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in streaming request: ${e.message}" }
                throw OllamaNetworkException("Error in streaming request", e)
            }
        }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse {
        try {
            // Check if messages is null or empty and handle accordingly
            val modifiedRequest = if (request.messages.isNullOrEmpty()) {
                request.copy(messages = listOf(Message("system", "You are a helpful assistant.")))
            } else {
                request
            }
            
            // Set stream to false to ensure we get a complete response
            val requestWithoutStream = modifiedRequest.copy(stream = false)
            
            val response = makeRequest<ChatRequest, ChatResponse>(
                HttpMethod.Post,
                "api/chat",
                requestWithoutStream
            )
            return handleResponse(response)
        } catch (e: Exception) {
            logger.error(e) { "Error in chat request: ${e.message}" }
            throw OllamaException("Error in chat request", e)
        }
    }

    override fun chatStream(request: ChatRequest): Flow<ChatResponse> {
        val modifiedRequest = request.copy(stream = true)
        return flow {
            try {
                logger.debug { "Starting chat streaming request to api/chat" }
                logger.debug { "Request body: ${json.encodeToString(ChatRequest.serializer(), modifiedRequest)}" }
                
                val response = client.post(createUrl("api/chat")) {
                    contentType(ContentType.Application.Json)
                    setBody(modifiedRequest)
                }
                
                if (!response.status.isSuccess()) {
                    try {
                        val errorResponse = response.body<ErrorResponse>()
                        handleErrorResponse(response.status.value, errorResponse)
                    } catch (e: Exception) {
                        throw OllamaResponseException(
                            error = response.bodyAsText().ifEmpty { "Unknown error" },
                            statusCode = response.status.value,
                            cause = e
                        )
                    }
                }
                
                response.bodyAsChannel().apply {
                    while (!isClosedForRead) {
                        val line = readUTF8Line(READ_LIMIT) ?: continue
                        if (line.isBlank()) continue
                        
                        try {
                            // logger.debug { "Received streaming line: $line" }
                            val chatResponse = json.decodeFromString<ChatResponse>(line)
                            emit(chatResponse)
                            
                            if (chatResponse.done) {
                                break
                            }
                        } catch (e: Exception) {
                            logger.warn { "Error parsing streaming response: $line. Error: ${e.message}" }
                            logger.debug { "Attempting to parse with manual JSON extraction..." }
                            
                            // Try alternative parsing approaches
                            try {
                                val chatResponse = json.decodeFromJsonElement(
                                    ChatResponse.serializer(),
                                    json.parseToJsonElement(line)
                                )
                                emit(chatResponse)
                                
                                if (chatResponse.done) {
                                    break
                                }
                            } catch (e2: Exception) {
                                logger.error { "Failed alternative parsing: ${e2.message}" }
                                // Continue to next line if we couldn't parse this one
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in streaming request: ${e.message}" }
                throw OllamaNetworkException("Error in streaming request", e)
            }
        }
    }

    override suspend fun embed(request: EmbedRequest): EmbedResponse {
        try {
            // Ensure we have a prompt or input for the embedding request
            val embeddingRequest = EmbeddingRequest(
                model = request.model,
                input = request.input,
                prompt = request.input, // Use input as prompt as well to ensure compatibility 
                options = request.options,
                keepAlive = request.keepAlive,
                truncate = request.truncate
            )
            
            return embedding(embeddingRequest)
        } catch (e: Exception) {
            logger.error(e) { "Error in embed request: ${e.message}" }
            throw OllamaException("Error in embed request", e)
        }
    }

    override suspend fun embedding(request: EmbeddingRequest): EmbedResponse {
        try {
            logger.debug { "Request body for embedding: ${json.encodeToString(EmbeddingRequest.serializer(), request)}" }
            
            val response = client.post(createUrl("api/embeddings")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                logger.error { "Embedding API error: $errorText" }
                throw OllamaResponseException(
                    error = errorText.ifEmpty { "Unknown error" },
                    statusCode = response.status.value
                )
            }
            
            val responseText = response.bodyAsText()
            logger.debug { "Embedding API response: $responseText" }
            
            // Parse the response text directly to handle different formats
            try {
                val jsonElement = json.parseToJsonElement(responseText)
                
                if (jsonElement is JsonObject) {
                    // Handle different embedding response formats
                    val model = (jsonElement["model"] as? JsonPrimitive)?.content ?: request.model
                    
                    // Check for "embedding" field (single embedding format)
                    val singleEmbedding = (jsonElement["embedding"] as? JsonArray)?.let { array ->
                        array.map { 
                            (it as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f 
                        }
                    }
                    
                    // Check for "embeddings" field (multi embedding format)
                    val multiEmbeddings = (jsonElement["embeddings"] as? JsonArray)?.let { outerArray ->
                        outerArray.map { innerArray ->
                            (innerArray as? JsonArray)?.map { 
                                (it as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f 
                            } ?: emptyList()
                        }
                    }
                    
                    return EmbedResponse(
                        model = model,
                        embedding = singleEmbedding,
                        embeddings = multiEmbeddings
                    )
                }
                
                // If direct parsing failed, try standard deserialization
                return response.body<EmbedResponse>()
            } catch (e: Exception) {
                logger.warn { "Error parsing embedding response directly, falling back to standard deserialization: ${e.message}" }
                
                // Try to deserialize as EmbedResponse directly
                try {
                    return response.body<EmbedResponse>()
                } catch (e2: Exception) {
                    logger.error { "Failed to parse embedding response: ${e2.message}" }
                    throw OllamaException("Failed to parse embedding response", e2)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in embedding request: ${e.message}" }
            throw OllamaException("Error in embedding request", e)
        }
    }

    /**
     * Helper class for single embedding response format
     */
    @Serializable
    private data class SingleEmbeddingResponse(
        val embedding: List<Float>? = null
    )

    override suspend fun embeddings(request: EmbeddingsRequest): EmbeddingsResponse {
        val response = makeRequest<EmbeddingsRequest, EmbeddingsResponse>(
            HttpMethod.Post,
            "api/embeddings",
            request
        )
        return handleResponse(response)
    }

    override suspend fun listModels(): ListResponse {
        try {
            val response = makeRequest<Unit, ListResponse>(
                HttpMethod.Get,
                "api/tags",
                null
            )
            return response
        } catch (e: Exception) {
            logger.error(e) { "Error listing models: ${e.message}" }
            throw OllamaException("Error listing models", e)
        }
    }

    override suspend fun showModel(request: ShowRequest): ShowResponse {
        try {
            if (request.model.isBlank()) {
                throw IllegalArgumentException("Model name cannot be blank")
            }
            
            val response = makeRequest<ShowRequest, ShowResponse>(
                HttpMethod.Post,
                "api/show",
                request
            )
            return handleResponse(response)
        } catch (e: Exception) {
            logger.error(e) { "Error showing model: ${e.message}" }
            throw OllamaException("Error showing model", e)
        }
    }

    override suspend fun pullModel(request: PullRequest): ProgressResponse {
        val modifiedRequest = request.copy(stream = false)
        val response = makeRequest<PullRequest, ProgressResponse>(
            HttpMethod.Post,
            "api/pull",
            modifiedRequest
        )
        return handleResponse(response)
    }

    override fun pullModelStream(request: PullRequest): Flow<ProgressResponse> {
        val modifiedRequest = request.copy(stream = true)
        return flow {
            try {
                logger.debug { "Starting pull model streaming request to api/pull" }
                logger.debug { "Request body: ${json.encodeToString(PullRequest.serializer(), modifiedRequest)}" }
                
                val response = client.post(createUrl("api/pull")) {
                    contentType(ContentType.Application.Json)
                    setBody(modifiedRequest)
                }
                
                if (!response.status.isSuccess()) {
                    try {
                        val errorResponse = response.body<ErrorResponse>()
                        handleErrorResponse(response.status.value, errorResponse)
                    } catch (e: Exception) {
                        throw OllamaResponseException(
                            error = response.bodyAsText().ifEmpty { "Unknown error" },
                            statusCode = response.status.value,
                            cause = e
                        )
                    }
                }
                
                response.bodyAsChannel().apply {
                    while (!isClosedForRead) {
                        val line = readUTF8Line(READ_LIMIT) ?: continue
                        if (line.isBlank()) continue
                        
                        try {
                            // logger.debug { "Received streaming line: $line" }
                            val progressResponse = json.decodeFromString<ProgressResponse>(line)
                            emit(progressResponse)
                            
                            if (progressResponse.status == "success") {
                                break
                            }
                        } catch (e: Exception) {
                            logger.warn { "Error parsing streaming response: $line. Error: ${e.message}" }
                            logger.debug { "Attempting to parse with manual JSON extraction..." }
                            
                            // Try alternative parsing approaches
                            try {
                                val progressResponse = json.decodeFromJsonElement(
                                    ProgressResponse.serializer(),
                                    json.parseToJsonElement(line)
                                )
                                emit(progressResponse)
                                
                                if (progressResponse.status == "success") {
                                    break
                                }
                            } catch (e2: Exception) {
                                logger.error { "Failed alternative parsing: ${e2.message}" }
                                // Continue to next line if we couldn't parse this one
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in streaming request: ${e.message}" }
                throw OllamaNetworkException("Error in streaming request", e)
            }
        }
    }

    override suspend fun pushModel(request: PushRequest): ProgressResponse {
        val modifiedRequest = request.copy(stream = false)
        val response = makeRequest<PushRequest, ProgressResponse>(
            HttpMethod.Post,
            "api/push",
            modifiedRequest
        )
        return handleResponse(response)
    }

    override fun pushModelStream(request: PushRequest): Flow<ProgressResponse> {
        val modifiedRequest = request.copy(stream = true)
        return flow {
            try {
                logger.debug { "Starting push model streaming request to api/push" }
                logger.debug { "Request body: ${json.encodeToString(PushRequest.serializer(), modifiedRequest)}" }
                
                val response = client.post(createUrl("api/push")) {
                    contentType(ContentType.Application.Json)
                    setBody(modifiedRequest)
                }
                
                if (!response.status.isSuccess()) {
                    try {
                        val errorResponse = response.body<ErrorResponse>()
                        handleErrorResponse(response.status.value, errorResponse)
                    } catch (e: Exception) {
                        throw OllamaResponseException(
                            error = response.bodyAsText().ifEmpty { "Unknown error" },
                            statusCode = response.status.value,
                            cause = e
                        )
                    }
                }
                
                response.bodyAsChannel().apply {
                    while (!isClosedForRead) {
                        val line = readUTF8Line(READ_LIMIT) ?: continue
                        if (line.isBlank()) continue
                        
                        try {
                            // logger.debug { "Received streaming line: $line" }
                            val progressResponse = json.decodeFromString<ProgressResponse>(line)
                            emit(progressResponse)
                            
                            if (progressResponse.status == "success") {
                                break
                            }
                        } catch (e: Exception) {
                            logger.warn { "Error parsing streaming response: $line. Error: ${e.message}" }
                            logger.debug { "Attempting to parse with manual JSON extraction..." }
                            
                            // Try alternative parsing approaches
                            try {
                                val progressResponse = json.decodeFromJsonElement(
                                    ProgressResponse.serializer(),
                                    json.parseToJsonElement(line)
                                )
                                emit(progressResponse)
                                
                                if (progressResponse.status == "success") {
                                    break
                                }
                            } catch (e2: Exception) {
                                logger.error { "Failed alternative parsing: ${e2.message}" }
                                // Continue to next line if we couldn't parse this one
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in streaming request: ${e.message}" }
                throw OllamaNetworkException("Error in streaming request", e)
            }
        }
    }

    override suspend fun createModel(request: CreateRequest): ProgressResponse {
        val modifiedRequest = request.copy(stream = false)
        val response = makeRequest<CreateRequest, ProgressResponse>(
            HttpMethod.Post,
            "api/create",
            modifiedRequest
        )
        return handleResponse(response)
    }

    override fun createModelStream(request: CreateRequest): Flow<ProgressResponse> {
        val modifiedRequest = request.copy(stream = true)
        return flow {
            try {
                logger.debug { "Starting create model streaming request to api/create" }
                logger.debug { "Request body: ${json.encodeToString(CreateRequest.serializer(), modifiedRequest)}" }
                
                val response = client.post(createUrl("api/create")) {
                    contentType(ContentType.Application.Json)
                    setBody(modifiedRequest)
                }
                
                if (!response.status.isSuccess()) {
                    try {
                        val errorResponse = response.body<ErrorResponse>()
                        handleErrorResponse(response.status.value, errorResponse)
                    } catch (e: Exception) {
                        throw OllamaResponseException(
                            error = response.bodyAsText().ifEmpty { "Unknown error" },
                            statusCode = response.status.value,
                            cause = e
                        )
                    }
                }
                
                response.bodyAsChannel().apply {
                    while (!isClosedForRead) {
                        val line = readUTF8Line(READ_LIMIT) ?: continue
                        if (line.isBlank()) continue
                        
                        try {
                            // logger.debug { "Received streaming line: $line" }
                            val progressResponse = json.decodeFromString<ProgressResponse>(line)
                            emit(progressResponse)
                            
                            if (progressResponse.status == "success") {
                                break
                            }
                        } catch (e: Exception) {
                            logger.warn { "Error parsing streaming response: $line. Error: ${e.message}" }
                            logger.debug { "Attempting to parse with manual JSON extraction..." }
                            
                            // Try alternative parsing approaches
                            try {
                                val progressResponse = json.decodeFromJsonElement(
                                    ProgressResponse.serializer(),
                                    json.parseToJsonElement(line)
                                )
                                emit(progressResponse)
                                
                                if (progressResponse.status == "success") {
                                    break
                                }
                            } catch (e2: Exception) {
                                logger.error { "Failed alternative parsing: ${e2.message}" }
                                // Continue to next line if we couldn't parse this one
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in streaming request: ${e.message}" }
                throw OllamaNetworkException("Error in streaming request", e)
            }
        }
    }

    override suspend fun deleteModel(request: DeleteRequest): StatusResponse {
        val response = makeRequest<DeleteRequest, StatusResponse>(
            HttpMethod.Post,
            "api/delete",
            request
        )
        return handleResponse(response)
    }

    override suspend fun copyModel(request: CopyRequest): StatusResponse {
        val response = makeRequest<CopyRequest, StatusResponse>(
            HttpMethod.Post,
            "api/copy",
            request
        )
        return handleResponse(response)
    }

    override suspend fun ps(): ListResponse {
        val response = makeRequest<Unit, ListResponse>(
            HttpMethod.Get,
            "api/ps",
            null
        )
        return response
    }

    override fun abort() {
        synchronized(activeStreams) {
            activeStreams.forEach { it.cancel() }
            activeStreams.clear()
        }
    }

    override suspend fun encodeImage(image: Any): String {
        return utils.encodeImage(image)
    }
    
    /**
     * Clean up resources when the client is no longer needed.
     */
    fun close() {
        abort()
        client.close()
    }
    
    companion object {
        const val READ_LIMIT = 128_000
        /**
         * Creates a new Ollama client with default configuration.
         */
        @JvmStatic
        fun create(): OllamaClient = OllamaClient()
        
        /**
         * Creates a new Ollama client with the specified host.
         *
         * @param host The Ollama server host URL
         */
        @JvmStatic
        fun create(host: String): OllamaClient = OllamaClient(
            OllamaConfig(host = formatHost(host))
        )
    }

    /**
     * Handles an error response from the API.
     *
     * @param statusCode The HTTP status code
     * @param errorResponse The error response object
     * @throws OllamaResponseException with the appropriate error message
     */
    private fun handleErrorResponse(statusCode: Int, errorResponse: ErrorResponse) {
        val errorMessage = errorResponse.error.ifEmpty { "Unknown API error" }
        logger.error { "API error (HTTP $statusCode): $errorMessage" }
        
        throw OllamaResponseException(
            error = errorMessage,
            statusCode = statusCode
        )
    }
} 