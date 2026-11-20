package com.mario.sentieri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class TrackingService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private var lastValidLocation: Location? = null

    companion object {
        var isRunning = false
        var onLocationUpdated: ((Location) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        startForeground(1, createNotification())
        isRunning = true
        requestLocation()
    }

    private fun createNotification(): Notification {
        val channelId = "tracking_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Tracciamento Escursione", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sentieri - Tracciamento attivo")
            .setContentText("Registrazione della posizione in corso...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun requestLocation() {
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 3f, this)
            }
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 30f) return
        lastValidLocation?.let { prev ->
            val distance = prev.distanceTo(location)
            if (distance > 500f) return
        }
        lastValidLocation = location
        onLocationUpdated?.invoke(location)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
