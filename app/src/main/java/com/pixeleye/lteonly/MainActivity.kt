package com.pixeleye.lteonly

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.gms.ads.MobileAds
import java.io.DataOutputStream


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        var btn = findViewById<Button>(R.id.networkinfo)
        btn.setOnClickListener {
            openRadioInfo()
        }
        var bandLock = findViewById<Button>(R.id.bandLock)
        bandLock.setOnClickListener {
            openBandModeSimSelectWithRoot()
        }

        findViewById<Button>(R.id.help).setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
        }

        val backgroundScope = CoroutineScope(Dispatchers.IO)
        backgroundScope.launch {
            MobileAds.initialize(this@MainActivity) {}
        }

    }


    private fun openRadioInfo() {
        val intent = Intent("android.intent.action.MAIN").apply {
            component = ComponentName("com.android.settings", "com.android.settings.RadioInfo")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Add this flag to ensure it's started as a new task
        }
        val intentNew = Intent("android.intent.action.MAIN").apply {
            component = ComponentName("com.android.phone", "com.android.phone.settings.RadioInfo")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Add this flag to ensure it's started as a new task
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(intentNew)
            } catch (e: Exception) {
                Toast.makeText(this, "RadioInfo page not accessible.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openBandModeSimSelectWithRoot() {
        try {
            val command =
                "am start -n com.mediatek.engineermode/com.mediatek.engineermode.bandselect.BandModeSimSelect\n"

            // Execute the command as root
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes(command)
            os.flush()
            os.close()

            Toast.makeText(this, "Attempting to open Band Selection...", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open Band Selection. This feature requires a rooted MediaTek device.", Toast.LENGTH_LONG).show()
        }
    }

}

