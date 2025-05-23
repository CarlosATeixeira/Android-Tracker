package dev.carlosalberto.locationtrackerapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import dev.carlosalberto.locationtrackerapp.api.LocalizacaoData
import dev.carlosalberto.locationtrackerapp.api.RetrofitClient
import dev.carlosalberto.locationtrackerapp.database.AppDatabase
import dev.carlosalberto.locationtrackerapp.database.LocalizacaoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.util.Log
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkCapabilities
import android.app.AlertDialog
import android.app.Service.START_STICKY
import android.os.Build
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val lat = it.getDoubleExtra("latitude",  0.0)
                val lon = it.getDoubleExtra("longitude", 0.0)
                val acc = it.getDoubleExtra("precisao",  0.0)
                Log.d("UIReceiver", "← received broadcast: lat=$lat, lon=$lon, acc=$acc")
                statusText.text = "Lat: $lat\nLon: $lon\nAcc: $acc m"
            }
        }

    }

    companion object {
        private const val ACTION_LOCATION_UPDATE = "dev.carlosalberto.LOCATION_UPDATE"
        private const val REQUEST_LOCATION_PERMS = 1001
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val UPDATE_INTERVAL: Long = 5000
    private val FASTEST_INTERVAL: Long = 5000
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.textStatus)
        solicitarPermissoesSeNecessario()
    }

    private fun solicitarPermissoesSeNecessario() {
        if (checkAndRequestPermissions()) {
            // Todas permissões OK, segue fluxo normalmente!
            startLocationService()
            requestIgnoreBatteryOptimizations()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_LOCATION_UPDATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // FLAG dizendo que é um receiver *não* exportado para outras apps
            registerReceiver(uiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // em Android <13 funciona sem flags
            registerReceiver(uiReceiver, filter)
        }
    }


    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            val pkg = packageName
            if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$pkg")
                }.also { startActivity(it) }
            }
        }
    }

    private fun verificarIgnorarOtimizacoes() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Permissão Necessária")
                .setMessage("Para que o rastreamento funcione corretamente em segundo plano, precisamos de sua permissão para ignorar as otimizações de bateria. Isso garante que o aplicativo continue enviando a localização mesmo quando a tela estiver desligada.")
                .setPositiveButton("Permitir") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    fun enviarOuArmazenarLocalizacao(phoneID: String, latitude: Double, longitude: Double, raio: Float, altitude: Double, precisionAltitude: Double) {
        val timestamp = System.currentTimeMillis()
        val localizacaoData = LocalizacaoData(phoneID, latitude, longitude, timestamp, raio, altitude, precisionAltitude)

        if (verificarConexao()) {
            // Tenta enviar os dados usando Retrofit
            RetrofitClient.instance.enviarLocalizacao(localizacaoData).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        Log.d("Localizacao", "Localização enviada com sucesso: $latitude, $longitude")
                        // Se houver dados pendentes, sincroniza-os
                        sincronizarDadosPendentes()
                    } else {
                        Log.e("Localizacao", "Falha ao enviar localização (Resposta inválida): ${response.code()}")
                        salvarLocalizacaoLocalmente(localizacaoData)
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e("Localizacao", "Falha ao enviar localização (Erro): ${t.message}")
                    salvarLocalizacaoLocalmente(localizacaoData)
                }
            })
        } else {
            Log.w("Localizacao", "Sem conexão com a Internet. Salvando localmente.")
            salvarLocalizacaoLocalmente(localizacaoData)
        }
    }

    private fun verificarConexao(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun salvarLocalizacaoLocalmente(localizacaoData: LocalizacaoData) {
        val db = AppDatabase.getDatabase(this)
        val entity = LocalizacaoEntity(
            phoneID = localizacaoData.phoneID,
            latitude = localizacaoData.latitude,
            longitude = localizacaoData.longitude,
            timestamp = localizacaoData.timestamp,
            raio = localizacaoData.raio,
            altitude = localizacaoData.altitude,
            precisionAltitude = localizacaoData.precisionAltitude
        )
        CoroutineScope(Dispatchers.IO).launch {
            db.localizacaoDao().inserir(entity)
        }
    }

    private fun sincronizarDadosPendentes() {
        val db = AppDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.IO).launch {
            val dadosPendentes = db.localizacaoDao().listarTodas()
            for (entity in dadosPendentes) {
                val localizacaoData = LocalizacaoData(entity.phoneID, entity.latitude, entity.longitude, entity.timestamp, entity.raio, entity.altitude, entity.precisionAltitude)
                RetrofitClient.instance.enviarLocalizacao(localizacaoData).enqueue(object : Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                        if (response.isSuccessful) {
                            CoroutineScope(Dispatchers.IO).launch {
                                db.localizacaoDao().deletarPorIds(listOf(entity.id))
                            }
                        }
                    }

                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        // Se falhar, a sincronização será tentada novamente
                    }
                })
            }
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            perms += Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }
        return if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_LOCATION_PERMS)
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMS) {
            // Sempre revalida permissões e reexecuta fluxo
            solicitarPermissoesSeNecessario()
            // Se alguma não foi concedida, seu método já exibe o Toast de aviso
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {

            // Em Android 10 (Q) o requestPermissions ainda abre o diálogo. Em 11+ exige Settings.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    REQUEST_LOCATION_PERMS
                )
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Permissão em Segundo Plano")
                    .setMessage("Para rastrear sempre, precisamos que você conceda a localização em segundo plano. Abra as configurações do app?")
                    .setPositiveButton("Abrir Configurações") { _, _ ->
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }.also { startActivity(it) }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun startLocationService() {
        Intent(this, LocationService::class.java).also {
            ContextCompat.startForegroundService(this, it)
        }
    }

    override fun onStop() {
        unregisterReceiver(uiReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
