package com.focusflow.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(private var notifications: List<NotificationEntity>) :
    RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.itemTitle)
        val text: TextView = view.findViewById(R.id.itemText)
        val packageName: TextView = view.findViewById(R.id.itemPackage)
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
    }

    override fun getItemCount() = notifications.size

    fun updateData(newNotifications: List<NotificationEntity>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }
}
