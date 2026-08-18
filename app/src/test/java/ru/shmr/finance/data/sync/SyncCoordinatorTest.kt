package ru.shmr.finance.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {

    @Test
    fun `concurrent sync entries are serialized`() = runTest {
        val coordinator = SyncCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = launch {
            coordinator.run {
                events += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-end"
                SyncOutcome.SUCCESS
            }
        }
        firstEntered.await()
        val second = launch {
            coordinator.run {
                events += "second"
                SyncOutcome.SUCCESS
            }
        }

        runCurrent()
        assertEquals(listOf("first-start"), events)

        releaseFirst.complete(Unit)
        joinAll(first, second)
        assertEquals(listOf("first-start", "first-end", "second"), events)
    }
}
