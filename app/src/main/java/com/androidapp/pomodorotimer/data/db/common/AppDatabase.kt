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
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun presetDao(): PresetDao
    abstract fun routineItemDao(): RoutineItemDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("UPDATE routine_items SET type = 'LOOP_START' WHERE type = 'REPEAT_START'")
                database.execSQL("UPDATE routine_items SET type = 'LOOP_END'   WHERE type = 'REPEAT_END'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM routine_items WHERE type IN ('CONDITION_START','CONDITION_END')")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE routine_items ADD COLUMN tickSound TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE presets ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("""
                    UPDATE presets SET `order` = (
                        SELECT COUNT(*) FROM presets p2 WHERE p2.id < presets.id
                    )
                """.trimIndent())
            }
        }

        // v5 → v6: routine_items に tickVolume カラム追加
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE routine_items ADD COLUMN tickVolume INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE presets ADD COLUMN weekdays INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE presets ADD COLUMN triggerTimeOfDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "routine_timer_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}