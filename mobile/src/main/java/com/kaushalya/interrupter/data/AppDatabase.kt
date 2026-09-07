package com.kaushalya.interrupter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StudySession::class,
        WifiNetwork::class,
        ConnectedTV::class,
        KidProfile::class,
        QuizResult::class,
        PendingFeedback::class
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studySessionDao(): StudySessionDao
    abstract fun historyDao(): HistoryDao
    abstract fun kidProfileDao(): KidProfileDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun pendingFeedbackDao(): PendingFeedbackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v12 → v13: adds the kid mascot-avatar column (defaults to "hero"); keeps all data. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kid_profiles ADD COLUMN avatar TEXT NOT NULL DEFAULT 'hero'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "study_shield_db"
                )
                .addMigrations(MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
