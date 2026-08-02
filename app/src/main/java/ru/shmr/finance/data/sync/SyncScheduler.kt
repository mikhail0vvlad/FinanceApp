package ru.shmr.finance.data.sync

interface SyncScheduler {
    fun enqueueOneTime()
    fun ensurePeriodic()
}
