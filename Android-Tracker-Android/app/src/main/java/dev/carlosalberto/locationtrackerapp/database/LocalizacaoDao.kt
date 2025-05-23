package dev.carlosalberto.locationtrackerapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocalizacaoDao {
    @Insert
    suspend fun inserir(localizacao: LocalizacaoEntity)

    @Query("SELECT * FROM localizacoes ORDER BY timestamp ASC")
    suspend fun listarTodas(): List<LocalizacaoEntity>

    @Query("DELETE FROM localizacoes WHERE id IN (:ids)")
    suspend fun deletarPorIds(ids: List<Int>)
}
