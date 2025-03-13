package io.ollama.kotlin.sdk

import io.ollama.kotlin.sdk.api.OllamaAPI
import io.ollama.kotlin.sdk.model.OllamaConfig
import io.ollama.kotlin.sdk.utils.formatHost

/**
 * Main entry point for the Ollama Kotlin SDK.
 * Provides static access to the Ollama API and utility methods for creating client instances.
 */
object Ollama : OllamaAPI by OllamaClient() {
    // Get the default client instance
    private val defaultClient = OllamaClient()
    
    /**
     * Creates a new Ollama client with custom configuration.
     *
     * @param config The client configuration
     * @return A new OllamaClient instance
     */
    @JvmStatic
    fun createClient(config: OllamaConfig): OllamaClient = OllamaClient(config)
    
    /**
     * Creates a new Ollama client with the specified host.
     *
     * @param host The Ollama server host URL
     * @return A new OllamaClient instance
     */
    @JvmStatic
    fun createClient(host: String): OllamaClient = OllamaClient(
        OllamaConfig(host = formatHost(host))
    )
    
    /**
     * Creates a new Ollama client with the specified host and headers.
     *
     * @param host The Ollama server host URL
     * @param headers Custom headers to include in requests
     * @return A new OllamaClient instance
     */
    @JvmStatic
    fun createClient(host: String, headers: Map<String, String>): OllamaClient = OllamaClient(
        OllamaConfig(
            host = formatHost(host),
            headers = headers
        )
    )
    
    /**
     * Closes the default client and releases resources.
     */
    fun close() {
        defaultClient.close()
    }
} 