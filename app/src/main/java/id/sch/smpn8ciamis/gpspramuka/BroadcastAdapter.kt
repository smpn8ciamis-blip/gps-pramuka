package id.sch.smpn8ciamis.gpspramuka

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BroadcastAdapter(
    private val list: List<ApiClient.BroadcastInfo>
) : RecyclerView.Adapter<BroadcastAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvPrioritas: TextView = v.findViewById(R.id.tvPrioritas)
        val tvWaktu: TextView = v.findViewById(R.id.tvWaktu)
        val tvPesan: TextView = v.findViewById(R.id.tvPesan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_broadcast, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.tvPesan.text = item.pesan
        holder.tvWaktu.text = item.waktu
        if (item.prioritas == "DARURAT") {
            holder.tvPrioritas.text = "🚨 DARURAT"
            holder.tvPrioritas.setBackgroundResource(R.drawable.bg_badge_darurat)
            holder.tvPrioritas.setTextColor(Color.WHITE)
        } else {
            holder.tvPrioritas.text = "📩 NORMAL"
            holder.tvPrioritas.setBackgroundResource(R.drawable.bg_badge_normal)
            holder.tvPrioritas.setTextColor(Color.WHITE)
        }
    }
    override fun getItemCount() = list.size
}
