package com.focusrefine.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import android.util.Base64

@Database(entities = [NotificationEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds the decision confidence without touching existing rows (the destructive fallback would erase history and learned rules). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN confidence REAL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = getOrGenerateDbKey(context)

                // Migrate from plaintext to SQLCipher if upgrading, with UI fallback on failure
                DatabaseMigratorHelper.migrateWithFallback(context, "focusrefine_database", passphrase)

                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusrefine_database"
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()

                INSTANCE = instance
                instance
            }
        }

        private fun getOrGenerateDbKey(context: Context): ByteArray {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPrefs = EncryptedSharedPreferences.create(
                context,
                "db_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            var keyBase64 = sharedPrefs.getString("db_key", null)
            if (keyBase64 == null) {
                val key = ByteArray(32)
                SecureRandom().nextBytes(key)
                keyBase64 = Base64.encodeToString(key, Base64.DEFAULT)
                sharedPrefs.edit().putString("db_key", keyBase64).apply()
            }

            return Base64.decode(keyBase64, Base64.DEFAULT)
        }
    }
}
