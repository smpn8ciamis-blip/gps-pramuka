package id.sch.smpn8ciamis.gpspramuka

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val list: List<ApiClient.ChatInfo>
) : RecyclerView.Adapter<ChatAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDari: TextView = v.findViewById(R.id.tvDari)
        val tvPesan: TextView = v.findViewById(R.id.tvPesan)
        val tvWaktu: TextView = v.findViewById(R.id.tvWaktu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.tvDari.text = item.dari
        holder.tvPesan.text = item.pesan
        holder.tvWaktu.text = item.waktu
    }
    override fun getItemCount() = list.size
}
