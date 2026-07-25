package ru.shmr.finance.domain.model

/** Длина ПИН-кода по макету: четыре точки над цифровой клавиатурой. */
const val PIN_LENGTH: Int = 4

/**
 * Состояние защиты приложения. Источник истины для `isPinSet` — хранилище верификатора,
 * а не пользовательские предпочтения: флаг в настройках может рассинхронизироваться,
 * если хранилище повреждено или ключ Keystore стал недействительным.
 */
data class SecurityState(
    val isPinSet: Boolean = false,
    val isBiometricsEnabled: Boolean = false,
)

/** Возможность биометрии на конкретном устройстве. */
enum class BiometricAvailability {
    /** Датчик есть, отпечаток/лицо зарегистрированы — можно включать. */
    AVAILABLE,

    /** Датчика нет вовсе. */
    NO_HARDWARE,

    /** Датчик есть, но временно занят или недоступен. */
    HARDWARE_UNAVAILABLE,

    /** Датчик есть, но пользователь не зарегистрировал ни одного образца. */
    NONE_ENROLLED,

    /** Требуется обновление системы безопасности, чтобы датчик заработал. */
    SECURITY_UPDATE_REQUIRED,

    /** Комбинация ОС/датчика не поддерживается библиотекой. */
    UNSUPPORTED,
}

/** Итог показа [androidx.biometric.BiometricPrompt]. */
sealed interface BiometricOutcome {
    data object Success : BiometricOutcome

    /** Пользователь нажал «Отмена» или «Ввести ПИН-код». */
    data object Cancelled : BiometricOutcome

    /** Слишком много неудачных попыток — датчик заблокирован на время. */
    data object Lockout : BiometricOutcome

    /** Датчик заблокирован до разблокировки устройства паролем. */
    data object LockoutPermanent : BiometricOutcome

    /** Датчик пропал или сломан: биометрию нужно выключить и жить с ПИН-кодом. */
    data object Unavailable : BiometricOutcome

    /** Прочая ошибка устройства; [message] приходит от системы и уже локализован. */
    data class Failed(val message: String) : BiometricOutcome
}

/** Причина, по которой запись или проверка ПИН-кода не удалась. */
enum class PinStorageError {
    /**
     * Верификатор есть, но расшифровать его нечем: ключ Keystore удалён или стал
     * недействительным. ПИН-код придётся создать заново.
     */
    CREDENTIAL_UNREADABLE,

    /** Keystore или файловое хранилище недоступны на этом устройстве. */
    DEVICE_STORAGE_UNAVAILABLE,
}

/** Результат проверки введённого ПИН-кода. */
sealed interface PinVerification {
    data object Match : PinVerification

    data object Mismatch : PinVerification

    data class Unavailable(val error: PinStorageError) : PinVerification
}
