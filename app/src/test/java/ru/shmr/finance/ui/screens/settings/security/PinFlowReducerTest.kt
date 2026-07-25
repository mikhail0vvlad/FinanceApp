package ru.shmr.finance.ui.screens.settings.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.domain.model.PinStorageError
import ru.shmr.finance.domain.model.PinVerification

class PinFlowReducerTest {

    @Test
    fun `create flow starts at new pin and change flow starts at current pin`() {
        assertEquals(PinStage.NEW, PinFlowState.initial(PinFlowMode.CREATE).stage)
        assertEquals(PinStage.CURRENT, PinFlowState.initial(PinFlowMode.CHANGE).stage)
    }

    @Test
    fun `matching new pin and confirmation persist the pin`() {
        val confirmStage = type(PinFlowState.initial(PinFlowMode.CREATE), "1234")
        assertEquals(PinStage.CONFIRM, confirmStage.state.stage)
        assertEquals("", confirmStage.state.entry)
        assertNull(confirmStage.command)

        val persisted = type(confirmStage.state, "1234")

        assertEquals(PinFlowCommand.Persist("1234"), persisted.command)
        assertTrue(persisted.state.isBusy)
    }

    @Test
    fun `mismatched confirmation restarts from the new pin stage`() {
        val confirmStage = type(PinFlowState.initial(PinFlowMode.CREATE), "1234")

        val mismatch = type(confirmStage.state, "1235")

        assertEquals(PinStage.NEW, mismatch.state.stage)
        assertEquals(PinFlowError.MISMATCH, mismatch.state.error)
        assertEquals("", mismatch.state.entry)
        assertEquals("", mismatch.state.newPin)
        assertNull(mismatch.command)
    }

    @Test
    fun `change flow verifies the current pin before asking for a new one`() {
        val verifying = type(PinFlowState.initial(PinFlowMode.CHANGE), "0000")

        assertEquals(PinFlowCommand.VerifyCurrent("0000"), verifying.command)
        assertTrue(verifying.state.isBusy)

        val accepted = reducePinFlow(
            verifying.state,
            PinFlowAction.CurrentPinChecked(PinVerification.Match),
        )

        assertEquals(PinStage.NEW, accepted.state.stage)
        assertFalse(accepted.state.isBusy)
        assertEquals("", accepted.state.entry)
    }

    @Test
    fun `wrong current pin keeps the stage and reports the error`() {
        val verifying = type(PinFlowState.initial(PinFlowMode.CHANGE), "0000")

        val rejected = reducePinFlow(
            verifying.state,
            PinFlowAction.CurrentPinChecked(PinVerification.Mismatch),
        )

        assertEquals(PinStage.CURRENT, rejected.state.stage)
        assertEquals(PinFlowError.WRONG_CURRENT, rejected.state.error)
        assertEquals("", rejected.state.entry)
        assertFalse(rejected.state.isBusy)
    }

    @Test
    fun `unreadable credential lets the user set a new pin instead of dead-ending`() {
        val verifying = type(PinFlowState.initial(PinFlowMode.CHANGE), "0000")

        val step = reducePinFlow(
            verifying.state,
            PinFlowAction.CurrentPinChecked(
                PinVerification.Unavailable(PinStorageError.CREDENTIAL_UNREADABLE),
            ),
        )

        assertEquals(PinStage.NEW, step.state.stage)
        assertEquals(PinFlowError.CREDENTIAL_UNREADABLE, step.state.error)
    }

    @Test
    fun `device storage failure during verification does not advance the flow`() {
        val verifying = type(PinFlowState.initial(PinFlowMode.CHANGE), "0000")

        val step = reducePinFlow(
            verifying.state,
            PinFlowAction.CurrentPinChecked(
                PinVerification.Unavailable(PinStorageError.DEVICE_STORAGE_UNAVAILABLE),
            ),
        )

        assertEquals(PinStage.CURRENT, step.state.stage)
        assertEquals(PinFlowError.STORAGE_UNAVAILABLE, step.state.error)
    }

    @Test
    fun `successful persist finishes the flow`() {
        val persisting = type(type(PinFlowState.initial(PinFlowMode.CREATE), "1234").state, "1234")

        val done = reducePinFlow(persisting.state, PinFlowAction.PinPersisted(null))

        assertEquals(PinFlowCommand.Finish, done.command)
        assertTrue(done.state.isFinished)
        assertEquals("", done.state.entry)
        assertEquals("", done.state.newPin)
    }

    @Test
    fun `failed persist restarts at the new pin stage with a device error`() {
        val persisting = type(type(PinFlowState.initial(PinFlowMode.CREATE), "1234").state, "1234")

        val failed = reducePinFlow(
            persisting.state,
            PinFlowAction.PinPersisted(PinStorageError.DEVICE_STORAGE_UNAVAILABLE),
        )

        assertEquals(PinStage.NEW, failed.state.stage)
        assertEquals(PinFlowError.STORAGE_UNAVAILABLE, failed.state.error)
        assertFalse(failed.state.isFinished)
        assertNull(failed.command)
    }

    @Test
    fun `entry never grows past the pin length`() {
        val state = type(PinFlowState.initial(PinFlowMode.CREATE), "1234").state

        // Пятая цифра пришла уже после перехода на повтор — она должна лечь в новый ввод.
        val extra = type(state, "9")

        assertEquals("9", extra.state.entry)
    }

    @Test
    fun `input is ignored while a command is in flight`() {
        val busy = type(PinFlowState.initial(PinFlowMode.CHANGE), "0000").state
        assertTrue(busy.isBusy)

        assertEquals(busy, reducePinFlow(busy, PinFlowAction.Digit('7')).state)
        assertEquals(busy, reducePinFlow(busy, PinFlowAction.Backspace).state)
    }

    @Test
    fun `backspace removes the last digit and clears the error`() {
        val withError = PinFlowState.initial(PinFlowMode.CREATE)
            .copy(entry = "12", error = PinFlowError.MISMATCH)

        val step = reducePinFlow(withError, PinFlowAction.Backspace)

        assertEquals("1", step.state.entry)
        assertNull(step.state.error)
    }

    @Test
    fun `backspace on empty entry is harmless`() {
        val step = reducePinFlow(
            PinFlowState.initial(PinFlowMode.CREATE),
            PinFlowAction.Backspace,
        )

        assertEquals("", step.state.entry)
    }

    @Test
    fun `non digit input is rejected`() {
        val step = reducePinFlow(
            PinFlowState.initial(PinFlowMode.CREATE),
            PinFlowAction.Digit('a'),
        )

        assertEquals("", step.state.entry)
    }

    private fun type(state: PinFlowState, digits: String): PinFlowStep {
        var step = PinFlowStep(state)
        digits.forEach { digit ->
            step = reducePinFlow(step.state, PinFlowAction.Digit(digit))
        }
        return step
    }
}
