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
import com.pixeleye.lteonly.databinding.ActivityMainBinding


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

        findViewById<Button>(R.id.help).setOnClickListener{
            val intent = Intent(this,HelpActivity::class.java)
            startActivity(intent)
        }

        val backgroundScope = CoroutineScope(Dispatchers.IO)
        backgroundScope.launch {
            MobileAds.initialize(this@MainActivity) {}
        }

    }


    private fun openRadioInfo() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.component = ComponentName("com.android.settings", "com.android.settings.RadioInfo")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "RadioInfo page not accessible.", Toast.LENGTH_SHORT).show()
        }
    }

}

