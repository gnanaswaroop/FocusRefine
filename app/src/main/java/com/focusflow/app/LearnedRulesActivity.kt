package com.focusflow.app

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearnedRulesActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var recyclerViewRules: RecyclerView
    private lateinit var adapter: RulesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learned_rules)

        prefs = getSharedPreferences("FocusFlowPrefs", MODE_PRIVATE)

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        recyclerViewRules = findViewById(R.id.recyclerViewRules)
        recyclerViewRules.layoutManager = LinearLayoutManager(this)

        loadRules()

        val btnClearAll = findViewById<Button>(R.id.btnClearAllRules)
        btnClearAll.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(this@LearnedRulesActivity)
                db.notificationDao().deleteAll()
                withContext(Dispatchers.Main) {
                    loadRules()
                    Toast.makeText(this@LearnedRulesActivity, "All learned rules cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadRules() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@LearnedRulesActivity)
            val notifications = db.notificationDao().getAll()
            withContext(Dispatchers.Main) {
                adapter = RulesAdapter(notifications,
                    onToggle = { notif ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val updated = notif.copy(isUrgent = !notif.isUrgent)
                            db.notificationDao().insert(updated)
                            val newList = db.notificationDao().getAll()
                            withContext(Dispatchers.Main) {
                                adapter.updateData(newList)
                                Toast.makeText(this@LearnedRulesActivity, "Rule updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDelete = { notif ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.notificationDao().delete(notif)
                            val newList = db.notificationDao().getAll()
                            withContext(Dispatchers.Main) {
                                adapter.updateData(newList)
                                Toast.makeText(this@LearnedRulesActivity, "Rule deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                recyclerViewRules.adapter = adapter
            }
        }
    }

    class RulesAdapter(
        private var items: List<NotificationEntity>,
        private val onToggle: (NotificationEntity) -> Unit,
        private val onDelete: (NotificationEntity) -> Unit
    ) : RecyclerView.Adapter<RulesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvPattern: TextView = view.findViewById(R.id.tvRulePattern)
            val tvStatus: TextView = view.findViewById(R.id.tvRuleStatus)
            val btnToggle: Button = view.findViewById(R.id.btnToggleStatus)
            val btnDelete: Button = view.findViewById(R.id.btnDeleteRule)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_learned_rule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val pattern = "${item.title ?: "App"}: ${item.text ?: ""}"
            holder.tvPattern.text = pattern

            if (item.isUrgent) {
                holder.tvStatus.text = "Status: Shown (Unhidden)"
                holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
            } else {
                holder.tvStatus.text = "Status: Muted (Hidden)"
                holder.tvStatus.setTextColor(Color.parseColor("#C62828"))
            }

            holder.btnToggle.setOnClickListener { onToggle(item) }
            holder.btnDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<NotificationEntity>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
