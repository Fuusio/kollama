package org.fuusio.kollama.examples

import org.fuusio.kollama.Kollama
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.fuusio.kollama.model.*

/**
 * Basic examples demonstrating usage of the Ollama Kotlin SDK.
 */
object BasicExample {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        try {
            // Example 1: List available models
            println("=== Available Models ===")
            val models = Kollama.listModels()
            models.models.forEach { model ->
                println("${model.name} - ${model.size} bytes")
            }
            println()

            // Get the first available model name, or default to "llama2" if no models are available
            val modelName = if (models.models.isNotEmpty()) models.models.last().name else "llama3"
            println("Using model: $modelName")
            
            // Example 2: Chat completion
            println("=== Chat Completion ===")
            try {
                val chatResponse = Kollama.chat(
                    ChatRequest(
                        model = modelName,
                        messages = listOf(
                            Message(
                                role = "user",
                                content = "What is the capital of France?"
                            )
                        )
                    )
                )
                println("Response: ${chatResponse.message.content}")
                println("Model: ${chatResponse.model}")
                println("Total duration: ${chatResponse.totalDuration}ms")
            } catch (e: Exception) {
                println("Error with chat completion: ${e.message}")
            }
            println()

            // Example 3: Streaming chat completion
            println("=== Streaming Chat Completion ===")
            println("Response: ")
            try {
                Kollama.chatStream(
                    ChatRequest(
                        model = modelName,
                        messages = listOf(
                            Message(
                                role = "user",
                                content = "Tell me about Kotlin programming language in one sentence."
                            )
                        ),
                        stream = true
                    )
                ).onEach { response ->
                    print(response.message.content)
                }.collect()
            } catch (e: Exception) {
                println("Error with streaming chat: ${e.message}")
            }
            println("\n")

            // Example 4: Generate text
            println("=== Generate Text ===")
            try {
                val generateResponse = Kollama.generate(
                    GenerateRequest(
                        model = modelName,
                        prompt = "Describe a sunset in one sentence."
                    )
                )
                println("Response: ${generateResponse.response}")
            } catch (e: Exception) {
                println("Error with generate: ${e.message}")
            }
            println()

            // Example 5: Streaming generate text
            println("=== Streaming Generate Text ===")
            println("Response: ")
            try {
                Kollama.generateStream(
                    GenerateRequest(
                        model = modelName,
                        prompt = "Count from 1 to 5.",
                        stream = true
                    )
                ).onEach { response ->
                    print(response.response)
                }.collect()
            } catch (e: Exception) {
                println("Error with streaming generate: ${e.message}")
            }
            println("\n")

            // Example 6: Show model details
            try {
                println("=== Model Details ===")
                val modelDetails = Kollama.showModel(
                    _root_ide_package_.org.fuusio.kollama.model.ShowRequest(
                        model = modelName
                    )
                )
                println("Model family: ${modelDetails.details?.family ?: "Unknown"}")
                println("Parameter size: ${modelDetails.details?.parameterSize ?: "Unknown"}")
                println("Template: ${modelDetails.template ?: "Default"}")
            } catch (e: Exception) {
                println("Error getting model details: ${e.message}")
            }

            // Example 7: Create embeddings
            try {
                println("\n=== Create Embeddings ===")
                val embeddingResponse = Kollama.embedding(
                    _root_ide_package_.org.fuusio.kollama.model.EmbeddingRequest(
                        model = modelName,
                        prompt = "Hello world!"
                    )
                )
                println("Embedding created for model: ${embeddingResponse.model}")
                println("Dimensions: ${embeddingResponse.embeddings?.firstOrNull()?.size ?: 0}")
                println("First few values: ${embeddingResponse.embeddings?.firstOrNull()?.take(5)}")
            } catch (e: Exception) {
                println("Error creating embeddings: ${e.message}")
            }
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
            e.printStackTrace()
        } finally {
            // Clean up
            Kollama.close()
        }
    }
} 