package com.pixeleye.lteonly

data class NetworkInfo(
    val carrierName: String = "Unknown",
    val operatorCode: String = "--",
    val networkType: String = "--",
    val isConnected: Boolean = false,
    val signalStrength: SignalInfo = SignalInfo(),
    val speedInfo: SpeedInfo = SpeedInfo(),
    val isLocationEnabled: Boolean = true,
    val isRoaming: Boolean = false,
    val simOperator: String = "--"
)

data class SignalInfo(
    val rsrp: Int = 0,
    val rsrq: Int = 0,
    val rssi: Int = 0,
    val sinr: Int = 0,
    val level: SignalLevel = SignalLevel.UNKNOWN,
    val history: List<Int> = emptyList()
)

data class SpeedInfo(
    val downloadSpeed: Double = 0.0,
    val uploadSpeed: Double = 0.0,
    val totalDownloaded: Long = 0,
    val totalUploaded: Long = 0,
    val latency: Int = 0,
    val level: SpeedLevel = SpeedLevel.UNKNOWN
)

enum class SignalLevel {
    EXCELLENT, GOOD, FAIR, POOR, NO_SIGNAL, UNKNOWN
}

enum class SpeedLevel {
    EXCELLENT, GOOD, FAIR, POOR, NO_SIGNAL, UNKNOWN
}
