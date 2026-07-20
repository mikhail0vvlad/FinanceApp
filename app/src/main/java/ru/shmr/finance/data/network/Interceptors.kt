package ru.shmr.finance.data.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val token: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${token()}")
                .build(),
        )
}

// Сервер периодически отвечает 5xx — повторяем запрос с растущей паузой.
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val delayMillis: Long = 500,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var lastException: IOException? = null
        for (attempt in 1..maxAttempts) {
            try {
                val response = chain.proceed(chain.request())
                if (response.code < 500 || attempt == maxAttempts) return response
                response.close()
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxAttempts) throw e
            }
            try {
                Thread.sleep(delayMillis * attempt)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw lastException ?: IOException("Retry interrupted", e)
            }
        }
        throw lastException ?: IOException("Request failed after $maxAttempts attempts")
    }
}
