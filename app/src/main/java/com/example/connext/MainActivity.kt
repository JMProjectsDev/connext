package com.example.connext

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton


class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private lateinit var fab: FloatingActionButton
    private var conversations: MutableList<String> = mutableListOf()

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private var isWifiP2pEnabled = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val LOCATION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        emptyMessage = findViewById(R.id.emptyMessage)
        fab = findViewById(R.id.fab)
        fab.imageTintList = ColorStateList.valueOf(Color.WHITE)

        // Inicializar el manager y el canal
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        // Configurar el receptor de Wi-Fi Direct
        receiver = WDBroadcastReceiver(manager, channel, this)
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        registerReceiver(receiver, intentFilter)

        // Configuración del recyclerView para mostrar mensajes
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ConversationAdapter(conversations)
        recyclerView.adapter = adapter

        // Mostrar mensaje si no hay conversaciones
        updateUI()

        // Iniciar la búsqueda al pulsar el FAB
        fab.setOnClickListener {
            if (!isWifiP2pEnabled) {
                Toast.makeText(
                    this,
                    "Por favor, activa el Wi-Fi para usar esta funcionalidad",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val intent = Intent(this, AddConnectionActivity::class.java)
            startActivity(intent)
            checkPermissionsAndDiscoverPeers()
        }
    }

    private fun updateUI() {
        if (conversations.isEmpty()) {
            emptyMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun checkPermissionsAndDiscoverPeers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

            // Verificar si hay permisos pendientes
            val hasPermissions = permissions.any { permission ->
                checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            }

            if (hasPermissions) {
                requestPermissions(permissions, PERMISSION_REQUEST_CODE)
            } else {
                if (!isLocationEnabled()) {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivityForResult(intent, LOCATION_REQUEST_CODE)
                    Toast.makeText(
                        this, "Activa la ubicación para usar Wi-Fi Direct", Toast.LENGTH_SHORT
                    ).show()
                } else {
                    startPeerDiscovery()
                }
            }
        } else {
            startPeerDiscovery()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun startPeerDiscovery() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE
            )

            Toast.makeText(
                this, "Permiso de ubicación necesario para Wi-Fi Direct", Toast.LENGTH_SHORT
            ).show()
            return
        }

        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                discoverPeersSafely()
            }

            override fun onFailure(reason: Int) {
                discoverPeersSafely() // Intentar buscar de todos modos
            }
        })
    }

    private fun discoverPeersSafely() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ), PERMISSION_REQUEST_CODE
            )
            Toast.makeText(this, "Permiso necesario para Wi-Fi Direct", Toast.LENGTH_SHORT).show()
            return
        }

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@MainActivity, "Búsqueda iniciada", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reason: Int) {
                val message = when (reason) {
                    WifiP2pManager.ERROR -> "Error interno en Wi-Fi Direct"
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct no es compatible en este dispositivo"
                    WifiP2pManager.BUSY -> "El framework está ocupado, inténtalo más tarde"
                    else -> "Error desconocido"
                }
                Log.e("WifiP2pError", "Discover peers failed. Reason: $reason")
                Toast.makeText(
                    this@MainActivity, "Error al buscar dispositivos: $message", Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isLocationEnabled()) {
                    startPeerDiscovery()
                } else {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivityForResult(intent, LOCATION_REQUEST_CODE)
                    Toast.makeText(
                        this, "Activa la ubicación para usar Wi-Fi Direct", Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Permisos de ubicación necesarios para la búsqueda de dispositivos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (isLocationEnabled()) {
                startPeerDiscovery()
            } else {
                Toast.makeText(this, "No se ha activado la ubicación", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    fun setWifiP2pEnabled(enabled: Boolean) {
        isWifiP2pEnabled = enabled
    }
}