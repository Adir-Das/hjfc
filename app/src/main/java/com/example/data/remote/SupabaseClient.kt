package com.example.data.remote

import com.example.core.config.AppConfig
import com.example.domain.model.AuthResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseClient {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun requireConfigured() {
        if (!AppConfig.isSupabaseConfigured) {
            throw IllegalStateException("Supabase URL & Anon Key are not configured in AI Studio Secrets.")
        }
    }

    private fun baseHeaders(accessToken: String? = null): Request.Builder {
        val builder = Request.Builder()
            .header("apikey", AppConfig.supabaseAnonKey)
            .header("Accept", "application/json")
        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        } else {
            builder.header("Authorization", "Bearer ${AppConfig.supabaseAnonKey}")
        }
        return builder
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = runCatching {
                    val obj = json.parseToJsonElement(bodyStr).jsonObject
                    obj["message"]?.jsonPrimitive?.content
                        ?: obj["error_description"]?.jsonPrimitive?.content
                        ?: obj["msg"]?.jsonPrimitive?.content
                        ?: obj["error"]?.jsonPrimitive?.content
                }.getOrNull() ?: bodyStr.ifBlank { "HTTP ${response.code}" }
                throw IOException(msg)
            }
            bodyStr
        }
    }

    // ---- GoTrue Auth ----

    suspend fun signInWithPassword(email: String, password: String): AuthResponseDto {
        requireConfigured()
        val payload = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }.toString()
        val req = baseHeaders()
            .url("${AppConfig.supabaseUrl}/auth/v1/token?grant_type=password")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        return json.decodeFromString(execute(req))
    }

    suspend fun signUp(email: String, password: String, fullName: String): AuthResponseDto {
        requireConfigured()
        val payload = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            put("data", buildJsonObject { put("full_name", fullName.trim()) })
        }.toString()
        val req = baseHeaders()
            .url("${AppConfig.supabaseUrl}/auth/v1/signup")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        return json.decodeFromString(execute(req))
    }

    suspend fun recoverPassword(email: String) {
        requireConfigured()
        val payload = buildJsonObject { put("email", email.trim()) }.toString()
        val req = baseHeaders()
            .url("${AppConfig.supabaseUrl}/auth/v1/recover")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        execute(req)
    }

    suspend fun refreshSession(refreshToken: String): AuthResponseDto {
        requireConfigured()
        val payload = buildJsonObject { put("refresh_token", refreshToken) }.toString()
        val req = baseHeaders()
            .url("${AppConfig.supabaseUrl}/auth/v1/token?grant_type=refresh_token")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        return json.decodeFromString(execute(req))
    }

    suspend fun signOut(accessToken: String) {
        if (!AppConfig.isSupabaseConfigured || accessToken.isBlank()) return
        val req = baseHeaders(accessToken)
            .url("${AppConfig.supabaseUrl}/auth/v1/logout")
            .post("{}".toRequestBody(jsonMedia))
            .build()
        runCatching { execute(req) }
    }

    // ---- PostgREST ----

    suspend fun selectRaw(table: String, query: String, accessToken: String): String {
        requireConfigured()
        val req = baseHeaders(accessToken)
            .url("${AppConfig.supabaseUrl}/rest/v1/$table?$query")
            .get()
            .build()
        return execute(req)
    }

    suspend inline fun <reified T> selectList(table: String, query: String, accessToken: String): List<T> {
        val raw = selectRaw(table, query, accessToken)
        return if (raw.isBlank()) emptyList() else json.decodeFromString(raw)
    }

    suspend fun insertRaw(
        table: String,
        bodyJson: String,
        accessToken: String,
        upsertConflict: String? = null
    ): String {
        requireConfigured()
        val prefer = if (upsertConflict != null) {
            "resolution=merge-duplicates,return=representation"
        } else {
            "return=representation"
        }
        val url = buildString {
            append("${AppConfig.supabaseUrl}/rest/v1/$table")
            if (upsertConflict != null) append("?on_conflict=$upsertConflict")
        }
        val req = baseHeaders(accessToken)
            .header("Prefer", prefer)
            .url(url)
            .post(bodyJson.toRequestBody(jsonMedia))
            .build()
        return execute(req)
    }

    suspend fun patchRaw(table: String, filterQuery: String, bodyJson: String, accessToken: String): String {
        requireConfigured()
        val req = baseHeaders(accessToken)
            .header("Prefer", "return=representation")
            .url("${AppConfig.supabaseUrl}/rest/v1/$table?$filterQuery")
            .patch(bodyJson.toRequestBody(jsonMedia))
            .build()
        return execute(req)
    }

    suspend fun deleteRaw(table: String, filterQuery: String, accessToken: String) {
        requireConfigured()
        val req = baseHeaders(accessToken)
            .url("${AppConfig.supabaseUrl}/rest/v1/$table?$filterQuery")
            .delete()
            .build()
        execute(req)
    }

    suspend fun rpcRaw(functionName: String, params: JsonObject, accessToken: String): String {
        requireConfigured()
        val req = baseHeaders(accessToken)
            .url("${AppConfig.supabaseUrl}/rest/v1/rpc/$functionName")
            .post(params.toString().toRequestBody(jsonMedia))
            .build()
        return execute(req)
    }

    // ---- Supabase Storage ----

    suspend fun uploadStorageObject(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        mimeType: String,
        accessToken: String
    ): String {
        requireConfigured()
        val uploadUrl = "${AppConfig.supabaseUrl}/storage/v1/object/$bucket/$objectPath"
        val req = baseHeaders(accessToken)
            .header("x-upsert", "true")
            .url(uploadUrl)
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        execute(req)
        return "${AppConfig.supabaseUrl}/storage/v1/object/public/$bucket/$objectPath"
    }

    // ---- Supabase Edge Functions ----

    suspend fun invokeEdgeFunction(
        functionName: String,
        payload: JsonObject,
        accessToken: String
    ): String {
        requireConfigured()
        val req = baseHeaders(accessToken)
            .url("${AppConfig.supabaseUrl}/functions/v1/$functionName")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return execute(req)
    }

    // ---- Supabase Realtime WebSocket (Phoenix Channel for live multi-device sync) ----

    fun observeTeamRealtime(teamId: String, accessToken: String): Flow<String> = callbackFlow {
        if (!AppConfig.isSupabaseConfigured || teamId.isBlank() || accessToken.isBlank()) {
            awaitClose { }
            return@callbackFlow
        }

        val wsUrl = AppConfig.supabaseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${AppConfig.supabaseAnonKey}&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        var refCounter = 1

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val joinMsg = buildJsonObject {
                    put("topic", "realtime:public")
                    put("event", "phx_join")
                    put("ref", (refCounter++).toString())
                    put("payload", buildJsonObject {
                        put("config", buildJsonObject {
                            put("postgres_changes", kotlinx.serialization.json.buildJsonArray {
                                add(buildJsonObject {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("filter", "team_id=eq.$teamId")
                                })
                            })
                        })
                        put("access_token", accessToken)
                    })
                }.toString()
                webSocket.send(joinMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("postgres_changes")) {
                    trySend(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Silent reconnect handled on next session refresh
            }
        })

        val heartbeatJob = launch(Dispatchers.IO) {
            while (isActive) {
                delay(25_000)
                val hb = """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"${refCounter++}"}"""
                ws.send(hb)
            }
        }

        awaitClose {
            heartbeatJob.cancel()
            ws.close(1000, "Closed")
        }
    }
}
