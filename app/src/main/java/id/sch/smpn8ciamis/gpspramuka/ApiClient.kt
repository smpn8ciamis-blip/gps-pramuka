package id.sch.smpn8ciamis.gpspramuka

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun validasiRegu(kodeRegu: String): Result {
        return try {
            val body = JSONObject().apply { put("kode_regu", kodeRegu) }.toString().toRequestBody(JSON)
            val request = Request.Builder().url("${BuildConfig.SERVER_URL}/api/regu/validasi").post(body).build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorJson = try { JSONObject(responseBody) } catch (e: Exception) { null }
                    val pesan = errorJson?.optString("pesan") ?: "Gagal validasi (${response.code})"
                    return Result(false, pesan, null)
                }
                val json = JSONObject(responseBody)
                if (!json.optBoolean("ok", false)) {
                    return Result(false, json.optString("pesan", "Kode tidak valid"), null)
                }
                val regu = json.getJSONObject("regu")
                Result(true, "OK", ReguInfo(regu.getInt("id"), regu.getString("kode_regu"),
                    regu.getString("nama_regu"), regu.optString("pembina", "")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "validasi error", e)
            Result(false, "Tidak bisa terhubung: ${e.message}", null)
        }
    }

    fun kirimLokasi(kodeRegu: String, lat: Double, lng: Double, akurasi: Float, status: String): Boolean {
        return try {
            val body = JSONObject().apply {
                put("kode_regu", kodeRegu); put("latitude", lat)
                put("longitude", lng); put("akurasi", akurasi.toDouble()); put("status", status)
            }.toString().toRequestBody(JSON)
            val request = Request.Builder().url("${BuildConfig.SERVER_URL}/api/regu/kirim-lokasi").post(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { Log.e(TAG, "kirimLokasi error", e); false }
    }

    fun ambilBroadcast(kodeRegu: String, sinceId: Long): BroadcastResult {
        return try {
            val url = "${BuildConfig.SERVER_URL}/api/regu/broadcast?kode_regu=$kodeRegu&since=$sinceId"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return BroadcastResult(false, emptyList())
                val json = JSONObject(response.body?.string() ?: "")
                if (!json.optBoolean("ok", false)) return BroadcastResult(false, emptyList())
                val arr = json.optJSONArray("broadcasts") ?: return BroadcastResult(true, emptyList())
                val list = mutableListOf<BroadcastInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(BroadcastInfo(o.getLong("id"), o.getString("pesan"),
                        o.optString("prioritas", "NORMAL"), o.optString("waktu", "")))
                }
                BroadcastResult(true, list)
            }
        } catch (e: Exception) { Log.e(TAG, "ambilBroadcast error", e); BroadcastResult(false, emptyList()) }
    }

    fun kirimChat(kodeRegu: String, pesan: String): Boolean {
        return try {
            val body = JSONObject().apply { put("kode_regu", kodeRegu); put("pesan", pesan) }.toString().toRequestBody(JSON)
            val request = Request.Builder().url("${BuildConfig.SERVER_URL}/api/regu/chat/kirim").post(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { Log.e(TAG, "kirimChat error", e); false }
    }

    fun ambilChat(kodeRegu: String, sinceId: Long): ChatResult {
        return try {
            val url = "${BuildConfig.SERVER_URL}/api/regu/chat/terima?kode_regu=$kodeRegu&since=$sinceId"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ChatResult(false, emptyList())
                val json = JSONObject(response.body?.string() ?: "")
                if (!json.optBoolean("ok", false)) return ChatResult(false, emptyList())
                val arr = json.optJSONArray("chats") ?: return ChatResult(true, emptyList())
                val list = mutableListOf<ChatInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(ChatInfo(o.getLong("id"), o.getString("pesan"),
                        o.optString("dari", "admin"), o.optString("waktu", "")))
                }
                ChatResult(true, list)
            }
        } catch (e: Exception) { Log.e(TAG, "ambilChat error", e); ChatResult(false, emptyList()) }
    }

    fun verifyExitCode(kodeRegu: String, exitCode: String): ExitCodeResult {
        return try {
            val body = JSONObject().apply { put("kode_regu", kodeRegu); put("exit_code", exitCode) }.toString().toRequestBody(JSON)
            val request = Request.Builder().url("${BuildConfig.SERVER_URL}/api/regu/verify-exit-code").post(body).build()
            client.newCall(request).execute().use { response ->
                val json = try { JSONObject(response.body?.string() ?: "") } catch (e: Exception) { null }
                if (response.isSuccessful && json?.optBoolean("ok", false) == true) {
                    ExitCodeResult(true, json.optString("pesan", "OK"))
                } else {
                    ExitCodeResult(false, json?.optString("pesan", "Kode salah") ?: "Kode salah")
                }
            }
        } catch (e: Exception) { Log.e(TAG, "verifyExitCode error", e); ExitCodeResult(false, "Error: ${e.message}") }
    }

    data class Result(val ok: Boolean, val pesan: String, val regu: ReguInfo?)
    data class ReguInfo(val id: Int, val kode: String, val nama: String, val pembina: String = "")
    data class BroadcastInfo(val id: Long, val pesan: String, val prioritas: String, val waktu: String)
    data class BroadcastResult(val ok: Boolean, val broadcasts: List<BroadcastInfo>)
    data class ChatInfo(val id: Long, val pesan: String, val dari: String, val waktu: String)
    data class ChatResult(val ok: Boolean, val chats: List<ChatInfo>)
    data class ExitCodeResult(val ok: Boolean, val pesan: String)
}
