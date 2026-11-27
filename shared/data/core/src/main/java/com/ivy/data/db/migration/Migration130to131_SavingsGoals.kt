package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the table behind multiple savings goals.
 *
 * Purely additive: the old single-figure buffer stays in `settings`, so a
 * wallet that upgrades keeps whatever it had and nothing has to be guessed or
 * converted.
 */
class Migration130to131_SavingsGoals : Migration(130, 131) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_goals` (" +
                "`name` TEXT NOT NULL, " +
                "`targetAmount` REAL NOT NULL, " +
                "`savedAmount` REAL NOT NULL, " +
                "`currency` TEXT, " +
                "`colorArgb` INTEGER, " +
                "`iconName` TEXT, " +
                "`targetDateMillis` INTEGER, " +
                "`archived` INTEGER NOT NULL, " +
                "`orderNum` REAL NOT NULL, " +
                // TEXT, not BLOB: the database's type converters store a UUID
                // as its string form, and Room validates the declared affinity
                // against the entity on open - a BLOB here crashes the upgrade.
                "`id` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}
