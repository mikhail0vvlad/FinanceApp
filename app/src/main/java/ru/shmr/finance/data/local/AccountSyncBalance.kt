package ru.shmr.finance.data.local

import java.math.BigDecimal

/**
 * `syncBalance` is the base the server should apply pending transactions on top of.
 * A local edit that doesn't touch the displayed balance must not shift that base,
 * so any change is carried as a delta against the currently displayed balance.
 */
fun resolveSyncBalance(
    existingBalance: BigDecimal,
    existingSyncBalance: BigDecimal,
    newBalance: BigDecimal,
): BigDecimal = existingSyncBalance + (newBalance - existingBalance)
