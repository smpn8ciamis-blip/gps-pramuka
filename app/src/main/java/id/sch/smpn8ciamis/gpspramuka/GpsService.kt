package id.sch.smpn8ciamis.gpspramuka

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject

class GpsService : Service() {

    companion object {
        private const val TAG = "GpsService"
        private const val CHANNEL_ID = "gps_tracker_channel"
        private const val CHANNEL_BROADCAST_ID = "broadcast_channel"
        private const val NOTIF_ID = 1001
        private const val NOTIF_BROADCAST_ID = 2001
        const val ACTION_STOP = "id.sch.smpn8ciamis.gpspramuka.STOP"
        const val ACTION_SOS = "id.sch.smpn8ciamis.gpspramuka.SOS"
        private const val KIRIM_INTERVAL_MS = 15_000L
        private const val MIN_JARAK_METER = 10f
        private const val NOTIF_UPDATE_INTERVAL_MS = 30_000L
        private const val BROADCAST_POLL_INTERVAL_MS = 30_000L
    }

    private lateinit var locationManager: LocationManager
    private var lastSentTime = 0L
    private var lastNotifUpdate = 0L
    private var lastUpdate = ""

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var broadcastJob: Job? = null

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

            // Throttle update notifikasi
            if (now - lastNotifUpdate >= NOTIF_UPDATE_INTERVAL_MS) {
                lastNotifUpdate = now
                updateNotification(location)
            }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    }

    private fun kirimKeServer(location: Location) {
        val kode = Prefs.getKode(this) ?: run {
            Log.w(TAG, "kirimKeServer: kode regu null, skip")
            return
        }
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
        createNotificationChannels()
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

        val notification = buildNotification("Memulai GPS...", "Menunggu sinyal")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        startTracking()
        startBroadcastPolling()
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
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) { null }
            if (lastKnown != null) locationListener.onLocationChanged(lastKnown)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security: ${e.message}")
            stopSelf()
        }
    }

    // ===== BROADCAST POLLING (FITUR 2) =====
    private fun startBroadcastPolling() {
        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            while (isActive) {
                try {
                    cekBroadcast()
                } catch (e: Exception) {
                    Log.e(TAG, "Broadcast polling error: ${e.message}")
                }
                delay(BROADCAST_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun cekBroadcast() {
        val kode = Prefs.getKode(this) ?: return
        val lastId = Prefs.getLastBroadcastId(this)

        val result = ApiClient.ambilBroadcast(kode, lastId)
        if (!result.ok || result.broadcasts.isEmpty()) return

        result.broadcasts.forEach { bc ->
            tampilkanBroadcast(bc)
            Prefs.setLastBroadcastId(this, bc.id)
        }
    }

    private fun tampilkanBroadcast(bc: ApiClient.BroadcastInfo) {
        val isDarurat = bc.prioritas == "DARURAT"
        val judul = if (isDarurat) "🚨 BROADCAST DARURAT" else "📢 Broadcast dari Pos Utama"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, bc.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_BROADCAST_ID)
            .setContentTitle(judul)
            .setContentText(bc.pesan)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bc.pesan))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (isDarurat) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_BROADCAST_ID + bc.id.toInt(), notif)

        Log.d(TAG, "Broadcast diterima: ${bc.pesan}")
    }

    private fun kirimSos() {
        val kode = Prefs.getKode(this) ?: run {
            Log.e(TAG, "SOS gagal: kode regu null")
            return
        }

        val lastKnown = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            Log.e(TAG, "SOS security: ${e.message}")
            null
        }

        scope.launch {
            if (lastKnown == null) {
                Log.w(TAG, "SOS tanpa koordinat (GPS belum lock)")
                ApiClient.kirimLokasi(kode, 0.0, 0.0, 0f, "DARURAT_SOS")
            } else {
                ApiClient.kirimLokasi(
                    kode, lastKnown.latitude, lastKnown.longitude,
                    lastKnown.accuracy, "DARURAT_SOS"
                )
            }
        }
    }

    private fun stopTracking() {
        try { locationManager.removeUpdates(locationListener) } catch (_: Exception) {}
        Prefs.setServiceActive(this, false)
        broadcastJob?.cancel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val channel1 = NotificationChannel(
                CHANNEL_ID, "Pelacak GPS Pramuka", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel1)

            val channel2 = NotificationChannel(
                CHANNEL_BROADCAST_ID, "Broadcast dari Pos Utama", NotificationManager.IMPORTANCE_HIGH
            ).apply { setShowBadge(true) }
            nm.createNotificationChannel(channel2)
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
            buildNotification("Regu $nama", "Akurasi ${akurasi}m • $lastUpdate")
        )
    }
}
