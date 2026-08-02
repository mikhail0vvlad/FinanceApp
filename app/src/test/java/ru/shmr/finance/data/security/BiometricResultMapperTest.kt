package ru.shmr.finance.data.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.shmr.finance.domain.model.BiometricAvailability
import ru.shmr.finance.domain.model.BiometricOutcome

/**
 * Именно эта таблица решает, выключать ли биометрию и показывать ли ошибку, поэтому
 * состояния устройства проверяются здесь, а не только руками на телефоне.
 */
class BiometricResultMapperTest {

    @Test
    fun `device states map to availability`() {
        assertEquals(
            BiometricAvailability.AVAILABLE,
            BiometricResultMapper.availability(BiometricManager.BIOMETRIC_SUCCESS),
        )
        assertEquals(
            BiometricAvailability.NO_HARDWARE,
            BiometricResultMapper.availability(
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            ),
        )
        assertEquals(
            BiometricAvailability.NONE_ENROLLED,
            BiometricResultMapper.availability(
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            ),
        )
        assertEquals(
            BiometricAvailability.HARDWARE_UNAVAILABLE,
            BiometricResultMapper.availability(
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            ),
        )
        assertEquals(
            BiometricAvailability.SECURITY_UPDATE_REQUIRED,
            BiometricResultMapper.availability(
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            ),
        )
    }

    @Test
    fun `unknown status is treated as unsupported rather than available`() {
        assertEquals(
            BiometricAvailability.UNSUPPORTED,
            BiometricResultMapper.availability(BiometricManager.BIOMETRIC_STATUS_UNKNOWN),
        )
        assertEquals(BiometricAvailability.UNSUPPORTED, BiometricResultMapper.availability(9999))
    }

    @Test
    fun `every way of dismissing the prompt counts as cancellation`() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
        ).forEach { code ->
            assertEquals(
                BiometricOutcome.Cancelled,
                BiometricResultMapper.outcome(code, "отменено"),
            )
        }
    }

    @Test
    fun `lockouts are distinguished from one another`() {
        assertEquals(
            BiometricOutcome.Lockout,
            BiometricResultMapper.outcome(BiometricPrompt.ERROR_LOCKOUT, ""),
        )
        assertEquals(
            BiometricOutcome.LockoutPermanent,
            BiometricResultMapper.outcome(BiometricPrompt.ERROR_LOCKOUT_PERMANENT, ""),
        )
    }

    @Test
    fun `missing hardware and enrollment mean biometrics must be switched off`() {
        listOf(
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
        ).forEach { code ->
            assertEquals(
                BiometricOutcome.Unavailable,
                BiometricResultMapper.outcome(code, "нет датчика"),
            )
        }
    }

    @Test
    fun `unclassified errors keep the localized system message`() {
        assertEquals(
            BiometricOutcome.Failed("Сенсор загрязнён"),
            BiometricResultMapper.outcome(BiometricPrompt.ERROR_VENDOR, "Сенсор загрязнён"),
        )
    }
}
