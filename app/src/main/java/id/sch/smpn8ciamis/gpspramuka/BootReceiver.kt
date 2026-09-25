package id.sch.smpn8ciamis.gpspramuka

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "Trigger: $action")
            if (Prefs.isServiceActive(context) && Prefs.getKode(context) != null) {
                val serviceIntent = Intent(context, GpsService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Gagal: ${e.message}")
                    val pi = PendingIntent.getService(context, 1, serviceIntent,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
                    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarm.set(AlarmManager.ELAPSED_REALTIME, System.currentTimeMillis() + 5000, pi)
                }
            }
        }
    }
}
