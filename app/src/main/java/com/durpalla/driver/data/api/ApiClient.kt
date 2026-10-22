package com.durpalla.driver.data.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** API failure: [code] is the apigw `error.code` (e.g. SLOT_CONFLICT) or NETWORK / HTTP_<status>. */
class ApiException(val status: Int, val code: String, override val message: String = code) : Exception(message) {
    companion object { const val NETWORK = "NETWORK"; const val UNAUTHORIZED = "UNAUTHORIZED" }
}

/** Adds `Authorization: Bearer` and logs out on 401 (only for requests that carried a token). */
class AuthInterceptor(private val token: () -> String?, private val onUnauthorized: () -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val t = token()
        val req = chain.request().newBuilder().header("Accept", "application/json").apply { if (t != null) header("Authorization", "Bearer $t") }.build()
        val res = chain.proceed(req)
        if (res.code == 401 && t != null) onUnauthorized()
        return res
    }
}

/**
 * Durpalla apigw driver API (`/api/v1/driver/`). Envelope `{success, message, data}`; errors
 * `{error:{code, message}}`. Returns `data` (JsonNull when absent).
 */
class ApiClient(baseUrl: String, token: () -> String?, onUnauthorized: () -> Unit, http: OkHttpClient? = null) {
    private val base = baseUrl.trimEnd('/') + "/"
    val http: OkHttpClient = (http?.newBuilder() ?: OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS))
        .addInterceptor(AuthInterceptor(token, onUnauthorized)).build()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    suspend fun get(path: String, query: Map<String, String?> = emptyMap()) = exec(Request.Builder().url(url(path, query)).get())
    suspend fun post(path: String, body: Any? = null, headers: Map<String, String> = emptyMap()) = exec(Request.Builder().url(url(path)).post(body(body)).apply { headers.forEach { (k, v) -> header(k, v) } })
    suspend fun post(path: String, body: RequestBody) = exec(Request.Builder().url(url(path)).post(body))
    suspend fun put(path: String, body: Any? = null) = exec(Request.Builder().url(url(path)).put(body(body)))
    suspend fun patch(path: String, body: Any?) = exec(Request.Builder().url(url(path)).patch(body(body)))
    suspend fun delete(path: String, body: Any? = null) = exec(Request.Builder().url(url(path)).delete(body?.let(::body)))

    private fun url(path: String, query: Map<String, String?> = emptyMap()) = (base + path.trimStart('/')).toHttpUrl().newBuilder()
        .apply { query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) } }.build()
    private fun body(b: Any?): RequestBody = (if (b == null) "{}" else gson.toJson(b)).toRequestBody(json)

    private suspend fun exec(b: Request.Builder): Result<JsonElement> = withContext(Dispatchers.IO) {
        try {
            http.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val root = runCatching { JsonParser.parseString(text) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                if (r.isSuccessful) Result.success(root?.get("data") ?: JsonNull.INSTANCE)
                else Result.failure(error(r.code, root))
            }
        } catch (e: IOException) { Result.failure(ApiException(0, ApiException.NETWORK, e.message ?: "network")) }
    }

    private fun error(status: Int, root: JsonObject?): ApiException {
        val err = root?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
        val code = err?.get("code")?.takeIf { !it.isJsonNull }?.asString ?: root?.get("code")?.takeIf { !it.isJsonNull }?.asString
            ?: when (status) { 401 -> ApiException.UNAUTHORIZED; 429 -> "RATE_LIMITED"; 422 -> "VALIDATION_ERROR"; else -> "HTTP_$status" }
        val msg = err?.get("message")?.takeIf { !it.isJsonNull }?.asString ?: root?.get("message")?.takeIf { !it.isJsonNull }?.asString ?: code
        return ApiException(status, code, msg)
    }
}
