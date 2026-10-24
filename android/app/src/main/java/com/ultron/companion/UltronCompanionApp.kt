package com.ultron.companion

import android.app.Application
import com.ultron.companion.core.network.MobileApiClient
import com.ultron.companion.core.security.SecureStorageManager
import com.ultron.companion.data.repository.UltronRepository

class UltronCompanionApp : Application() {

    lateinit var secureStorage: SecureStorageManager
        private set

    lateinit var apiClient: MobileApiClient
        private set

    lateinit var repository: UltronRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        secureStorage = SecureStorageManager(applicationContext)
        apiClient = MobileApiClient(secureStorage)
        repository = UltronRepository(secureStorage, apiClient)
    }

    companion object {
        lateinit var instance: UltronCompanionApp
            private set
    }
}
