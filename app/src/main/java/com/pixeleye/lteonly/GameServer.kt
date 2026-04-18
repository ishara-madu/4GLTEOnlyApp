package com.pixeleye.lteonly

import androidx.compose.runtime.*

class GameServer(val name: String, val ip: String) {
    var pingMs by mutableIntStateOf(-1)
    var status by mutableStateOf("Waiting...")
    val pingHistory = mutableStateListOf<Int>()
}
