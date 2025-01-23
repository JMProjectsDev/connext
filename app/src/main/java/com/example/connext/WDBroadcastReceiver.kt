package com.example.connext

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class WDBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: AppCompatActivity
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            // Comprobamos si wifi direct esta habilitado
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                (activity as? MainActivity)?.setWifiP2pEnabled(isEnabled)
                Toast.makeText(
                    activity,
                    if (isEnabled) "Wi-Fi Direct activado" else "Wi-Fi Direct desactivado",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Solicita la lista de dispositivos cercanos
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                if (ActivityCompat.checkSelfPermission(
                        context!!, android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    manager.requestPeers(channel) { peers ->
                        if (peers.deviceList.isNotEmpty()) {
                            Log.d("WDBroadcastReceiver", "Dispositivos detectados: ${peers.deviceList}")
                            if (activity is AddConnectionActivity) {
                                activity.updateDeviceList(peers.deviceList.toList())
                            }
                        } else {
                            Log.d("WDBroadcastReceiver", "No se encontraron dispositivos")
                            if (activity is AddConnectionActivity) {
                                activity.updateDeviceList(emptyList()) // Llama con una lista vacía
                            }
                        }
                    }
                }
            }

            // Detecta los cambios en la conexion
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
                    WifiP2pManager.EXTRA_NETWORK_INFO
                )

                if (networkInfo?.isConnected == true) {
                    // Si estamos conectados, obtenemos informacion sobre la conexion
                    manager.requestConnectionInfo(channel) { info ->
                        val deviceAddress = info.groupOwnerAddress?.hostAddress ?: "Desconocido"
                        Toast.makeText(
                            activity,
                            "Conectado a: $deviceAddress",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else if (networkInfo != null && networkInfo.state == NetworkInfo.State.DISCONNECTED) {
                    // Solo mostramos este mensaje si estabamos conectados antes
                    Toast.makeText(activity, "Conexión perdida", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }
}
