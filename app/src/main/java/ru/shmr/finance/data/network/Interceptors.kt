package ru.shmr.finance.data.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

internal class AuthInterceptor(private val token: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val value = token().trim()
        val request = if (value.isEmpty()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $value")
                .build()
        }
        return chain.proceed(request)
    }
}

// Сервер периодически отвечает 5xx — повторяем запрос с растущей паузой.
internal class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val retryDelayMillis: Long = 2_000,
    private val sleeper: (Long) -> Unit = Thread::sleep,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method !in RETRYABLE_METHODS) return chain.proceed(request)

        var lastException: IOException? = null
        for (attempt in 0..maxRetries) {
            try {
                val response = chain.proceed(request)
                if (response.code < 500 || attempt == maxRetries) return response
                response.close()
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxRetries) throw e
            }
            try {
                sleeper(retryDelayMillis)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw lastException ?: IOException("Retry interrupted", e)
            }
        }
        throw lastException ?: IOException("Request failed after ${maxRetries + 1} attempts")
    }

    private companion object {
        val RETRYABLE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE", "PUT", "DELETE")
    }
}
