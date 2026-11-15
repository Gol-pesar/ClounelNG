package com.example

import android.app.Application
import com.example.data.PreferencesRepository
import com.example.data.ProxyDatabase
import com.example.data.ProxyProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PulseProxyApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ProxyDatabase by lazy { ProxyDatabase.getInstance(this) }
    val profileRepository: ProxyProfileRepository by lazy {
        ProxyProfileRepository(database.profileDao())
    }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val seededId = profileRepository.ensureSeeded()
            if (seededId > 0L) {
                preferencesRepository.setActiveProfileId(seededId)
            }
        }
    }
}
