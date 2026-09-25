package id.sch.smpn8ciamis.gpspramuka

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import id.sch.smpn8ciamis.gpspramuka.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingRegu: ApiClient.ReguInfo? = null
    private var kodePending: String? = null
    private var exitDialogShown = false

    private val broadcastList = mutableListOf<ApiClient.BroadcastInfo>()
    private val chatList = mutableListOf<ApiClient.ChatInfo>()
    private lateinit var broadcastAdapter: BroadcastAdapter
    private lateinit var chatAdapter: ChatAdapter

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "BROADCAST_DITERIMA" -> {
                    val id = intent.getLongExtra("id", 0)
                    val pesan = intent.getStringExtra("pesan") ?: ""
                    val prioritas = intent.getStringExtra("prioritas") ?: "NORMAL"
                    val waktu = intent.getStringExtra("waktu") ?: ""
                    broadcastList.add(0, ApiClient.BroadcastInfo(id, pesan, prioritas, waktu))
                    if (broadcastList.size > 50) broadcastList.removeAt(broadcastList.size - 1)
                    broadcastAdapter.notifyDataSetChanged()
                    updateBroadcastEmpty()
                    if (prioritas == "DARURAT") {
                        val v = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0,300,100,300,100,300), -1))
                        } else {
                            @Suppress("DEPRECATION")
                            v.vibrate(longArrayOf(0,300,100,300,100,300), -1)
                        }
                    }
                }
                "CHAT_DITERIMA" -> {
                    val id = intent.getLongExtra("id", 0)
                    val pesan = intent.getStringExtra("pesan") ?: ""
                    val dari = intent.getStringExtra("dari") ?: "admin"
                    val waktu = intent.getStringExtra("waktu") ?: ""
                    chatList.add(ApiClient.ChatInfo(id, pesan, dari, waktu))
                    if (chatList.size > 100) chatList.removeAt(0)
                    chatAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private val mintaIzinLokasi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasil ->
        val fineOk = hasil[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineOk) {
            cekDanMintaIzinBackground()
            kodePending?.let { validasiKeServer(it) }
        } else {
            toast("Izin lokasi wajib untuk tracking.")
        }
    }

    private val mintaIzinNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        broadcastAdapter = BroadcastAdapter(broadcastList)
        chatAdapter = ChatAdapter(chatList)
        binding.rvBroadcast.layoutManager = LinearLayoutManager(this)
        binding.rvBroadcast.adapter = broadcastAdapter
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { binding.cardBroadcast.visibility = View.VISIBLE; binding.cardChat.visibility = View.GONE }
                    1 -> { binding.cardBroadcast.visibility = View.GONE; binding.cardChat.visibility = View.VISIBLE }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

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
        binding.btnKirimChat.setOnClickListener { kirimChat() }

        if (Build.VERSION.SDK_INT >= 33) {
            mintaIzinNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("BROADCAST_DITERIMA"); addAction("CHAT_DITERIMA")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(broadcastReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(broadcastReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated")
    override fun onBackPressed() {
        if (Prefs.getKode(this) != null) tampilkanDialogExitCode()
        else { @Suppress("DEPRECATION"); super.onBackPressed() }
    }

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
        updateBroadcastEmpty()
    }

    private fun updateBroadcastEmpty() {
        binding.tvBroadcastEmpty.visibility = if (broadcastList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun prosesDaftar() {
        val kode = binding.inputKode.text.toString().trim().uppercase()
        if (!Regex("^[A-Z0-9_-]{3,20}$").matches(kode)) {
            binding.pesanError.text = "Kode tidak valid. Gunakan 3-20 karakter (A-Z, 0-9, _, -)."
            return
        }
        binding.pesanError.text = ""
        if (!punyaIzinLokasi()) {
            kodePending = kode
            mintaIzinLokasi.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }
        validasiKeServer(kode)
    }

    private fun validasiKeServer(kode: String) {
        binding.btnMasuk.isEnabled = false
        binding.btnMasuk.text = "Memeriksa..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ApiClient.validasiRegu(kode) }
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

    private fun mulaiService() {
        try {
            val intent = Intent(this, GpsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            toast("GPS mulai melacak.")
        } catch (e: Exception) {
            toast("Gagal memulai GPS: ${e.message}")
        }
    }

    private fun kirimSos() {
        AlertDialog.Builder(this)
            .setTitle("🚨 KONFIRMASI SOS")
            .setMessage("Kirim sinyal darurat ke Pos Utama?")
            .setPositiveButton("KIRIM") { _, _ ->
                try {
                    val intent = Intent(this, GpsService::class.java).apply { action = GpsService.ACTION_SOS }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                    else startService(intent)
                    toast("SOS terkirim.")
                } catch (e: Exception) { toast("Gagal: ${e.message}") }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun kirimChat() {
        val pesan = binding.inputChat.text.toString().trim()
        if (pesan.isEmpty()) { toast("Pesan kosong"); return }
        val kode = Prefs.getKode(this) ?: return
        binding.btnKirimChat.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { ApiClient.kirimChat(kode, pesan) }
            binding.btnKirimChat.isEnabled = true
            if (ok) { binding.inputChat.text.clear(); toast("✅ Terkirim") }
            else toast("❌ Gagal")
        }
    }

    private fun konfirmasiKeluar() { tampilkanDialogExitCode() }

    private fun tampilkanDialogExitCode() {
        if (exitDialogShown) return
        exitDialogShown = true
        val input = android.widget.EditText(this).apply {
            hint = "Masukkan kode keluar dari admin"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_dialog_title))
            .setMessage(getString(R.string.exit_dialog_msg))
            .setView(input)
            .setPositiveButton(getString(R.string.exit_dialog_verify)) { _, _ ->
                val kode = input.text.toString().trim()
                if (kode.isEmpty()) { toast("Kode kosong"); exitDialogShown = false; return@setPositiveButton }
                verifikasiExitCode(kode)
            }
            .setNegativeButton(getString(R.string.exit_dialog_cancel)) { _, _ -> exitDialogShown = false }
            .setOnCancelListener { exitDialogShown = false }
            .show()
    }

    private fun verifikasiExitCode(exitCode: String) {
        val kodeRegu = Prefs.getKode(this) ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ApiClient.verifyExitCode(kodeRegu, exitCode) }
            exitDialogShown = false
            if (result.ok) {
                stopService(Intent(this@MainActivity, GpsService::class.java))
                Prefs.hapus(this@MainActivity)
                toast("Kode benar. Aplikasi ditutup.")
                tampilkanForm()
                binding.inputKode.text.clear()
                finishAffinity()
            } else {
                toast("❌ ${result.pesan}")
                val v = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(500)
                }
            }
        }
    }

    private fun punyaIzinLokasi(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
                .setNegativeButton("Nanti", null).show()
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
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
                .setNegativeButton("Nanti", null).show()
        }
    }

    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}
