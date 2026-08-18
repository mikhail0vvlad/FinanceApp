package ru.shmr.finance.data.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `retries server error three times with two second interval`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val delays = mutableListOf<Long>()
        val client = client(delays::add)

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(4, server.requestCount)
        assertEquals(listOf(2_000L, 2_000L, 2_000L), delays)
    }

    @Test
    fun `does not retry client error`() {
        server.enqueue(MockResponse().setResponseCode(400))
        val delays = mutableListOf<Long>()

        client(delays::add)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()
            .use { response -> assertEquals(400, response.code) }

        assertEquals(1, server.requestCount)
        assertEquals(emptyList<Long>(), delays)
    }

    @Test
    fun `returns final server response after retry budget is exhausted`() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(503)) }

        client { }
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()
            .use { response -> assertEquals(503, response.code) }

        assertEquals(4, server.requestCount)
    }

    @Test
    fun `does not retry non idempotent post after server error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))
        val delays = mutableListOf<Long>()
        val request = Request.Builder()
            .url(server.url("/transactions"))
            .post("{}".toRequestBody())
            .build()

        client(delays::add).newCall(request).execute().use { response ->
            assertEquals(500, response.code)
        }

        assertEquals(1, server.requestCount)
        assertEquals(emptyList<Long>(), delays)
    }

    private fun client(sleeper: (Long) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .addInterceptor(
                RetryInterceptor(
                    maxRetries = 3,
                    retryDelayMillis = 2_000,
                    sleeper = sleeper,
                ),
            )
            .build()
}
