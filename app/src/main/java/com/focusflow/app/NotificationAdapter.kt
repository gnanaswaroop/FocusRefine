package com.focusflow.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(private var notifications: List<NotificationEntity>) :
    RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.itemAppIcon)
        val title: TextView = view.findViewById(R.id.itemTitle)
        val text: TextView = view.findViewById(R.id.itemText)
        val packageName: TextView = view.findViewById(R.id.itemPackage)
        val timestamp: TextView = view.findViewById(R.id.itemTimestamp)
        val btnReclassify: ImageButton = view.findViewById(R.id.btnReclassify)
        val btnOpenApp: ImageButton = view.findViewById(R.id.btnOpenApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]
        holder.title.text = notification.title ?: "No Title"
        holder.text.text = notification.text ?: "No Text"
        holder.packageName.text = notification.packageName
        holder.timestamp.text = timeFormat.format(Date(notification.timestamp))

        val context = holder.itemView.context
        try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(notification.packageName)
            holder.appIcon.setImageDrawable(icon)
        } catch (_: Exception) {
            holder.appIcon.setImageResource(R.mipmap.ic_launcher)
        }

        val isExpanded = expandedPositions.contains(position)
        if (isExpanded) {
            holder.text.maxLines = Int.MAX_VALUE
        } else {
            holder.text.maxLines = 2
        }

        holder.itemView.setOnClickListener {
            if (expandedPositions.contains(position)) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
        }

        holder.btnReclassify.setOnClickListener {
            val db = AppDatabase.getDatabase(context)
            val newUrgent = !notification.isUrgent
            val updated = notification.copy(isUrgent = newUrgent)
            CoroutineScope(Dispatchers.IO).launch {
                db.notificationDao().insert(updated)
                val newList = db.notificationDao().getByUrgency(notification.isUrgent)
                withContext(Dispatchers.Main) {
                    updateData(newList)
                    Toast.makeText(context, if (newUrgent) "Reclassified: Moved to Shown tab" else "Reclassified: Moved to Archived tab", Toast.LENGTH_SHORT).show()
                }
            }
        }

        holder.btnOpenApp.setOnClickListener {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "App not installed on this device: ${notification.packageName}", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot open app: ${notification.packageName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount() = notifications.size

    fun getItemAt(position: Int): NotificationEntity = notifications[position]

    fun updateData(newNotifications: List<NotificationEntity>) {
        notifications = newNotifications
        expandedPositions.clear()
        notifyDataSetChanged()
    }
}
