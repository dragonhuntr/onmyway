package com.onmyway.app.model

import com.onmyway.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

/** Client for the Worker in `api/src/index.ts`. */
object Api {
    /** Worker URL; set with `-PapiUrl=…` at build time to use a local Worker. */
    private val baseUrl = BuildConfig.API_URL.trimEnd('/')

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Sends a request and decodes the JSON response. Throws [ApiException] with the server's message. */
    suspend inline fun <reified T> send(
        method: String,
        path: String,
        body: Map<String, Any?>? = null,
        query: Map<String, String> = emptyMap(),
        token: String?,
    ): T {
        val text = sendRaw(method, path, body, query, token)
        return try {
            json.decodeFromString(serializer<T>(), text)
        } catch (e: IllegalArgumentException) {
            throw ApiException("Something went wrong. Try again.", status = 200)
        }
    }

    suspend fun sendRaw(
        method: String,
        path: String,
        body: Map<String, Any?>?,
        query: Map<String, String>,
        token: String?,
    ): String = withContext(Dispatchers.IO) {
        val queryString = if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        val status: Int
        val text: String
        try {
            val connection = URL("$baseUrl/$path$queryString").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(toJson(body).toString().toByteArray()) }
            }
            status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
        } catch (e: IOException) {
            throw ApiException("Could not reach the server. Check your connection and try again.")
        }

        if (status !in 200..299) {
            val error = runCatching { json.decodeFromString<ErrorBody>(text) }.getOrNull()
            throw ApiException(error?.error ?: "Something went wrong. Try again.", error?.code, status)
        }
        text
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toJson(item) })
        is Iterable<*> -> JsonArray(value.map(::toJson))
        else -> error("Can't send ${value::class} as JSON")
    }

    // Account

    @Serializable
    data class Account(
        val id: String,
        val username: String,
        val email: String,
        val firstName: String,
        val lastName: String,
        val token: String,
    ) {
        val sessionUser: SessionUser get() = SessionUser(id, username, email, firstName, lastName)
    }

    suspend fun register(email: String, firstName: String, lastName: String, username: String, password: String): Account =
        send(
            "POST", "register",
            body = mapOf(
                "email" to email,
                "firstName" to firstName,
                "lastName" to lastName,
                "username" to username,
                "password" to password,
            ),
            token = null,
        )

    suspend fun login(username: String, password: String): Account =
        send("POST", "login", body = mapOf("username" to username, "password" to password), token = null)

    suspend fun currentAccount(token: String): Account = send("GET", "me", token = token)
}

class ApiException(message: String, val code: String? = null, val status: Int = 0) : Exception(message)

/** For endpoints whose response body the app doesn't use. */
@Serializable
class Empty

@Serializable
private data class ErrorBody(val error: String, val code: String? = null)

/** The Worker's ISO 8601 timestamps, with or without fractional seconds. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant = OffsetDateTime.parse(decoder.decodeString()).toInstant()
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}

fun Instant.iso(): String = toString()
