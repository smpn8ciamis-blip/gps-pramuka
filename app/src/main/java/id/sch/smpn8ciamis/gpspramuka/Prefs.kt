package id.sch.smpn8ciamis.gpspramuka

import android.content.Context

object Prefs {
    private const val NAME = "gps_pramuka_prefs"
    private const val KEY_ID = "id_regu"
    private const val KEY_KODE = "kode_regu"
    private const val KEY_NAMA = "nama_regu"
    private const val KEY_ACTIVE = "service_active"
    private const val KEY_LAST_BROADCAST = "last_broadcast_id"
    private const val KEY_ALL_PERMS_GRANTED = "all_perms_granted"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun simpanRegu(ctx: Context, id: Int, kode: String, nama: String) {
        sp(ctx).edit()
            .putInt(KEY_ID, id)
            .putString(KEY_KODE, kode)
            .putString(KEY_NAMA, nama)
            .apply()
    }

    fun getId(ctx: Context): Int = sp(ctx).getInt(KEY_ID, 0)

    fun getKode(ctx: Context): String? = sp(ctx).getString(KEY_KODE, null)

    fun getNama(ctx: Context): String? = sp(ctx).getString(KEY_NAMA, null)

    fun setServiceActive(ctx: Context, active: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ACTIVE, active).apply()
    }

    fun isServiceActive(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ACTIVE, false)

    // ===== BROADCAST =====
    fun getLastBroadcastId(ctx: Context): Long = sp(ctx).getLong(KEY_LAST_BROADCAST, 0L)

    fun setLastBroadcastId(ctx: Context, id: Long) {
        sp(ctx).edit().putLong(KEY_LAST_BROADCAST, id).apply()
    }

    // ===== PERMISSIONS =====
    fun isAllPermsGranted(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ALL_PERMS_GRANTED, false)

    fun setAllPermsGranted(ctx: Context, granted: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ALL_PERMS_GRANTED, granted).apply()
    }

    fun hapus(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }
}
