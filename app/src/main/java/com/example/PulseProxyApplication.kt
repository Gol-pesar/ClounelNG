package com.example

import android.app.Application
import com.example.data.PreferencesRepository
import com.example.data.ProxyDatabase
import com.example.data.ProxyProfileRepository

class PulseProxyApplication : Application() {
    val database: ProxyDatabase by lazy { ProxyDatabase.getInstance(this) }
    val profileRepository: ProxyProfileRepository by lazy {
        ProxyProfileRepository(database.profileDao())
    }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(this) }
}
