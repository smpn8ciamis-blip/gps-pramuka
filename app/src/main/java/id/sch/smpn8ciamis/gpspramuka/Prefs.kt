package id.sch.smpn8ciamis.gpspramuka

import android.content.Context

object Prefs {
    private const val NAME = "gps_pramuka_prefs"
    private const val KEY_ID = "id_regu"
    private const val KEY_KODE = "kode_regu"
    private const val KEY_NAMA = "nama_regu"
    private const val KEY_ACTIVE = "service_active"

    fun simpanRegu(ctx: Context, id: Int, kode: String, nama: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_ID, id)
            .putString(KEY_KODE, kode)
            .putString(KEY_NAMA, nama)
            .apply()
    }

    fun getId(ctx: Context): Int =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_ID, 0)

    fun getKode(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_KODE, null)

    fun getNama(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_NAMA, null)

    fun setServiceActive(ctx: Context, active: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, active).apply()
    }

    fun isServiceActive(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun hapus(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
