package com.example.connext

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pDevice
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
            // WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION: Comprobamos si Wi-Fi Direct está habilitado
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

            // WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION: Solicita la lista de dispositivos cercanos
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                if (ActivityCompat.checkSelfPermission(
                        context!!, android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    manager.requestPeers(channel) { peers ->
                        if (peers.deviceList.isNotEmpty()) {
                            if (activity is AddConnectionActivity) {
                                activity.updateDeviceList(peers.deviceList.toList())
                            }
                        } else {
                            Toast.makeText(
                                activity, "No se encontraron dispositivos", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            // WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION: Detecta los cambios en la conexión
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
                    WifiP2pManager.EXTRA_NETWORK_INFO
                )

                if (networkInfo?.isConnected == true) {
                    // Si estamos conectados, obtenemos información sobre la conexión
                    manager.requestConnectionInfo(channel) { info ->
                        val deviceAddress = info.groupOwnerAddress.hostAddress
                        Toast.makeText(
                            activity,
                            "Conectado a: $deviceAddress",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // Si la conexión se pierde
                    Toast.makeText(activity, "Conexión perdida", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
