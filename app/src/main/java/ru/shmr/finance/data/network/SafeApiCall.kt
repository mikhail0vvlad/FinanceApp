package ru.shmr.finance.data.network

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.domain.model.AppError

suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> = withContext(Dispatchers.IO) {
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        AppResult.Failure(
            when (e.code()) {
                401 -> AppError.Unauthorized
                in 400..499 -> AppError.Client(e.code())
                in 500..599 -> AppError.Server(e.code())
                else -> AppError.Unknown
            },
        )
    } catch (e: IOException) {
        Log.e(TAG, "IO error", e)
        AppResult.Failure(AppError.NoInternet)
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error", e)
        AppResult.Failure(AppError.Unknown)
    }
}

private const val TAG = "SafeApiCall"
