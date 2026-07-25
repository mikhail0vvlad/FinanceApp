package ru.shmr.finance.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.domain.model.BiometricOutcome
import ru.shmr.finance.domain.model.PinStorageError
import ru.shmr.finance.domain.model.PinVerification
import ru.shmr.finance.domain.model.SecurityState

class AppLockReducerTest {

    @Test
    fun `app without a pin is never locked`() {
        val step = reduceAppLock(
            AppLockState(),
            AppLockAction.SecurityStateChanged(SecurityState(isPinSet = false)),
        )

        assertEquals(LockPhase.UNLOCKED, step.state.phase)
        assertNull(step.command)
    }

    @Test
    fun `cold start with a configured pin locks the app`() {
        val step = lockedStart()

        assertEquals(LockPhase.LOCKED, step.state.phase)
        assertNull(step.command)
    }

    @Test
    fun `cold start with biometrics enabled asks for the prompt immediately`() {
        val step = reduceAppLock(
            AppLockState(),
            AppLockAction.SecurityStateChanged(
                SecurityState(isPinSet = true, isBiometricsEnabled = true),
            ),
        )

        assertEquals(LockPhase.LOCKED, step.state.phase)
        assertTrue(step.state.isBiometricPromptPending)
    }

    @Test
    fun `pending prompt survives a late subscriber because it lives in state`() {
        // Регресс: холодный старт запирал приложение раньше, чем композиция успевала
        // подписаться, и одноразовое событие показа диалога терялось.
        val locked = lockedStart(biometricsEnabled = true).state

        val replayed = reduceAppLock(
            locked,
            AppLockAction.SecurityStateChanged(
                SecurityState(isPinSet = true, isBiometricsEnabled = true),
            ),
        )

        assertTrue(replayed.state.isBiometricPromptPending)
    }

    @Test
    fun `every prompt outcome clears the pending request`() {
        val locked = lockedStart(biometricsEnabled = true).state
        assertTrue(locked.isBiometricPromptPending)

        listOf(
            BiometricOutcome.Success,
            BiometricOutcome.Cancelled,
            BiometricOutcome.Lockout,
            BiometricOutcome.LockoutPermanent,
            BiometricOutcome.Unavailable,
            BiometricOutcome.Failed("сбой"),
        ).forEach { outcome ->
            val step = reduceAppLock(locked, AppLockAction.BiometricFinished(outcome))
            assertFalse(
                "Запрос не снят после $outcome",
                step.state.isBiometricPromptPending,
            )
        }
    }

    @Test
    fun `correct pin unlocks the app`() {
        val verifying = type(lockedStart().state, "1234")
        assertEquals(AppLockCommand.VerifyPin("1234"), verifying.command)
        assertTrue(verifying.state.isVerifying)

        val unlocked = reduceAppLock(
            verifying.state,
            AppLockAction.PinChecked(PinVerification.Match),
        )

        assertEquals(LockPhase.UNLOCKED, unlocked.state.phase)
        assertEquals("", unlocked.state.entry)
        assertFalse(unlocked.state.isVerifying)
    }

    @Test
    fun `wrong pin keeps the app locked and clears the entry`() {
        val verifying = type(lockedStart().state, "1234")

        val rejected = reduceAppLock(
            verifying.state,
            AppLockAction.PinChecked(PinVerification.Mismatch),
        )

        assertEquals(LockPhase.LOCKED, rejected.state.phase)
        assertEquals(AppLockError.WrongPin, rejected.state.error)
        assertEquals("", rejected.state.entry)
    }

    @Test
    fun `unverifiable credential releases the lock instead of trapping the user`() {
        val verifying = type(lockedStart().state, "1234")

        val step = reduceAppLock(
            verifying.state,
            AppLockAction.PinChecked(
                PinVerification.Unavailable(PinStorageError.CREDENTIAL_UNREADABLE),
            ),
        )

        assertEquals(LockPhase.UNLOCKED, step.state.phase)
        assertFalse(step.state.isPinSet)
        assertEquals(AppLockError.CredentialReset, step.state.error)
        assertEquals(AppLockCommand.ResetBrokenCredential, step.command)
    }

    @Test
    fun `short trip to background stays unlocked`() {
        val unlocked = unlockedState()

        val backgrounded = reduceAppLock(unlocked, AppLockAction.MovedToBackground(1_000L))
        val returned = reduceAppLock(
            backgrounded.state,
            AppLockAction.MovedToForeground(1_000L + LOCK_GRACE_PERIOD_MILLIS - 1),
        )

        assertEquals(LockPhase.UNLOCKED, returned.state.phase)
        assertNull(returned.state.backgroundedAtMillis)
    }

    @Test
    fun `returning after the grace period locks the app again`() {
        val unlocked = unlockedState()

        val backgrounded = reduceAppLock(unlocked, AppLockAction.MovedToBackground(1_000L))
        val returned = reduceAppLock(
            backgrounded.state,
            AppLockAction.MovedToForeground(1_000L + LOCK_GRACE_PERIOD_MILLIS),
        )

        assertEquals(LockPhase.LOCKED, returned.state.phase)
        assertNull(returned.state.backgroundedAtMillis)
    }

    @Test
    fun `returning after the grace period reoffers biometrics`() {
        val unlocked = unlockedState(biometricsEnabled = true)

        val backgrounded = reduceAppLock(unlocked, AppLockAction.MovedToBackground(0L))
        val returned = reduceAppLock(
            backgrounded.state,
            AppLockAction.MovedToForeground(LOCK_GRACE_PERIOD_MILLIS),
        )

        assertTrue(returned.state.isBiometricPromptPending)
    }

    @Test
    fun `background is not recorded while the app is already locked`() {
        val locked = lockedStart().state

        val step = reduceAppLock(locked, AppLockAction.MovedToBackground(500L))

        assertNull(step.state.backgroundedAtMillis)
    }

    @Test
    fun `foreground without a recorded background does nothing`() {
        val unlocked = unlockedState()

        val step = reduceAppLock(unlocked, AppLockAction.MovedToForeground(10_000_000L))

        assertEquals(LockPhase.UNLOCKED, step.state.phase)
    }

    @Test
    fun `successful biometrics unlocks the app`() {
        val locked = lockedStart(biometricsEnabled = true).state

        val step = reduceAppLock(
            locked,
            AppLockAction.BiometricFinished(BiometricOutcome.Success),
        )

        assertEquals(LockPhase.UNLOCKED, step.state.phase)
    }

    @Test
    fun `cancelled biometrics leaves the pin keypad without an error`() {
        val locked = lockedStart(biometricsEnabled = true).state

        val step = reduceAppLock(
            locked,
            AppLockAction.BiometricFinished(BiometricOutcome.Cancelled),
        )

        assertEquals(LockPhase.LOCKED, step.state.phase)
        assertNull(step.state.error)
        assertNull(step.command)
    }

    @Test
    fun `biometric lockout is reported but the pin still works`() {
        val locked = lockedStart(biometricsEnabled = true).state

        val lockout = reduceAppLock(
            locked,
            AppLockAction.BiometricFinished(BiometricOutcome.Lockout),
        )
        assertEquals(AppLockError.BiometricLockout, lockout.state.error)
        assertEquals(LockPhase.LOCKED, lockout.state.phase)

        val unlocked = reduceAppLock(
            type(lockout.state, "1234").state,
            AppLockAction.PinChecked(PinVerification.Match),
        )
        assertEquals(LockPhase.UNLOCKED, unlocked.state.phase)
    }

    @Test
    fun `permanent lockout is reported separately`() {
        val locked = lockedStart(biometricsEnabled = true).state

        val step = reduceAppLock(
            locked,
            AppLockAction.BiometricFinished(BiometricOutcome.LockoutPermanent),
        )

        assertEquals(AppLockError.BiometricLockoutPermanent, step.state.error)
    }

    @Test
    fun `missing hardware turns biometrics off`() {
        val locked = lockedStart(biometricsEnabled = true).state

        val step = reduceAppLock(
            locked,
            AppLockAction.BiometricFinished(BiometricOutcome.Unavailable),
        )

        assertFalse(step.state.isBiometricsEnabled)
        assertEquals(AppLockCommand.DisableBiometrics, step.command)
    }

    @Test
    fun `device failure message is carried to the ui`() {
        val locked = lockedStart(biometricsEnabled = true).state

        val step = reduceAppLock(
            locked,
            AppLockAction.BiometricFinished(BiometricOutcome.Failed("Сенсор загрязнён")),
        )

        assertEquals(AppLockError.BiometricFailed("Сенсор загрязнён"), step.state.error)
    }

    @Test
    fun `biometric prompt is not offered when biometrics are off`() {
        val locked = lockedStart().state

        val step = reduceAppLock(locked, AppLockAction.BiometricRequested)

        assertFalse(step.state.isBiometricPromptPending)
    }

    @Test
    fun `unlocking by pin cancels any pending prompt`() {
        val verifying = type(lockedStart(biometricsEnabled = true).state, "1234")

        val unlocked = reduceAppLock(
            verifying.state,
            AppLockAction.PinChecked(PinVerification.Match),
        )

        assertFalse(unlocked.state.isBiometricPromptPending)
    }

    @Test
    fun `keypad is inert while the app is unlocked`() {
        val unlocked = unlockedState()

        val step = reduceAppLock(unlocked, AppLockAction.Digit('1'))

        assertEquals("", step.state.entry)
        assertNull(step.command)
    }

    @Test
    fun `keypad is inert while a pin check is in flight`() {
        val verifying = type(lockedStart().state, "1234").state

        assertEquals(verifying, reduceAppLock(verifying, AppLockAction.Digit('5')).state)
        assertEquals(verifying, reduceAppLock(verifying, AppLockAction.Backspace).state)
    }

    @Test
    fun `disabling the pin from settings releases an unlocked session`() {
        val unlocked = unlockedState()

        val step = reduceAppLock(
            unlocked,
            AppLockAction.SecurityStateChanged(SecurityState(isPinSet = false)),
        )

        assertEquals(LockPhase.UNLOCKED, step.state.phase)
        assertFalse(step.state.isPinSet)
    }

    @Test
    fun `security updates do not unlock an already locked app`() {
        val locked = lockedStart().state

        val step = reduceAppLock(
            locked,
            AppLockAction.SecurityStateChanged(
                SecurityState(isPinSet = true, isBiometricsEnabled = true),
            ),
        )

        assertEquals(LockPhase.LOCKED, step.state.phase)
        assertTrue(step.state.isBiometricsEnabled)
        assertNull(step.command)
    }

    private fun lockedStart(biometricsEnabled: Boolean = false): AppLockStep = reduceAppLock(
        AppLockState(),
        AppLockAction.SecurityStateChanged(
            SecurityState(isPinSet = true, isBiometricsEnabled = biometricsEnabled),
        ),
    )

    private fun unlockedState(biometricsEnabled: Boolean = false): AppLockState = reduceAppLock(
        type(lockedStart(biometricsEnabled).state, "1234").state,
        AppLockAction.PinChecked(PinVerification.Match),
    ).state

    private fun type(state: AppLockState, digits: String): AppLockStep {
        var step = AppLockStep(state)
        digits.forEach { digit ->
            step = reduceAppLock(step.state, AppLockAction.Digit(digit))
        }
        return step
    }
}
