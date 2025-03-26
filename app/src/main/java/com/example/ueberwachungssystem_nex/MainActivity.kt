package com.example.ueberwachungssystem_nex

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Zugriff auf die UI-Elemente
        val button = findViewById<Button>(R.id.btnTest)
        val statusText = findViewById<TextView>(R.id.txtStatus)
        val logText = findViewById<TextView>(R.id.txtLog)

        // Klick-Listener für den Button
        button.setOnClickListener {
            // 1. Aktuellen Zeitstempel holen
            val currentTime = System.currentTimeMillis()
            val timestamp = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault()).format(
                Date(currentTime)
            )

            // 2. Statusanzeige aktualisieren
            statusText.text = "Letzter Klick: $timestamp"

            // 3. Neuen Log-Eintrag hinzufügen
            logText.append("[$timestamp] Button gedrückt\n")
        }
    }
}
