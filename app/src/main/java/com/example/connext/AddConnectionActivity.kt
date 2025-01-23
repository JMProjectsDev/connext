package com.example.connext

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class AddConnectionActivity : AppCompatActivity() {

    private lateinit var txt_devices_found: TextView
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: DeviceAdapter
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private val peers = mutableListOf<WifiP2pDevice>()

    private var isDiscovering = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_connection)

        // Habilitar la flecha de retorno
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Configuracion inicial
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        txt_devices_found = findViewById(R.id.id_devices_found)

        // Inicializar wifi direct
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        // Configurar el RecyclerView
        adapter = DeviceAdapter(peers) { device ->
            connectToDevice(device)
        }
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        devicesRecyclerView.adapter = adapter

        // Iniciar la busqueda de dispositivos
        startDeviceDiscovery()
    }

    // Metodo para actualizar la lista de dispositivos en el RecyclerView
    fun updateDeviceList(devices: List<WifiP2pDevice>) {
        progressBar.visibility = View.GONE
        isDiscovering = false // Resetear flag

        txt_devices_found.text = "Dispositivos encontrados: ${devices.size}"

        if (devices.isEmpty()) {
            Toast.makeText(this, "No se encontraron dispositivos cercanos", Toast.LENGTH_SHORT).show()
        } else {
            runOnUiThread {
                peers.clear()
                peers.addAll(devices)
                adapter.notifyDataSetChanged()  // Notificar al adaptador que los datos han cambiado
            }
            Log.d("AddConnectionActivity", "Dispositivos encontrados: $devices")
        }
    }


    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    private fun startDeviceDiscovery() {
        if (isDiscovering) {
            Log.d("AddConnectionActivity", "Ya hay una búsqueda en progreso.")
            return
        }

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
            Toast.makeText(
                this, "Permisos necesarios para Wi-Fi Direct", Toast.LENGTH_SHORT
            ).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        isDiscovering = true

        // Tiempo maximo de busqueda 15 segundos
        Handler(mainLooper).postDelayed({
            if (isDiscovering) {
                stopDeviceDiscovery()
                Toast.makeText(
                    this@AddConnectionActivity,
                    "No se encontraron dispositivos cercanos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, 15000)

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if (isDiscovering) { // Asegurarse de que no se ejecute si ya se detuvo
                    Toast.makeText(
                        this@AddConnectionActivity, "Búsqueda iniciada", Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(reason: Int) {
                stopDeviceDiscovery()
                val message = when (reason) {
                    WifiP2pManager.ERROR -> "Error interno en Wi-Fi Direct"
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct no es compatible en este dispositivo"
                    WifiP2pManager.BUSY -> "El framework está ocupado, inténtalo más tarde"
                    else -> "Error desconocido"
                }
                Toast.makeText(
                    this@AddConnectionActivity,
                    "Error al buscar dispositivos: $message",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun stopDeviceDiscovery() {
        progressBar.visibility = View.GONE
        isDiscovering = false
        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("AddConnectionActivity", "Búsqueda detenida exitosamente.")
            }

            override fun onFailure(reason: Int) {
                Log.e("AddConnectionActivity", "Error al detener la búsqueda: $reason")
            }
        })
    }

    private fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        this@AddConnectionActivity,
                        "Conectado a ${device.deviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(
                        this@AddConnectionActivity, "Error al conectar: $reason", Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.cancelConnect(channel, null)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
