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

        holder.btnOpenApp.setOnClickListener {
            try {
                var intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                if (intent == null) {
                    val pkg = notification.packageName.lowercase()
                    val title = notification.title?.lowercase() ?: ""
                    val altPackages = mutableListOf<String>()
                    if (pkg.contains("telegram") || title.contains("telegram")) {
                        altPackages.addAll(listOf("org.telegram.messenger", "org.telegram.plus", "org.telegram.messenger.web", "org.telegram.aka"))
                    } else if (pkg.contains("whatsapp") || title.contains("whatsapp")) {
                        altPackages.add("com.whatsapp")
                    } else if (pkg.contains("maps") || title.contains("maps")) {
                        altPackages.add("com.google.android.apps.maps")
                    } else {
                        altPackages.add(notification.packageName)
                    }

                    for (altPkg in altPackages) {
                        intent = context.packageManager.getLaunchIntentForPackage(altPkg)
                        if (intent != null) break
                    }
                }

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
