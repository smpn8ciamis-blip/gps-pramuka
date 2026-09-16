package id.sch.smpn8ciamis.gpspramuka

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class GpsService : Service() {

    companion object {
        private const val TAG = "GpsService"
        private const val CHANNEL_ID = "gps_tracker_channel"
        private const val NOTIF_ID = 1001
        const val ACTION_STOP = "id.sch.smpn8ciamis.gpspramuka.STOP"
        const val ACTION_SOS = "id.sch.smpn8ciamis.gpspramuka.SOS"
        private const val KIRIM_INTERVAL_MS = 5000L
        private const val MIN_JARAK_METER = 3f
    }

    private lateinit var locationManager: LocationManager
    private var lastSentTime = 0L
    private var lastUpdate = ""

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = System.currentTimeMillis()
            lastUpdate = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(now))

            Log.d(TAG, "GPS: ${location.latitude}, ${location.longitude}")

            if (now - lastSentTime >= KIRIM_INTERVAL_MS - 500) {
                kirimKeServer(location)
                lastSentTime = now
            }
            updateNotification(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    }

    private fun kirimKeServer(location: Location) {
        val kode = Prefs.getKode(this) ?: return
        scope.launch {
            ApiClient.kirimLokasi(
                kodeRegu = kode,
                lat = location.latitude,
                lng = location.longitude,
                akurasi = location.accuracy,
                status = "NORMAL"
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        Prefs.setServiceActive(this, true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SOS -> {
                kirimSos()
                return START_STICKY
            }
        }
        startForeground(NOTIF_ID, buildNotification("Memulai GPS...", "Menunggu sinyal"))
        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                KIRIM_INTERVAL_MS,
                MIN_JARAK_METER,
                locationListener
            )
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000L, 10f, locationListener
                )
            }
            val lastKnown = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (e: SecurityException) { null }
            if (lastKnown != null) locationListener.onLocationChanged(lastKnown)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security: ${e.message}")
            stopSelf()
        }
    }

    private fun kirimSos() {
        try {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: return
            val kode = Prefs.getKode(this) ?: return
            scope.launch {
                ApiClient.kirimLokasi(
                    kodeRegu = kode,
                    lat = lastKnown.latitude,
                    lng = lastKnown.longitude,
                    akurasi = lastKnown.accuracy,
                    status = "DARURAT_SOS"
                )
            }
        } catch (_: Exception) {}
    }

    private fun stopTracking() {
        try { locationManager.removeUpdates(locationListener) } catch (_: Exception) {}
        Prefs.setServiceActive(this, false)
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pelacak GPS Pramuka", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, GpsService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(location: Location) {
        val nama = Prefs.getNama(this) ?: "-"
        val akurasi = location.accuracy.toInt()
        getSystemService(NotificationManager::class.java).notify(
            NOTIF_ID,
            buildNotification("Regu $nama", "Lokasi terkirim • akurasi ${akurasi}m • $lastUpdate")
        )
    }
}
