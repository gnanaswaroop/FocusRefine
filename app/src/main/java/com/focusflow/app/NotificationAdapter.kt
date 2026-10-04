package com.focusflow.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(private var notifications: List<NotificationEntity>) :
    RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.itemTitle)
        val text: TextView = view.findViewById(R.id.itemText)
        val packageName: TextView = view.findViewById(R.id.itemPackage)
        val timestamp: TextView = view.findViewById(R.id.itemTimestamp)
        val btnOpenApp: Button = view.findViewById(R.id.btnOpenApp)
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

        holder.btnOpenApp.setOnClickListener {
            val context = holder.itemView.context
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${notification.packageName}")
                    }
                    context.startActivity(settingsIntent)
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot open app: ${notification.packageName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount() = notifications.size

    fun updateData(newNotifications: List<NotificationEntity>) {
        notifications = newNotifications
        expandedPositions.clear()
        notifyDataSetChanged()
    }
}
