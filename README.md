# Ollama Kotlin SDK

A Kotlin client library for the [Ollama](https://ollama.ai) API. This SDK enables Kotlin and Java applications to easily interact with the Ollama API for running large language models locally.

## Features

- Complete implementation of all Ollama API endpoints
- Support for streaming responses using Kotlin Flows
- Supports both synchronous and asynchronous calls
- Image encoding for multimodal models
- Comprehensive error handling
- Built with Kotlin Coroutines for efficient asynchronous operations
- Clean and idiomatic Kotlin API design
- Fully documented with KDoc

## Installation

### Gradle

```kotlin
dependencies {
    implementation("org.fuusio.kollama:kollama:0.1.1")
}
```

### Maven

```xml
<dependency>
    <groupId>io.ollama.kotlin</groupId>
    <artifactId>ollama-kotlin-sdk</artifactId>
    <version>0.1.1</version>
</dependency>
```

## Quick Start

```kotlin
import org.fuusio.kollama.Ollama
import org.fuusio.kollama.model.ChatRequest
import org.fuusio.kollama.model.Message
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // List available models
    val models = Ollama.listModels()
    models.models.forEach { println(it.name) }
    
    // Chat completion (non-streaming)
    val chatResponse = Ollama.chat(
        ChatRequest(
            model = "llama3",
            messages = listOf(
                Message(
                    role = "user",
                    content = "What is the capital of France?"
                )
            )
        )
    )
    println("Response: ${chatResponse.message.content}")
    
    // Chat completion (streaming)
    Ollama.chatStream(
        ChatRequest(
            model = "llama3",
            messages = listOf(
                Message(
                    role = "user",
                    content = "Tell me about Kotlin programming language."
                )
            ),
            stream = true
        )
    ).onEach { response ->
        print(response.message.content)
    }.collect()
}
```

## API Overview

### Creating a Client

The SDK provides a default client instance through the `Ollama` object. You can also create custom clients:

```kotlin
// Default client (connects to http://127.0.0.1:11434)
val defaultClient = Ollama

// Custom host
val customClient = Ollama.createClient("https://your-ollama-server:11434")

// Custom configuration
val configuredClient = Ollama.createClient(
    OllamaConfig(
        host = "https://your-ollama-server:11434",
        headers = mapOf("Authorization" to "Bearer token"),
        timeoutMs = 120000L
    )
)
```

### Chat Completions

```kotlin
// Non-streaming
val chatResponse = Ollama.chat(
    ChatRequest(
        model = "llama3",
        messages = listOf(
            Message(role = "system", content = "You are a helpful assistant."),
            Message(role = "user", content = "Hello, who are you?")
        )
    )
)

// Streaming
Ollama.chatStream(
    ChatRequest(
        model = "llama3",
        messages = listOf(
            Message(role = "system", content = "You are a helpful assistant."),
            Message(role = "user", content = "Hello, who are you?")
        ),
        stream = true
    )
).collect { response ->
    print(response.message.content)
}
```

### Text Generation

```kotlin
// Non-streaming
val generateResponse = Ollama.generate(
    GenerateRequest(
        model = "llama3",
        prompt = "Once upon a time"
    )
)

// Streaming
Ollama.generateStream(
    GenerateRequest(
        model = "llama3",
        prompt = "Once upon a time",
        stream = true
    )
).collect { response ->
    print(response.response)
}
```

### Embeddings

```kotlin
val embedResponse = Ollama.embed(
    EmbedRequest(
        model = "llama3",
        input = "Hello world"
    )
)

// Access the embeddings
val embeddings = embedResponse.embeddings
```

### Working with Models

```kotlin
// List models
val models = Ollama.listModels()

// Show model details
val modelDetails = Ollama.showModel(
    ShowRequest(
        model = "llama3"
    )
)

// Pull a model
val pullResponse = Ollama.pullModel(
    PullRequest(
        model = "llama3"
    )
)

// Pull a model with streaming progress
Ollama.pullModelStream(
    PullRequest(
        model = "llama3",
        stream = true
    )
).collect { progress ->
    println("Progress: ${progress.completed}/${progress.total}")
}

// Delete a model
val deleteResponse = Ollama.deleteModel(
    DeleteRequest(
        model = "llama3"
    )
)
```

### Multimodal Support

```kotlin
// Chat with images
val chatWithImageResponse = Ollama.chat(
    ChatRequest(
        model = "llava",
        messages = listOf(
            Message(
                role = "user",
                content = "What's in this image?",
                images = listOf(
                    Ollama.encodeImage("path/to/image.jpg")
                )
            )
        )
    )
)
```

## Error Handling

The SDK uses a hierarchy of exceptions for different error types:

```kotlin
try {
    val response = Ollama.chat(...)
} catch (e: OllamaResponseException) {
    // API response error
    println("API error: ${e.error}, status code: ${e.statusCode}")
} catch (e: OllamaNetworkException) {
    // Network error
    println("Network error: ${e.message}")
} catch (e: OllamaException) {
    // Any other Ollama-related error
    println("Error: ${e.message}")
}
```

## Configuration Options

The client can be configured with these options:

```kotlin
val client = Ollama.createClient(
    OllamaConfig(
        host = "https://your-ollama-server:11434",
        headers = mapOf(
            "Authorization" to "Bearer token",
            "Custom-Header" to "Value"
        ),
        proxy = false,
        timeoutMs = 60000L
    )
)
```

## Resource Management

It's good practice to close the client when you're done with it to release resources:

```kotlin
val client = Ollama.createClient(...)
try {
    // Use the client
} finally {
    client.close()
}
```

## License

This project is available under the MIT License.

## Related

- [Ollama](https://ollama.ai) - Run large language models locally
- [Ollama API Documentation](https://github.com/ollama/ollama/blob/main/docs/api.md) - Official API documentation 