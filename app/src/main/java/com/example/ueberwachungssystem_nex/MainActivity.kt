package com.example.ueberwachungssystem_nex

import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

    private var recorder: MediaRecorder? = null         // Audioaufnahme und Messen der Lautstärke
    private var isMeasuring = false                     // Messung aktiv oder nicht
    private lateinit var sensorManager: SensorManager   // Zugriffspunkt alle Sensoren
    private var accelerationSensor: Sensor? = null      // Beschleunigungssensor
    private var currentAcceleration = 0.0f              // Aktueller Beschleunigungswert

    //UI Elemente, die später deklariert werden
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var button: Button

    // Was passiert beim Start der App
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)              // Startlogik von Android
        setContentView(R.layout.activity_main)          // Lädt das Layout aus der activity_main.xml

        // Zugriff auf die UI-Elemente
        button = findViewById<Button>(R.id.btnTest)             // Deklarierung des Buttons (Start/Stopp)
        statusText = findViewById<TextView>(R.id.txtStatus)     // Deklarierung des Textfeldes (aktueller Status: "läuft", "gestoppt", "Fehler")
        logText = findViewById<TextView>(R.id.txtLog)           // Deklarierung des LogFeldes (Messprotokoll)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager       //Initialisieren des SensorManagers, um Sensoren verwenden zu können
        accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)  // Referenz zum eingebauten Beschleunigungssensor

        // Ausführung beim Drücken des Buttons
        button.setOnClickListener {
            //Starte Messung, falls keine Messung läuft
            if (!isMeasuring) {
                //Falls Erlaubnis vorhanden, Starte Messung
                if (checkAndRequestAudioPermission()) {
                    startMeasurement()
                } else {
                    statusText.text = "⚠️ Mikrofonberechtigung erforderlich"
                }
            }
            //Stoppen der Messung, falls bereits eine Messung läuft
            else {
                stopMeasurement(statusText, button, logText)
            }
        }
    }

    // Abfrage, ob Erläubnis für Audio da ist. Falls nicht, wird um Erlaubnis gebeten.
    private fun checkAndRequestAudioPermission(): Boolean {
        val permission = Manifest.permission.RECORD_AUDIO   // Der Berechtigungs-Name, den wir anfragen wollen: Mikrofon-Zugriff
        val granted = PackageManager.PERMISSION_GRANTED     // konstanter Wert, den Android zurückgibt, wenn die Erlaubnis bereits erteilt wurde

        // Wenn die Berechtigung noch nicht vorhanden ist...
        return if (ContextCompat.checkSelfPermission(this, permission) != granted) {
            // ...frage Android, ob der Nutzer sie erlauben möchte (Systemdialog erscheint)
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
            // Rückgabe: false → wir haben NOCH keine Erlaubnis (warten bis zur Eingabe des Nutzers)
            false
        }
        // Wenn die Erlaubnis schon vorhanden ist → true zurückgeben
        else {
            true
        }
    }

    // Wird abgerufen, nachdem der Nutzer die Eingabe für Erlaubnis gegeben hat. Egal ob abgelehnt oder zugestimmt
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // Wichtig bei Erweiterung, aktuell noch nicht großartig notwendig

        // Zugriff wurde für alle 3 erlaubt erlaubt → Jetzt Mikrofon starten (Mikrofon, Hat der Nutzer auf die Erlaubnis Reagiert?, Erlaubnis gegeben?)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            // Aktualisierung der Statusanzeige
            statusText.text = "🎤 Zugriff erlaubt – Messung startet"

            //Starten der Messung
            startMeasurement()
        }
    }

    // Starten der Messung (Audioaufnahme + Bewegungserkennung + Anzeige)
    private fun startMeasurement(){
        // try-Block, um Fehler beim Zugriff auf Hardware abzufangen
        try {
            // wird so angezeigt, als ob es gespeichert wird (wird von Android verlangt), aber wird nicht wirklich gespeichert
            val outputFile = "${externalCacheDir?.absolutePath}/test.3gp"

            // Erstellen eines MediaRecorder-Objekts (.apply, um Einstellungen vorzunehmen)
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)           // Mikrofon als Quelle
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)   // Container Format (ähnlich MP4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)      // Standard Audio Codec
                setOutputFile(outputFile)                               // Zieldatei
                prepare()                                               // Bereitet die Aufnahme vor
                start()                                                 // Startet die Aufnahme
            }

            // Aktualisierung der Statusanzeige
            statusText.text = "🎤 Aufnahme gestartet..."

            // Starten der Messschleife
            startMeasuringLoop(logText)

            // Aktivierung des Beschleunigungssensors
            accelerationSensor?.let {
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }

            // Erstellt und loggt den Start-Zeitstempel für das Protokoll
            val timestamp = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))
            logText.append("🟢 $timestamp\nMessung gestartet\n\n")

            // Button wird Stop angezeigt und gibt an, dass gerade eine Messung läuft
            button.text = "Stop"
            isMeasuring = true

        }
        // Anzeige einer Fehlermeldung, falls etwas nicht funktioniert
        catch (e: Exception) {
            statusText.text = "❌ Fehler: ${e.message}"
            e.printStackTrace()
        }
    }

    // Aktivierung bei Stopp
    private fun stopMeasurement(statusView: TextView, button: Button, logView: TextView) {
        // try-Block, um Fehler beim Zugriff auf Hardware abzufangen
        try {
            recorder?.apply {
                stop()      // Beenden der Aufnahme
                reset()     // Zurücksetzen des Recorders
                release()   // Freigabe der Ressourcen
            }
            recorder = null // kein Recorder mehr aktiv

            // Deaktivieren des Beschleunigungssensors
            sensorManager.unregisterListener(sensorListener)

            // Zeitstempel beim Stoppen
            val timestamp = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))
            logView.append("🔴 $timestamp\nMessung gestoppt\n\n")

            //Aktualisierung der Variablen
            statusView.text = "🛑 Aufnahme gestoppt"
            button.text = "Start"
            isMeasuring = false
        }
        // Anzeige einer Fehlermeldung, falls etwas nicht funktioniert
        catch (e: Exception) {
            statusView.text = "❌ Fehler beim Stoppen: ${e.message}"
        }
    }

//    private fun startAudioMeasurement(logView: TextView, statusView: TextView) {
//        try {
//            val outputFile = "${externalCacheDir?.absolutePath}/test.3gp"
//
//            recorder = MediaRecorder().apply {
//                setAudioSource(MediaRecorder.AudioSource.MIC)
//                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
//                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
//                setOutputFile(outputFile)
//                prepare()
//                start()
//            }
//
//            statusView.text = "🎤 Aufnahme gestartet..."
//
//            // Starte die Lautstärke-Messung
//            startMeasuringLoop(logView)
//
//            accelerationSensor?.let {
//                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
//            }
//
//
//        } catch (e: Exception) {
//            statusView.text = "❌ Fehler: ${e.message}"
//            e.printStackTrace()
//        }
//    }

//    private fun stopAudioMeasurement(statusView: TextView) {
//        try {
//            recorder?.apply {
//                stop()
//                reset()
//                release()
//            }
//            recorder = null
//            statusView.text = "🛑 Aufnahme gestoppt"
//
//            sensorManager.unregisterListener(sensorListener)
//        } catch (e: Exception) {
//            statusView.text = "❌ Fehler beim Stoppen: ${e.message}"
//        }
//    }

    //Berechnung der Beschleunigungswerte
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            // Sensorevent, der die Messwerte der Beschleunigung in X-, Y-, und Z-Richtung enthält
            event?.let {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                // Betragsbeschleunigung berechnen
                currentAcceleration = sqrt(x * x + y * y + z * z)
            }
        }

        // Wird nur aufgerufen, wenn Android merkt, dass der sSensor neu kalibiert werden müsste
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }

    // Loop während der Messung
    private fun startMeasuringLoop(logView: TextView) {
        val handler = Handler(Looper.getMainLooper())

        // Schleifeneinstellungen
        val interval = 1000L // jede Sekunde
        val maxRuntime = 10 * 60 * 1000L // 10 Minuten in Millisekunden
        val startTime = System.currentTimeMillis()  // Zeit bei Start der Messung

        // eigentliche Schleife
        val runnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()    // Aktuelle Zeit
                val elapsed = currentTime - startTime           // Zur Messung der Vergangenen Zeit seit Start der Messung

                // Soll Messen, solange die maximale Zeit noch nicht erreicht wurde
                if (recorder != null && elapsed <= maxRuntime) {

                    // maximaler Amplitude der Messung
                    val amp = recorder?.maxAmplitude ?: -1

                    // Nur Sinnvolle Werte werden gemessen
                    if (amp > 0) {
                        // Spannable Stringbuilder zum Hinzufügen von neuen Strings
                        val logEntry = SpannableStringBuilder()

                        // Zeitstempel
                        val timestamp = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault())
                            .format(Date(currentTime))

                        // Erzeuge neuen Eintrag für das Log mit Zeitstempel (erste Zeile)
                        logEntry.appendLine("🕓 $timestamp")

                        //Lautstärke mit Farbe
                        val ampColor = when{
                            amp < 10000 -> Color.GREEN
                            amp <= 20000 -> Color.rgb(255,165,9) //orange
                            else -> Color.RED }
                        val ampText = "🎤 Lautstärke: $amp\n"
                        val ampSpan = SpannableString(ampText)

                        //??
                        ampSpan.setSpan(ForegroundColorSpan(ampColor), 0, ampText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                        // Einfügen des Lautstärlentexts in den String für den Log
                        logEntry.append(ampSpan)

                        // Beschleunigung mit Farbe
                        val accelFormatted = "%.2f".format(currentAcceleration)
                        val accColor = when {
                            currentAcceleration < 10.0 -> Color.GREEN
                            currentAcceleration <= 11.0 -> Color.rgb(255, 165, 0) //Orange
                            else -> Color.RED
                        }
                        val accText = "📱 Beschleunigung: $accelFormatted\n\n"
                        val accSpan = SpannableString(accText)
                        accSpan.setSpan(ForegroundColorSpan(accColor), 0, accText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        logEntry.append(accSpan)
                        // Einfügen des Beschleunigungswerts in den String für den Log
                        logEntry.appendLine() //Leerzeile

                        // Spannable in den Log einfügen
                        logView.append(logEntry)
                    }

                    // Runnable ruft sich selber nach dem Intervall auf
                    handler.postDelayed(this, interval)
                }
                // Falls keine Aufzeichnung mehr läuft oder Zeit abgelaufen ist, wird die Schleife nicht erneut gestartet
                else {
                    recorder = null
                    logView.append("⏹️ Aufnahme gestoppt\n")
                }
            }
        }
        handler.post(runnable)
    }
}
