package io.ollama.kotlin.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import java.util.Date

/**
 * Configuration for the Ollama SDK client.
 */
data class OllamaConfig(
    /**
     * Host URL for Ollama server.
     */
    val host: String,
    
    /**
     * Custom headers to include in requests.
     */
    val headers: Map<String, String> = emptyMap(),
    
    /**
     * Whether to use a proxy.
     */
    val proxy: Boolean = false,
    
    /**
     * Timeout for network requests in milliseconds.
     */
    val timeoutMs: Long = 300000L  // 5 minutes timeout for large models
)

/**
 * Model generation options.
 */
@Serializable
data class Options(
    val numa: Boolean? = null,
    val numCtx: Int? = null,
    @SerialName("num_ctx") val _numCtx: Int? = null,
    val numBatch: Int? = null,
    @SerialName("num_batch") val _numBatch: Int? = null,
    val numGpu: Int? = null,
    @SerialName("num_gpu") val _numGpu: Int? = null,
    val mainGpu: Int? = null,
    @SerialName("main_gpu") val _mainGpu: Int? = null,
    val lowVram: Boolean? = null,
    @SerialName("low_vram") val _lowVram: Boolean? = null,
    val f16Kv: Boolean? = null,
    @SerialName("f16_kv") val _f16Kv: Boolean? = null,
    val logitsAll: Boolean? = null,
    @SerialName("logits_all") val _logitsAll: Boolean? = null,
    val vocabOnly: Boolean? = null,
    @SerialName("vocab_only") val _vocabOnly: Boolean? = null,
    val useMmap: Boolean? = null,
    @SerialName("use_mmap") val _useMmap: Boolean? = null,
    val useMlock: Boolean? = null,
    @SerialName("use_mlock") val _useMlock: Boolean? = null,
    val embeddingOnly: Boolean? = null,
    @SerialName("embedding_only") val _embeddingOnly: Boolean? = null,
    val numThread: Int? = null,
    @SerialName("num_thread") val _numThread: Int? = null,
    
    // Runtime options
    val numKeep: Int? = null,
    @SerialName("num_keep") val _numKeep: Int? = null,
    val seed: Int? = null,
    val numPredict: Int? = null,
    @SerialName("num_predict") val _numPredict: Int? = null,
    val topK: Int? = null,
    @SerialName("top_k") val _topK: Int? = null,
    val topP: Float? = null,
    @SerialName("top_p") val _topP: Float? = null,
    val tfsZ: Float? = null,
    @SerialName("tfs_z") val _tfsZ: Float? = null,
    val typicalP: Float? = null,
    @SerialName("typical_p") val _typicalP: Float? = null,
    val repeatLastN: Int? = null,
    @SerialName("repeat_last_n") val _repeatLastN: Int? = null,
    val temperature: Float? = null,
    val repeatPenalty: Float? = null,
    @SerialName("repeat_penalty") val _repeatPenalty: Float? = null,
    val presencePenalty: Float? = null,
    @SerialName("presence_penalty") val _presencePenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    @SerialName("frequency_penalty") val _frequencyPenalty: Float? = null,
    val mirostat: Int? = null,
    val mirostatTau: Float? = null,
    @SerialName("mirostat_tau") val _mirostatTau: Float? = null,
    val mirostatEta: Float? = null,
    @SerialName("mirostat_eta") val _mirostatEta: Float? = null,
    val penalizeNewline: Boolean? = null,
    @SerialName("penalize_newline") val _penalizeNewline: Boolean? = null,
    val stop: List<String>? = null
)

/**
 * Represents a chat message.
 */
@Serializable
data class Message(
    @Required
    val role: String,
    @Required
    val content: String,
    val images: List<String>? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
)

/**
 * Represents a tool call in a message.
 */
@Serializable
data class ToolCall(
    val function: ToolCallFunction
)

/**
 * Function details for a tool call.
 */
@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: Map<String, @Contextual Any>
)

/**
 * Represents a tool definition.
 */
@Serializable
data class Tool(
    val type: String,
    val function: ToolFunction
)

/**
 * Function details for a tool definition.
 */
@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

/**
 * Parameters for a tool function.
 */
@Serializable
data class ToolParameters(
    val type: String,
    val required: List<String>,
    val properties: Map<String, ToolProperty>
)

/**
 * Property definition for tool parameters.
 */
@Serializable
data class ToolProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

// Request classes

/**
 * Request for generating text.
 */
@Serializable
data class GenerateRequest(
    val model: String,
    val prompt: String,
    val suffix: String? = null,
    val system: String? = null,
    val template: String? = null,
    val context: List<Int>? = null,
    val stream: Boolean? = null,
    val raw: Boolean? = null,
    val format: String? = null,
    val images: List<String>? = null,
    @SerialName("keep_alive") val keepAlive: String? = null,
    val options: Options? = null
)

/**
 * Request for chat completion.
 */
@Serializable
data class ChatRequest(
    @Required
    val model: String,
    @Required
    val messages: List<Message>? = null,
    val stream: Boolean? = null,
    val format: String? = null,
    @SerialName("keep_alive") val keepAlive: String? = null,
    val tools: List<Tool>? = null,
    val options: Options? = null
) {
    init {
        require(model.isNotBlank()) { "Model name cannot be blank" }
        require(messages.isNullOrEmpty() || messages.all { it.role.isNotBlank() && it.content.isNotBlank() }) { 
            "All messages must have non-blank role and content" 
        }
    }
}

/**
 * Request for pulling a model.
 */
@Serializable
data class PullRequest(
    val model: String,
    val insecure: Boolean? = null,
    val stream: Boolean? = null
)

/**
 * Request for pushing a model.
 */
@Serializable
data class PushRequest(
    val model: String,
    val insecure: Boolean? = null,
    val stream: Boolean? = null
)

/**
 * Request for creating a model.
 */
@Serializable
data class CreateRequest(
    val model: String,
    val from: String? = null,
    val stream: Boolean? = null,
    val quantize: String? = null,
    val template: String? = null,
    val license: String? = null,
    val system: String? = null,
    val parameters: Map<String, @Contextual Any>? = null,
    val messages: List<Message>? = null,
    val adapters: Map<String, String>? = null
)

/**
 * Request for deleting a model.
 */
@Serializable
data class DeleteRequest(
    val model: String
)

/**
 * Request for copying a model.
 */
@Serializable
data class CopyRequest(
    val source: String,
    val destination: String
)

/**
 * Request for showing model details.
 */
@Serializable
data class ShowRequest(
    @Required
    val model: String,
    val system: String? = null,
    val template: String? = null,
    val options: Options? = null
) {
    init {
        require(model.isNotBlank()) { "Model name cannot be blank" }
    }
}

/**
 * Request for embedding text.
 */
@Serializable
data class EmbedRequest(
    val model: String,
    val input: String,
    val truncate: Boolean? = null,
    @SerialName("keep_alive") val keepAlive: String? = null,
    val options: Options? = null
)

/**
 * Alternative request format for embeddings.
 */
@Serializable
data class EmbeddingsRequest(
    val model: String,
    val prompt: String,
    @SerialName("keep_alive") val keepAlive: String? = null,
    val options: Options? = null
)

/**
 * Unified request for embeddings that works with the API.
 */
@Serializable
data class EmbeddingRequest(
    @Required
    val model: String,
    val prompt: String? = null,
    val input: String? = null,
    val truncate: Boolean? = null,
    @SerialName("keep_alive") val keepAlive: String? = null,
    val options: Options? = null
) {
    init {
        require(model.isNotBlank()) { "Model name cannot be blank" }
        require(prompt != null || input != null) { "Either prompt or input must be provided" }
    }
}

// Response classes

/**
 * Response from text generation.
 */
@Serializable
data class GenerateResponse(
    val model: String,
    @SerialName("created_at") val createdAt: String,
    val response: String,
    val done: Boolean,
    @SerialName("done_reason") val doneReason: String? = null,
    val context: List<Int>? = null,
    @SerialName("total_duration") val totalDuration: Long = 0,
    @SerialName("load_duration") val loadDuration: Long = 0,
    @SerialName("prompt_eval_count") val promptEvalCount: Int = 0,
    @SerialName("prompt_eval_duration") val promptEvalDuration: Long = 0,
    @SerialName("eval_count") val evalCount: Int = 0,
    @SerialName("eval_duration") val evalDuration: Long = 0
)

/**
 * Response from chat completion.
 */
@Serializable
data class ChatResponse(
    val model: String,
    @SerialName("created_at") val createdAt: String,
    val message: Message,
    val done: Boolean,
    @SerialName("done_reason") val doneReason: String? = null,
    @SerialName("total_duration") val totalDuration: Long = 0,
    @SerialName("load_duration") val loadDuration: Long = 0,
    @SerialName("prompt_eval_count") val promptEvalCount: Int = 0,
    @SerialName("prompt_eval_duration") val promptEvalDuration: Long = 0,
    @SerialName("eval_count") val evalCount: Int = 0,
    @SerialName("eval_duration") val evalDuration: Long = 0
)

/**
 * Response from embedding.
 */
@Serializable
data class EmbedResponse(
    val model: String? = null,
    val embedding: List<Float>? = null,
    val embeddings: List<List<Float>>? = null
)

/**
 * Alternative response format for embeddings.
 */
@Serializable
data class EmbeddingsResponse(
    val embedding: List<Float>? = null,
    val model: String? = null
)

/**
 * Response with progress information.
 */
@Serializable
data class ProgressResponse(
    val status: String,
    val digest: String? = null,
    val total: Long? = null,
    val completed: Long? = null
)

/**
 * Details about a model.
 */
@Serializable
data class ModelDetails(
    @SerialName("parent_model") val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    @SerialName("parameter_size") val parameterSize: String? = null,
    @SerialName("quantization_level") val quantizationLevel: String? = null
)

/**
 * Response with model information.
 */
@Serializable
data class ModelResponse(
    val name: String,
    @SerialName("modified_at") val modifiedAt: String,
    val model: String? = null,
    val size: Long,
    val digest: String,
    val details: ModelDetails? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("size_vram") val sizeVram: Long? = null
)

/**
 * Response with detailed model information.
 */
@Serializable
data class ShowResponse(
    val license: String? = null,
    val modelfile: String? = null,
    val parameters: String? = null,
    val template: String? = null,
    val system: String? = null,
    val details: ModelDetails? = null,
    val messages: List<Message>? = null,
    @SerialName("modified_at") val modifiedAt: String? = null,
    @SerialName("model_info") val modelInfo: Map<String, @Contextual Any>? = null,
    @SerialName("projector_info") val projectorInfo: Map<String, @Contextual Any>? = null
)

/**
 * Response with a list of models.
 */
@Serializable
data class ListResponse(
    val models: List<ModelResponse>
)

/**
 * Error response.
 */
@Serializable
data class ErrorResponse(
    val error: String
)

/**
 * Status response.
 */
@Serializable
data class StatusResponse(
    val status: String
) 