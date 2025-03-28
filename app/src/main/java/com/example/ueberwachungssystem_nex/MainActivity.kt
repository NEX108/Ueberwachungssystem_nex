package com.example.ueberwachungssystem_nex

import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var isMeasuring = false
    private lateinit var sensorManager: SensorManager
    private var accelerationSensor: Sensor? = null
    private var currentAcceleration = 0.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Zugriff auf die UI-Elemente
        val button = findViewById<Button>(R.id.btnTest)
        val statusText = findViewById<TextView>(R.id.txtStatus)
        val logText = findViewById<TextView>(R.id.txtLog)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Klick-Listener für den Button
        button.setOnClickListener {
            val timestamp = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))

            if (!isMeasuring) {
                if (checkAndRequestAudioPermission()) {
                    startAudioMeasurement(logText, statusText)
                    logText.append("$timestamp: Messung gestartet\n")
                    button.text = "Stop"
                    isMeasuring = true
                } else {
                    statusText.text = "⚠️ Mikrofonberechtigung erforderlich"
                }
            } else {
                stopAudioMeasurement(statusText)
                logText.append("$timestamp: Messung gestoppt\n")
                button.text = "Start"
                isMeasuring = false
            }
        }

    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                // Gesamtbeschleunigung berechnen
                currentAcceleration = sqrt(x * x + y * y + z * z)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // brauchen wir nicht
        }
    }


    private fun checkAndRequestAudioPermission(): Boolean {
        val permission = Manifest.permission.RECORD_AUDIO
        val granted = PackageManager.PERMISSION_GRANTED

        return if (ContextCompat.checkSelfPermission(this, permission) != granted) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            // Zugriff wurde erlaubt → Jetzt Mikrofon starten
            val statusText = findViewById<TextView>(R.id.txtStatus)
            val logText = findViewById<TextView>(R.id.txtLog)

            statusText.text = "🎤 Zugriff erlaubt – Messung startet"
            startAudioMeasurement(logText, statusText)
        }
    }

    private fun startAudioMeasurement(logView: TextView, statusView: TextView) {
        try {
            val outputFile = "${externalCacheDir?.absolutePath}/test.3gp"

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile)
                prepare()
                start()
            }

            statusView.text = "🎤 Aufnahme gestartet..."

            // Starte die Lautstärke-Messung
            startMeasuringLoop(logView)

            accelerationSensor?.let {
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }

//            // Stoppe nach 10 Minuten automatisch
//            Handler(Looper.getMainLooper()).postDelayed({
//                stopAudioMeasurement(statusView)
//            }, 600000)

        } catch (e: Exception) {
            statusView.text = "❌ Fehler: ${e.message}"
            e.printStackTrace()
        }
    }

    private fun stopAudioMeasurement(statusView: TextView) {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null
            statusView.text = "🛑 Aufnahme gestoppt"

            sensorManager.unregisterListener(sensorListener)
        } catch (e: Exception) {
            statusView.text = "❌ Fehler beim Stoppen: ${e.message}"
        }
    }

    private fun startMeasuringLoop(logView: TextView) {
        val handler = Handler(Looper.getMainLooper())
        val interval = 1000L // jede Sekunde
        val maxRuntime = 10 * 60 * 1000L // 10 Minuten in Millisekunden
        val startTime = System.currentTimeMillis()

        val runnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime

                if (recorder != null && elapsed <= maxRuntime) {
                    val amp = recorder?.maxAmplitude ?: -1

                    if (amp > 0) {
                        val timestamp = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault())
                            .format(Date(currentTime))
                        val logEntry = SpannableStringBuilder()

                        logEntry.appendLine("🕓 $timestamp")

                        //Lautstärke mit Farbe
                        val ampColor = when{
                            amp < 10000 -> Color.GREEN
                            amp <= 20000 -> Color.rgb(255,165,9) //orange
                            else -> Color.RED
                        }
                        val ampText = "🎤 Lautstärke: $amp\n"
                        val ampSpan = SpannableString(ampText)
                        ampSpan.setSpan(ForegroundColorSpan(ampColor), 0, ampText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        logEntry.append(ampSpan)

                        // 📱 Beschleunigung mit Farbe
                        val accelFormatted = "%.2f".format(currentAcceleration)
                        val accColor = when {
                            currentAcceleration < 10.0 -> Color.GREEN
                            currentAcceleration <= 11.0 -> Color.rgb(255, 165, 0)
                            else -> Color.RED
                        }
                        val accText = "📱 Beschleunigung: $accelFormatted\n\n"
                        val accSpan = SpannableString(accText)
                        accSpan.setSpan(ForegroundColorSpan(accColor), 0, accText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        logEntry.append(accSpan)
                        logEntry.appendLine() //Leerzeile


                        logView.append(logEntry)
                    }

                    handler.postDelayed(this, interval)
                } else {
                    recorder = null
                    logView.append("⏹️ Aufnahme gestoppt\n")
                }
            }
        }

        handler.post(runnable)
    }



}
