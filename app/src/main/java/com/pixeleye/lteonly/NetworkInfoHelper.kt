package com.pixeleye.lteonly

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

object NetworkInfoHelper {

    /**
     * Finds the first valid, non-loopback IPv4 address on the device.
     */
    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "Unavailable"
                    }
                }
            }
            "Unavailable"
        } catch (e: Exception) {
            "Unavailable"
        }
    }

    /**
     * Fetches the public IP address from an external service.
     */
    suspend fun getPublicIpAddress(): String = withContext(Dispatchers.IO) {
        try {
            URL("https://api.ipify.org").readText()
        } catch (e: Exception) {
            "Unavailable"
        }
    }

    /**
     * Gets the current Wi-Fi link speed.
     */
    fun getWifiLinkSpeed(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo != null && connectionInfo.networkId != -1) {
                "${connectionInfo.linkSpeed} Mbps"
            } else {
                "Not connected"
            }
        } catch (e: Exception) {
            "Not connected"
        }
    }
}
