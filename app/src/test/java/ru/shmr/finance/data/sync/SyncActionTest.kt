package ru.shmr.finance.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncActionTest {

    @Test
    fun `editing pending create keeps create action`() {
        assertEquals(SyncAction.CREATE, SyncAction.CREATE.afterLocalEdit())
    }

    @Test
    fun `editing synchronized row schedules update`() {
        assertEquals(SyncAction.UPDATE, SyncAction.NONE.afterLocalEdit())
    }

    @Test
    fun `editing pending update remains update`() {
        assertEquals(SyncAction.UPDATE, SyncAction.UPDATE.afterLocalEdit())
    }
}
