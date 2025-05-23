package dev.carlosalberto.locationtrackerapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "localizacoes")
data class LocalizacaoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val phoneID: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val raio: Float,
    val altitude: Double,
    val precisionAltitude: Double
)