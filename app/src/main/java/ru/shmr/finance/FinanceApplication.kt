package ru.shmr.finance

import android.app.Application
import ru.shmr.finance.di.ServiceLocator

class FinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
