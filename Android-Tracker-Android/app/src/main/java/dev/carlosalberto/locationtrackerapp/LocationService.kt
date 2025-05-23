package dev.carlosalberto.locationtrackerapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationRequest
import dev.carlosalberto.locationtrackerapp.api.LocalizacaoData
import dev.carlosalberto.locationtrackerapp.api.RetrofitClient
import dev.carlosalberto.locationtrackerapp.database.AppDatabase
import dev.carlosalberto.locationtrackerapp.database.LocalizacaoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException


class LocationService : Service() {
    companion object {
        const val ACTION_LOCATION_UPDATE = "dev.carlosalberto.LOCATION_UPDATE"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val db by lazy { AppDatabase.getDatabase(applicationContext) }
    private val api by lazy { RetrofitClient.instance }
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(0)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val loc = result.lastLocation ?: return

                Log.d("LocationService", "→ onLocationResult: lat=${loc.latitude}, lon=${loc.longitude}, acc=${loc.accuracy}")

                Intent(ACTION_LOCATION_UPDATE).apply {
                    putExtra("latitude",  loc.latitude)
                    putExtra("longitude", loc.longitude)
                    putExtra("precisao",  loc.accuracy.toDouble())
                }.also {
                    Log.d("LocationService", "   broadcasting to UI…")
                    sendBroadcast(it)
                }


                // 1. Puxa o ID do aparelho
                val phoneID = Settings.Secure.getString(
                    applicationContext.contentResolver,
                    Settings.Secure.ANDROID_ID
                )

                // 2. Agora passa o phoneID junto no construtor
                val entity = LocalizacaoEntity(
                    phoneID             = phoneID,
                    latitude            = loc.latitude,
                    longitude           = loc.longitude,
                    timestamp           = System.currentTimeMillis(),
                    raio                = loc.accuracy,
                    altitude            = loc.altitude,
                    precisionAltitude   = loc.verticalAccuracyMeters.toDouble()
                )

                // 3. Continua com a lógica de envio/fallback ao banco
                ioScope.launch {
                    try {
                        val response = api.enviarLocalizacao(
                            LocalizacaoData(
                                phoneID           = phoneID,
                                latitude          = entity.latitude,
                                longitude         = entity.longitude,
                                timestamp         = entity.timestamp,
                                raio              = entity.raio,
                                altitude          = entity.altitude,
                                precisionAltitude = entity.precisionAltitude
                            )
                        ).execute()

                        Log.d("API", "response: code=${response.code()}, body=${response.errorBody()?.string()}")

                        if (!response.isSuccessful) {
                            db.localizacaoDao().inserir(entity)
                        } else {
                            sincronizarDadosPendentes()
                        }
                    } catch (e: IOException) {
                        db.localizacaoDao().inserir(entity)
                        Log.e("API", "Exception: ${e.message}", e)
                    }
                }
            }
        }

        iniciarServico()
    }

    private fun iniciarServico() {
        val channelId = "location_channel"
        val channelName = "Location Tracking"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rastreamento de Localização")
            .setContentText("O aplicativo está rastreando sua localização.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun sincronizarDadosPendentes() {
        val db = AppDatabase.getDatabase(applicationContext)
        ioScope.launch {
            val dadosPendentes = db.localizacaoDao().listarTodas()
            for (entity in dadosPendentes) {
                val localizacaoData = LocalizacaoData(
                    entity.phoneID, entity.latitude, entity.longitude,
                    entity.timestamp, entity.raio, entity.altitude, entity.precisionAltitude
                )
                try {
                    val response = api.enviarLocalizacao(localizacaoData).execute()
                    if (response.isSuccessful) {
                        db.localizacaoDao().deletarPorIds(listOf(entity.id))
                    }
                } catch (e: IOException) {
                    // Continua salvo para tentar depois
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Retorna START_STICKY para que o serviço seja reiniciado se for encerrado
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        ioScope.cancel()
    }
}
