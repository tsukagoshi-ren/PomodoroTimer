package com.androidapp.pomodorotimer.data.db.common

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.androidapp.pomodorotimer.data.db.preset.PresetDao
import com.androidapp.pomodorotimer.data.db.preset.PresetEntity
import com.androidapp.pomodorotimer.data.db.routine.RoutineItemDao
import com.androidapp.pomodorotimer.data.db.routine.RoutineItemEntity

@Database(
    entities = [PresetEntity::class, RoutineItemEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun presetDao(): PresetDao
    abstract fun routineItemDao(): RoutineItemDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // version 1 → 2: REPEAT_START/REPEAT_END を LOOP_START/LOOP_END に変換
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE routine_items SET type = 'LOOP_START' WHERE type = 'REPEAT_START'"
                )
                database.execSQL(
                    "UPDATE routine_items SET type = 'LOOP_END' WHERE type = 'REPEAT_END'"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "routine_timer_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}