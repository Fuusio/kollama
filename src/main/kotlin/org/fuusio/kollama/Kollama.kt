package org.fuusio.kollama

import org.fuusio.kollama.api.KollamaAPI
import org.fuusio.kollama.model.KollamaConfig
import org.fuusio.kollama.utils.formatHost

/**
 * Main entry point for the Ollama Kotlin SDK.
 * Provides static access to the Ollama API and utility methods for creating client instances.
 */
object Kollama : KollamaAPI by KollamaClient() {
    // Get the default client instance
    private val defaultClient = KollamaClient()
    
    /**
     * Creates a new Ollama client with custom configuration.
     *
     * @param config The client configuration
     * @return A new OllamaClient instance
     */
    @JvmStatic
    fun createClient(config: KollamaConfig): KollamaClient = KollamaClient(config)
    
    /**
     * Creates a new Ollama client with the specified host.
     *
     * @param host The Ollama server host URL
     * @return A new OllamaClient instance
     */
    @JvmStatic
    fun createClient(host: String): KollamaClient = KollamaClient(
        KollamaConfig(host = formatHost(host))
    )
    
    /**
     * Creates a new Ollama client with the specified host and headers.
     *
     * @param host The Ollama server host URL
     * @param headers Custom headers to include in requests
     * @return A new OllamaClient instance
     */
    @JvmStatic
    fun createClient(host: String, headers: Map<String, String>): KollamaClient = KollamaClient(
        KollamaConfig(
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