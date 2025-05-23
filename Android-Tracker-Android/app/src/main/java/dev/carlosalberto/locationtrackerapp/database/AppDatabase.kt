package dev.carlosalberto.locationtrackerapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocalizacaoEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localizacaoDao(): LocalizacaoDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    // Migração da versão 1 para 2: Adicionar colunas
                    .addMigrations(object : Migration(1, 2) {
                        override fun migrate(database: SupportSQLiteDatabase) {
                            try {
                                database.execSQL("ALTER TABLE localizacoes ADD COLUMN phoneID INTEGER NOT NULL DEFAULT 0")
                            } catch (e: Exception) {
                                if (!e.message?.contains("duplicate column name")!!) throw e
                            }
                            try {
                                database.execSQL("ALTER TABLE localizacoes ADD COLUMN raio REAL NOT NULL DEFAULT 0.0")
                            } catch (e: Exception) {
                                if (!e.message?.contains("duplicate column name")!!) throw e
                            }
                            try {
                                database.execSQL("ALTER TABLE localizacoes ADD COLUMN altitude REAL NOT NULL DEFAULT 0.0")
                            } catch (e: Exception) {
                                if (!e.message?.contains("duplicate column name")!!) throw e
                            }
                            try {
                                database.execSQL("ALTER TABLE localizacoes ADD COLUMN precisionAltitude REAL NOT NULL DEFAULT 0.0")
                            } catch (e: Exception) {
                                if (!e.message?.contains("duplicate column name")!!) throw e
                            }
                        }
                    })
                    // Migração da versão 2 para 3: Corrigir tipo de phoneID
                    .addMigrations(object : Migration(2, 3) {
                        override fun migrate(database: SupportSQLiteDatabase) {
                            // Criar tabela temporária com esquema corrigido
                            database.execSQL("""
                            CREATE TABLE localizacoes_temp (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                phoneID TEXT NOT NULL DEFAULT 0,
                                latitude REAL NOT NULL,
                                longitude REAL NOT NULL,
                                timestamp INTEGER NOT NULL,
                                raio REAL NOT NULL DEFAULT 0.0,
                                altitude REAL NOT NULL DEFAULT 0.0,
                                precisionAltitude REAL NOT NULL DEFAULT 0.0
                            )
                        """.trimIndent())

                            // Copiar dados da tabela antiga para a nova
                            database.execSQL("""
                            INSERT INTO localizacoes_temp (id, phoneID, latitude, longitude, timestamp, raio, altitude, precisionAltitude)
                            SELECT id, phoneID, latitude, longitude, timestamp, raio, altitude, precisionAltitude FROM localizacoes
                        """)

                            // Apagar a tabela antiga
                            database.execSQL("DROP TABLE localizacoes")

                            // Renomear tabela temporária para o nome original
                            database.execSQL("ALTER TABLE localizacoes_temp RENAME TO localizacoes")
                        }
                    })
                    .build().also { instance = it }
            }
    }
}
