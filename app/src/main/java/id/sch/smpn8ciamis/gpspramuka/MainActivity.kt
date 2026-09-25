package id.sch.smpn8ciamis.gpspramuka

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.sch.smpn8ciamis.gpspramuka.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingRegu: ApiClient.ReguInfo? = null
    private var kodePending: String? = null
    private var exitDialogShown = false

    // ===== IZIN LOKASI =====
    private val mintaIzinLokasi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasil ->
        val fineOk = hasil[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineOk) {
            cekDanMintaIzinBackground()
            // Lanjutkan validasi jika ada kode pending
            kodePending?.let { validasiKeServer(it) }
        } else {
            toast("Izin lokasi wajib untuk tracking.")
        }
    }

    // ===== IZIN NOTIFIKASI =====
    private val mintaIzinNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* tidak masalah kalau ditolak */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val kodeTersimpan = Prefs.getKode(this)
        val namaTersimpan = Prefs.getNama(this)

        if (kodeTersimpan != null && namaTersimpan != null) {
            tampilkanTracker(namaTersimpan)
            mulaiService()
        } else {
            tampilkanForm()
        }

        binding.btnMasuk.setOnClickListener { prosesDaftar() }
        binding.btnKonfirmasi.setOnClickListener { konfirmasiMulai() }
        binding.btnBatal.setOnClickListener { batalKonfirmasi() }
        binding.btnSos.setOnClickListener { kirimSos() }
        binding.btnKeluar.setOnClickListener { konfirmasiKeluar() }

        if (Build.VERSION.SDK_INT >= 33) {
            mintaIzinNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ===== BACK PRESS: TOLAK KELUAR (FITUR 3) =====
    @Deprecated("Deprecated")
    override fun onBackPressed() {
        if (Prefs.getKode(this) != null) {
            // Aplikasi sedang tracking, tolak back
            tampilkanDialogExitCode()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ===== UI =====
    private fun tampilkanForm() {
        binding.layoutDaftar.visibility = View.VISIBLE
        binding.layoutKonfirmasi.visibility = View.GONE
        binding.layoutTracker.visibility = View.GONE
    }

    private fun tampilkanKonfirmasi(nama: String, pembina: String?) {
        binding.layoutDaftar.visibility = View.GONE
        binding.layoutKonfirmasi.visibility = View.VISIBLE
        binding.layoutTracker.visibility = View.GONE
        binding.namaReguPreview.text = nama
        binding.pembinaPreview.text = if (!pembina.isNullOrEmpty()) "Pembina: $pembina" else ""
    }

    private fun tampilkanTracker(nama: String) {
        binding.layoutDaftar.visibility = View.GONE
        binding.layoutKonfirmasi.visibility = View.GONE
        binding.layoutTracker.visibility = View.VISIBLE
        binding.namaRegu.text = nama
    }

    // ===== DAFTAR =====
    private fun prosesDaftar() {
        val kode = binding.inputKode.text.toString().trim().uppercase()

        if (!Regex("^[A-Z0-9_-]{3,20}$").matches(kode)) {
            binding.pesanError.text = "Kode tidak valid. Gunakan 3-20 karakter (A-Z, 0-9, _, -)."
            return
        }

        binding.pesanError.text = ""

        if (!punyaIzinLokasi()) {
            kodePending = kode
            mintaIzinLokasi.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        validasiKeServer(kode)
    }

    private fun validasiKeServer(kode: String) {
        binding.btnMasuk.isEnabled = false
        binding.btnMasuk.text = "Memeriksa..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiClient.validasiRegu(kode)
            }

            binding.btnMasuk.isEnabled = true
            binding.btnMasuk.text = "Masuk"
            kodePending = null

            if (result.ok && result.regu != null) {
                pendingRegu = result.regu
                tampilkanKonfirmasi(result.regu.nama, result.regu.pembina)
            } else {
                binding.pesanError.text = result.pesan
            }
        }
    }

    private fun konfirmasiMulai() {
        val regu = pendingRegu ?: return
        Prefs.simpanRegu(this, regu.id, regu.kode, regu.nama)
        tampilkanTracker(regu.nama)
        mulaiService()
        mintaBypassBaterai()
        pendingRegu = null
    }

    private fun batalKonfirmasi() {
        pendingRegu = null
        binding.inputKode.text.clear()
        tampilkanForm()
    }

    // ===== SERVICE =====
    private fun mulaiService() {
        try {
            val intent = Intent(this, GpsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            toast("GPS mulai melacak. Jangan tutup aplikasi.")
        } catch (e: Exception) {
            toast("Gagal memulai GPS: ${e.message}")
        }
    }

    // ===== SOS =====
    private fun kirimSos() {
        AlertDialog.Builder(this)
            .setTitle("🚨 KONFIRMASI SOS")
            .setMessage("Kirim sinyal darurat ke Pos Utama?")
            .setPositiveButton("KIRIM") { _, _ ->
                try {
                    val intent = Intent(this, GpsService::class.java).apply {
                        action = GpsService.ACTION_SOS
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    toast("SOS terkirim. Tetap di posisi Anda.")
                } catch (e: Exception) {
                    toast("Gagal kirim SOS: ${e.message}")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ===== KELUAR (FITUR 3) =====
    private fun konfirmasiKeluar() {
        tampilkanDialogExitCode()
    }

    private fun tampilkanDialogExitCode() {
        if (exitDialogShown) return
        exitDialogShown = true

        val input = android.widget.EditText(this).apply {
            hint = "Masukkan kode keluar dari admin"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("🔒 Kode Keluar Diperlukan")
            .setMessage("Aplikasi ini harus tetap berjalan agar regu Anda terpantau.\n\nMasukkan kode keluar dari admin untuk menutup aplikasi.")
            .setView(input)
            .setPositiveButton("Verifikasi") { _, _ ->
                val kode = input.text.toString().trim()
                if (kode.isEmpty()) {
                    toast("Kode tidak boleh kosong.")
                    exitDialogShown = false
                    return@setPositiveButton
                }
                verifikasiExitCode(kode)
            }
            .setNegativeButton("Batal") { _, _ ->
                exitDialogShown = false
            }
            .setOnCancelListener {
                exitDialogShown = false
            }
            .show()
    }

    private fun verifikasiExitCode(exitCode: String) {
        val kodeRegu = Prefs.getKode(this) ?: return

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiClient.verifyExitCode(kodeRegu, exitCode)
            }

            exitDialogShown = false

            if (result.ok) {
                // Stop service & logout
                stopService(Intent(this@MainActivity, GpsService::class.java))
                Prefs.hapus(this@MainActivity)
                toast("Kode benar. Aplikasi ditutup.")
                tampilkanForm()
                binding.inputKode.text.clear()

                // Keluar dari aplikasi
                finishAffinity()
            } else {
                toast("❌ ${result.pesan}")
                // Getar
                val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            }
        }
    }

    // ===== PERMISSIONS =====
    private fun punyaIzinLokasi(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun cekDanMintaIzinBackground() {
        if (Build.VERSION.SDK_INT >= 29) {
            AlertDialog.Builder(this)
                .setTitle("Izin Lokasi Latar Belakang")
                .setMessage("Untuk tracking saat layar mati, pilih 'Izinkan sepanjang waktu' di pengaturan.")
                .setPositiveButton("Buka Pengaturan") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
                .setNegativeButton("Nanti", null)
                .show()
        }
    }

    private fun mintaBypassBaterai() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Agar GPS Tetap Aktif")
                .setMessage("Izinkan aplikasi berjalan di latar belakang tanpa dibatasi.")
                .setPositiveButton("Izinkan") { _, _ ->
                    try {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        )
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
                .setNegativeButton("Nanti", null)
                .show()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
