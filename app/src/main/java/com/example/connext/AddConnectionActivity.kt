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
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@AddConnectionActivity, "Búsqueda iniciada", Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onFailure(reason: Int) {
                progressBar.visibility = View.GONE
                val message = when (reason) {
                    WifiP2pManager.ERROR -> "Error interno en Wi-Fi Direct"
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct no es compatible en este dispositivo"
                    WifiP2pManager.BUSY -> "El framework está ocupado, inténtalo más tarde"
                    else -> "Error desconocido"
                }
                Log.e("WifiP2pError", "Discover peers failed. Reason: $reason")
                Toast.makeText(
                    this@AddConnectionActivity,
                    "Error al buscar dispositivos: $message",
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
