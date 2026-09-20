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
        PendingFeedback::class,
        PendingOp::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studySessionDao(): StudySessionDao
    abstract fun historyDao(): HistoryDao
    abstract fun kidProfileDao(): KidProfileDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun pendingFeedbackDao(): PendingFeedbackDao
    abstract fun pendingOpDao(): PendingOpDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v12 → v13: adds the kid mascot-avatar column (defaults to "hero"); keeps all data. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kid_profiles ADD COLUMN avatar TEXT NOT NULL DEFAULT 'hero'")
            }
        }

        /** v13 → v14: adds the local-only kid photo URI column (nullable, no backend sync). */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kid_profiles ADD COLUMN photoUri TEXT")
            }
        }

        /** v14 → v15: creates the offline op queue table (pending kid deletes). */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_ops` (" +
                        "`id` TEXT NOT NULL, " +
                        "`opType` TEXT NOT NULL, " +
                        "`targetRemoteId` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "study_shield_db"
                )
                .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
