package ru.shmr.finance.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.shmr.finance.data.sync.SyncScheduler

class NetworkMonitor(
    context: Context,
    private val syncScheduler: SyncScheduler,
) {

    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(currentStatus())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update()
        override fun onLost(network: Network) = update()
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = update()
    }

    fun start() {
        connectivityManager.registerDefaultNetworkCallback(callback)
        update()
    }

    private fun update() {
        val online = currentStatus()
        val restored = !_isOnline.value && online
        _isOnline.value = online
        if (restored) syncScheduler.enqueueOneTime()
    }

    private fun currentStatus(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
