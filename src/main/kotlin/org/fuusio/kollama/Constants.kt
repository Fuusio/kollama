package org.fuusio.kollama

/**
 * Default constants for the Ollama SDK.
 */
object Constants {
    /**
     * Default port for Ollama server.
     */
    const val DEFAULT_PORT = 11434
    
    /**
     * Default host URL for Ollama server.
     */
    const val DEFAULT_HOST = "http://127.0.0.1:$DEFAULT_PORT"
    
    /**
     * Default timeout for network requests in milliseconds.
     */
    const val DEFAULT_TIMEOUT_MS = 60000L
    
    /**
     * SDK version.
     */
    const val VERSION = "0.1.0"
} 