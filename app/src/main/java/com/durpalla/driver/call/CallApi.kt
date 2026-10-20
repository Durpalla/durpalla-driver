package com.durpalla.driver.call

import com.durpalla.driver.data.ApiJson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** REST side of calling (docs/calling-contract.md). All calls use `Authorization: Bearer <token>`. */
interface CallApi {
    suspend fun start(tripId: String): Result<String>                    // POST /calls {tripId} -> {callId}
    suspend fun accept(callId: String): Result<Unit>                     // POST /calls/{callId}/accept
    suspend fun decline(callId: String): Result<Unit>                    // POST /calls/{callId}/decline
    suspend fun end(callId: String, reason: String): Result<Unit>        // POST /calls/{callId}/end {reason}
    suspend fun iceServers(): Result<IceConfig>                          // GET /calls/ice-servers
}

object CallJson {
    data class StartCall(val tripId: String)
    data class EndCall(val reason: String)
    fun start(tripId: String): String = ApiJson.gson.toJson(StartCall(tripId))
    fun end(reason: String): String = ApiJson.gson.toJson(EndCall(reason))
    fun callId(body: String): String = JsonParser.parseString(body).asJsonObject["callId"].asString

    /** Accepts `[{urls,username,credential}]` or `{iceServers:[...], ttl}`; `urls` may be a string or an array. */
    fun iceServers(body: String): IceConfig {
        val root = JsonParser.parseString(body)
        val arr = if (root.isJsonArray) root.asJsonArray else root.asJsonObject["iceServers"].asJsonArray
        val ttl = if (root.isJsonObject) root.asJsonObject["ttl"]?.takeIf { !it.isJsonNull }?.asLong ?: 0 else 0
        val list = arr.map { e ->
            val o = e.asJsonObject; val u = o["urls"]
            IceServerDto(if (u.isJsonArray) u.asJsonArray.map { it.asString } else listOf(u.asString),
                o["username"]?.takeIf { !it.isJsonNull }?.asString, o["credential"]?.takeIf { !it.isJsonNull }?.asString)
        }
        return IceConfig(list.ifEmpty { IceConfig.FALLBACK.servers }, ttl)
    }

    fun error(status: Int, body: String?): CallError = runCatching {
        CallError.of(JsonParser.parseString(body).asJsonObject["error"].asJsonObject["code"].asString)
    }.getOrElse { if (status == 401) CallError.UNAUTHORIZED else if (status == 429) CallError.RATE_LIMITED else CallError.UNKNOWN }
}

class HttpCallApi(private val baseUrl: String, private val token: () -> String?) : CallApi {
    private var cachedIce: Pair<IceConfig, Long>? = null

    private suspend fun call(method: String, path: String, body: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            c.requestMethod = method; c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            token()?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
            if (code in 200..299) Result.success(text.orEmpty()) else Result.failure(CallException(CallJson.error(code, text)))
        } catch (e: IOException) { Result.failure(CallException(CallError.NETWORK, e.message)) }
    }

    override suspend fun start(tripId: String) = call("POST", "/calls", CallJson.start(tripId)).mapCatching { CallJson.callId(it) }
    override suspend fun accept(callId: String) = call("POST", "/calls/$callId/accept", "{}").map { }
    override suspend fun decline(callId: String) = call("POST", "/calls/$callId/decline", "{}").map { }
    override suspend fun end(callId: String, reason: String) = call("POST", "/calls/$callId/end", CallJson.end(reason)).map { }

    /** TURN creds are short-lived: cached for half the TTL, and STUN is used if the endpoint fails. */
    override suspend fun iceServers(): Result<IceConfig> {
        cachedIce?.let { (cfg, exp) -> if (System.currentTimeMillis() < exp) return Result.success(cfg) }
        val r = call("GET", "/calls/ice-servers").mapCatching { CallJson.iceServers(it) }
        r.getOrNull()?.let { cachedIce = it to System.currentTimeMillis() + it.ttlSeconds * 500 }
        return Result.success(r.getOrDefault(IceConfig.FALLBACK))
    }
}
