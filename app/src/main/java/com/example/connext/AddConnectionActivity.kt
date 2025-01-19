package com.example.connext

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class AddConnectionActivity : AppCompatActivity() {

    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: DeviceAdapter
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private val peers = mutableListOf<WifiP2pDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_connection)

        // Habilitar la flecha de retorno
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Configuración inicial
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        progressBar = findViewById(R.id.progressBar)

        // Inicializar Wi-Fi Direct
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        // Configurar el RecyclerView
        adapter = DeviceAdapter(peers) { device ->
            connectToDevice(device)
        }
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        devicesRecyclerView.adapter = adapter

        // Iniciar la búsqueda de dispositivos
        startDeviceDiscovery()
    }

    // Método para actualizar la lista de dispositivos en el RecyclerView
    fun updateDeviceList(devices: List<WifiP2pDevice>) {
        runOnUiThread {
            peers.clear()
            peers.addAll(devices)
            adapter.notifyDataSetChanged()  // Notificar al adaptador que los datos han cambiado
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    private fun startDeviceDiscovery() {
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

        progressBar.visibility = View.VISIBLE
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@AddConnectionActivity, "Búsqueda iniciada", Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onFailure(reason: Int) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@AddConnectionActivity,
                    "Error al buscar dispositivos: $reason",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }

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

    override fun onDestroy() {
        super.onDestroy()
        manager.cancelConnect(channel, null)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
