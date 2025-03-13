package org.fuusio.kollama.api

import org.fuusio.kollama.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining all API operations for the Ollama service.
 */
interface KollamaAPI {
    /**
     * Generates completions for the provided prompt using the specified model.
     *
     * @param request The generation request parameters
     * @return A response containing the generated text and metadata
     */
    suspend fun generate(request: GenerateRequest): GenerateResponse

    /**
     * Generates completions for the provided prompt with streaming output.
     *
     * @param request The generation request parameters (with stream=true)
     * @return A flow of generation responses
     */
    fun generateStream(request: GenerateRequest): Flow<GenerateResponse>

    /**
     * Creates a chat completion for the provided messages using the specified model.
     *
     * @param request The chat request parameters
     * @return A response containing the generated chat message and metadata
     */
    suspend fun chat(request: ChatRequest): ChatResponse

    /**
     * Creates a chat completion with streaming output.
     *
     * @param request The chat request parameters (with stream=true)
     * @return A flow of chat responses
     */
    fun chatStream(request: ChatRequest): Flow<ChatResponse>

    /**
     * Creates embeddings for the provided input using the specified model.
     *
     * @param request The embed request parameters
     * @return A response containing the embeddings
     */
    suspend fun embed(request: EmbedRequest): EmbedResponse

    /**
     * Alternative method for creating embeddings.
     *
     * @param request The embeddings request parameters
     * @return A response containing the embedding
     */
    suspend fun embeddings(request: EmbeddingsRequest): EmbeddingsResponse

    /**
     * Creates embeddings using the unified request format.
     *
     * @param request The embedding request parameters
     * @return A response containing the embeddings
     */
    suspend fun embedding(request: EmbeddingRequest): EmbedResponse

    /**
     * Lists all locally available models.
     *
     * @return A response containing the list of models
     */
    suspend fun listModels(): ListResponse

    /**
     * Shows information about a specific model.
     *
     * @param request The show request parameters
     * @return A response containing the model details
     */
    suspend fun showModel(request: ShowRequest): ShowResponse

    /**
     * Pulls a model from a registry.
     *
     * @param request The pull request parameters
     * @return A response containing the final status of the pull operation
     */
    suspend fun pullModel(request: PullRequest): ProgressResponse

    /**
     * Pulls a model from a registry with streaming progress updates.
     *
     * @param request The pull request parameters (with stream=true)
     * @return A flow of progress responses
     */
    fun pullModelStream(request: PullRequest): Flow<ProgressResponse>

    /**
     * Pushes a model to a registry.
     *
     * @param request The push request parameters
     * @return A response containing the final status of the push operation
     */
    suspend fun pushModel(request: PushRequest): ProgressResponse

    /**
     * Pushes a model to a registry with streaming progress updates.
     *
     * @param request The push request parameters (with stream=true)
     * @return A flow of progress responses
     */
    fun pushModelStream(request: PushRequest): Flow<ProgressResponse>

    /**
     * Creates a model from a Modelfile.
     *
     * @param request The create request parameters
     * @return A response containing the final status of the create operation
     */
    suspend fun createModel(request: CreateRequest): ProgressResponse

    /**
     * Creates a model from a Modelfile with streaming progress updates.
     *
     * @param request The create request parameters (with stream=true)
     * @return A flow of progress responses
     */
    fun createModelStream(request: CreateRequest): Flow<ProgressResponse>

    /**
     * Deletes a model.
     *
     * @param request The delete request parameters
     * @return A response indicating the status of the delete operation
     */
    suspend fun deleteModel(request: DeleteRequest): StatusResponse

    /**
     * Copies a model.
     *
     * @param request The copy request parameters
     * @return A response indicating the status of the copy operation
     */
    suspend fun copyModel(request: CopyRequest): StatusResponse

    /**
     * Lists running model instances.
     *
     * @return A response containing the list of running model instances
     */
    suspend fun ps(): ListResponse
    
    /**
     * Aborts any ongoing streaming requests.
     */
    fun abort()
    
    /**
     * Encodes an image for use in multimodal requests.
     *
     * @param image The image as ByteArray, File, or path String
     * @return A base64-encoded string representation of the image
     */
    suspend fun encodeImage(image: Any): String
} 