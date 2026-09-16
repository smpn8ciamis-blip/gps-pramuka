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

    // Launcher izin lokasi
    private val mintaIzinLokasi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasil ->
        val fineOk = hasil[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineOk) {
            cekDanMintaIzinBackground()
        } else {
            toast("Izin lokasi wajib untuk tracking.")
        }
    }

    // Launcher izin notifikasi
    private val mintaIzinNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* tidak masalah kalau ditolak */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cek sesi tersimpan
        val kodeTersimpan = Prefs.getKode(this)
        val namaTersimpan = Prefs.getNama(this)

        if (kodeTersimpan != null && namaTersimpan != null) {
            tampilkanTracker(namaTersimpan)
            mulaiService()
        } else {
            tampilkanForm()
        }

        // Setup listeners
        binding.btnMasuk.setOnClickListener { prosesDaftar() }
        binding.btnKonfirmasi.setOnClickListener { konfirmasiMulai() }
        binding.btnBatal.setOnClickListener { batalKonfirmasi() }
        binding.btnSos.setOnClickListener { kirimSos() }
        binding.btnKeluar.setOnClickListener { konfirmasiKeluar() }

        // Minta izin notifikasi (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            mintaIzinNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        binding.btnMasuk.isEnabled = false
        binding.btnMasuk.text = "Memeriksa..."

        // Pastikan izin lokasi
        if (!punyaIzinLokasi()) {
            mintaIzinLokasi.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            binding.btnMasuk.isEnabled = true
            binding.btnMasuk.text = "Masuk"
            return
        }

        // Validasi ke server
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiClient.validasiRegu(kode)
            }

            binding.btnMasuk.isEnabled = true
            binding.btnMasuk.text = "Masuk"

            if (result.ok && result.regu != null) {
                pendingRegu = result.regu
                tampilkanKonfirmasi(result.regu.nama, null)
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
        val intent = Intent(this, GpsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        toast("GPS mulai melacak. Jangan tutup aplikasi.")
    }

    // ===== SOS =====
    private fun kirimSos() {
        AlertDialog.Builder(this)
            .setTitle("🚨 KONFIRMASI SOS")
            .setMessage("Kirim sinyal darurat ke Pos Utama?")
            .setPositiveButton("KIRIM") { _, _ ->
                val intent = Intent(this, GpsService::class.java).apply {
                    action = GpsService.ACTION_SOS
                }
                startService(intent)
                toast("SOS terkirim. Tetap di posisi Anda.")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ===== KELUAR =====
    private fun konfirmasiKeluar() {
        AlertDialog.Builder(this)
            .setTitle("Keluar dari Regu")
            .setMessage("Anda akan berhenti terpantau. Yakin?")
            .setPositiveButton("Keluar") { _, _ ->
                stopService(Intent(this, GpsService::class.java))
                Prefs.hapus(this)
                tampilkanForm()
                binding.inputKode.text.clear()
            }
            .setNegativeButton("Batal", null)
            .show()
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

    // ===== BATTERY OPTIMIZATION =====
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