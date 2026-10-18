package com.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.data.local.dao.*
import com.app.data.local.entity.*

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        UserEntity::class,
        GoalEntity::class,
        PendingOperationEntity::class,
        ChatMessageEntity::class,
        MerchantCategoryMappingEntity::class,
        NotificationEntity::class,
        SharedSpaceEntity::class
    ],
    version = 15,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun userDao(): UserDao
    abstract fun goalDao(): GoalDao
    abstract fun pendingOperationDao(): PendingOperationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun merchantCategoryMappingDao(): MerchantCategoryMappingDao
    abstract fun notificationDao(): NotificationDao
    abstract fun spaceDao(): SpaceDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isAnomaly INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shared_spaces ADD COLUMN expensesJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN alias TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isReviewed INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE merchant_category_mappings ADD COLUMN alias TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE merchant_category_mappings ADD COLUMN frequency INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE merchant_category_mappings ADD COLUMN lastUsedTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
