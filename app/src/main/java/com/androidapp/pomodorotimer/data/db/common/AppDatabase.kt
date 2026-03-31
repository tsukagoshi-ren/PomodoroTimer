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
    version = 5,
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

        // v4 → v5: presets に order カラム追加
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE presets ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
                // 既存行の order を id 昇順で連番に設定
                database.execSQL("""
                    UPDATE presets SET `order` = (
                        SELECT COUNT(*) FROM presets p2 WHERE p2.id < presets.id
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "routine_timer_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}