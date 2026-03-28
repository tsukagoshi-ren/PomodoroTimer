package com.androidapp.pomodorotimer.data.db.common

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.androidapp.pomodorotimer.data.db.preset.PresetDao
import com.androidapp.pomodorotimer.data.db.preset.PresetEntity
import com.androidapp.pomodorotimer.data.db.routine.RoutineItemDao
import com.androidapp.pomodorotimer.data.db.routine.RoutineItemEntity

@Database(
    entities = [PresetEntity::class, RoutineItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun presetDao(): PresetDao
    abstract fun routineItemDao(): RoutineItemDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "routine_timer_database"
                ).build().also { instance = it }
            }
        }
    }
}