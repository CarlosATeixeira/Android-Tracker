package dev.carlosalberto.locationtrackerapp.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("enviarLocalizacao")
    fun enviarLocalizacao(@Body localizacao: LocalizacaoData): Call<Void>
}
