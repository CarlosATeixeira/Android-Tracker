package dev.carlosalberto.locationtrackerapp.api

data class LocalizacaoData(
    val phoneID: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val raio: Float,
    val altitude: Double,
    val precisionAltitude: Double
)
