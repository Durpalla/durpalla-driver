package com.durpalla.driver.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private val server = MockWebServer()
    private var token: String? = "tok-123"
    private var expired = 0
    private lateinit var api: ApiClient

    @Before fun setUp() { server.start(); api = ApiClient(server.url("/api/v1/driver/").toString(), { token }, { expired++ }) }
    @After fun tearDown() = server.shutdown()

    @Test fun sendsBearerAndUnwrapsData() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"message":"ok","data":{"id":"7"}}"""))
        val d = api.get("me").getOrThrow()
        assertEquals("7", d.asJsonObject["id"].asString)
        val r = server.takeRequest()
        assertEquals("/api/v1/driver/me", r.path)
        assertEquals("Bearer tok-123", r.getHeader("Authorization"))
    }

    @Test fun postsAsciiJson() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":null}"""))
        api.post("availability/days", mapOf("date" to "2026-11-01", "blocked" to true)).getOrThrow()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"date\":\"2026-11-01\"") && body.contains("\"blocked\":true"))
    }

    @Test fun mapsErrorCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"success":false,"error":{"code":"SLOT_CONFLICT","message":"busy"}}"""))
        val e = api.post("requests/1/bids", mapOf("amount" to 100)).exceptionOrNull() as ApiException
        assertEquals(409, e.status); assertEquals("SLOT_CONFLICT", e.code)
        assertEquals(0, expired)
        assertNotNull(ApiErrors.text(e))
    }

    @Test fun unauthorizedWithTokenExpiresSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))
        val e = api.get("me").exceptionOrNull() as ApiException
        assertEquals(401, e.status); assertEquals(1, expired)
    }

    @Test fun unauthorizedWithoutTokenDoesNotExpire() = runBlocking {
        token = null
        server.enqueue(MockResponse().setResponseCode(401))
        api.post("auth/otp/verify", mapOf("code" to "1")); assertEquals(0, expired)
    }

    @Test fun networkFailureIsNetworkCode() = runBlocking {
        server.shutdown()
        val e = api.get("me").exceptionOrNull() as ApiException
        assertEquals(ApiException.NETWORK, e.code)
    }
}
