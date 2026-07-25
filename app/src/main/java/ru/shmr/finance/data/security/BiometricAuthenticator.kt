package ru.shmr.finance.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.shmr.finance.domain.model.BiometricAvailability
import ru.shmr.finance.domain.model.BiometricOutcome

/**
 * Разбор кодов androidx.biometric. Вынесен отдельно от Android-обёртки, потому что именно
 * эта таблица решает, выключать биометрию, предлагать ПИН-код или молча ничего не делать.
 */
object BiometricResultMapper {

    fun availability(status: Int): BiometricAvailability = when (status) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            BiometricAvailability.HARDWARE_UNAVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            BiometricAvailability.SECURITY_UPDATE_REQUIRED
        else -> BiometricAvailability.UNSUPPORTED
    }

    fun outcome(errorCode: Int, message: CharSequence): BiometricOutcome = when (errorCode) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> BiometricOutcome.Cancelled

        BiometricPrompt.ERROR_LOCKOUT -> BiometricOutcome.Lockout
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricOutcome.LockoutPermanent

        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        -> BiometricOutcome.Unavailable

        else -> BiometricOutcome.Failed(message.toString())
    }
}

/**
 * Проверка отпечатка или лица через системный [BiometricPrompt].
 *
 * Используется класс аутентификаторов `BIOMETRIC_WEAK`: приложение не привязывает к
 * биометрии ключи Keystore, а лишь ускоряет вход, запасной путь по ПИН-коду остаётся
 * всегда. Требовать `BIOMETRIC_STRONG` значило бы отключить вход по лицу на части
 * устройств без выигрыша в защите.
 */
class BiometricAuthenticator(private val context: Context) {

    fun availability(): BiometricAvailability = BiometricResultMapper.availability(
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS),
    )

    /**
     * Показывает системный диалог и ждёт его исхода.
     *
     * `onAuthenticationFailed` (палец не распознан) сознательно не завершает ожидание:
     * системный диалог остаётся на экране и позволяет попробовать снова.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButton: String,
    ): BiometricOutcome = suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            androidx.core.content.ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    if (continuation.isActive) continuation.resume(BiometricOutcome.Success)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    if (continuation.isActive) {
                        continuation.resume(BiometricResultMapper.outcome(errorCode, errString))
                    }
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButton)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info)
    }

    private companion object {
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
}
