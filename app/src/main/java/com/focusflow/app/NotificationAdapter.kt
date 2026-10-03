package com.focusflow.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(private var notifications: List<NotificationEntity>) :
    RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.itemTitle)
        val text: TextView = view.findViewById(R.id.itemText)
        val packageName: TextView = view.findViewById(R.id.itemPackage)
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
            val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Cannot launch ${notification.packageName}", Toast.LENGTH_SHORT).show()
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
