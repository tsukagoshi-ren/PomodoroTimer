package com.androidapp.pomodorotimer

import android.app.Application
import com.androidapp.pomodorotimer.data.db.common.AppDatabase
import com.androidapp.pomodorotimer.data.repository.preset.PresetRepository

class App : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val presetRepository by lazy { PresetRepository(database.presetDao()) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}