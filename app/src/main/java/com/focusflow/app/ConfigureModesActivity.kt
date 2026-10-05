package com.focusflow.app

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ConfigureModesActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var recyclerViewModes: RecyclerView
    private lateinit var adapter: ModesAdapter

    data class ModeItem(val name: String, val prompt: String, val isEnabled: Boolean, val isCustom: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configure_modes)

        prefs = getSharedPreferences("FocusFlowPrefs", MODE_PRIVATE)

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        recyclerViewModes = findViewById(R.id.recyclerViewModes)
        recyclerViewModes.layoutManager = LinearLayoutManager(this)
        loadModes()

        val btnAddMode = findViewById<Button>(R.id.btnAddMode)
        btnAddMode.setOnClickListener {
            showEditModeDialog(null, null, true)
        }
    }

    private fun loadModes() {
        val list = mutableListOf<ModeItem>()

        // OOTB modes dynamically loaded from PromptManager
        val ootbConfigs = PromptManager.getAllOotbConfigs(this)
        for ((name, config) in ootbConfigs) {
            val enabled = prefs.getBoolean("mode_${name}_enabled", config.enabled)
            val prompt = prefs.getString("prompt_${name}", config.prompt) ?: config.prompt
            list.add(ModeItem(name, prompt, enabled, false))
        }

        // Custom modes
        val customStr = prefs.getString("custom_modes_list", "") ?: ""
        if (customStr.isNotEmpty()) {
            for (name in customStr.split(",")) {
                if (name.isNotBlank()) {
                    val prompt = prefs.getString("custom_mode_prompt_$name", "") ?: ""
                    val enabled = prefs.getBoolean("custom_mode_enabled_$name", true)
                    list.add(ModeItem(name, prompt, enabled, true))
                }
            }
        }

        adapter = ModesAdapter(list,
            onToggle = { mode, isEnabled ->
                if (!mode.isCustom) {
                    prefs.edit().putBoolean("mode_${mode.name}_enabled", isEnabled).apply()
                } else {
                    prefs.edit().putBoolean("custom_mode_enabled_${mode.name}", isEnabled).apply()
                }
                Toast.makeText(this, "${mode.name} enabled = $isEnabled", Toast.LENGTH_SHORT).show()
            },
            onEdit = { mode ->
                showEditModeDialog(mode.name, mode.prompt, mode.isCustom)
            },
            onDelete = { mode ->
                if (mode.isCustom) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Custom Mode")
                        .setMessage("Are you sure you want to delete '${mode.name}'?")
                        .setPositiveButton("Delete") { _, _ ->
                            val existing = prefs.getString("custom_modes_list", "") ?: ""
                            val listCustom = if (existing.isEmpty()) mutableListOf() else existing.split(",").toMutableList()
                            listCustom.remove(mode.name)
                            prefs.edit()
                                .putString("custom_modes_list", listCustom.joinToString(","))
                                .remove("custom_mode_prompt_${mode.name}")
                                .remove("custom_mode_enabled_${mode.name}")
                                .apply()
                            loadModes()
                            Toast.makeText(this, "Mode '${mode.name}' deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        )
        recyclerViewModes.adapter = adapter
    }

    private fun showEditModeDialog(modeName: String?, modePrompt: String?, isCustom: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (modeName == null) "Add Custom Mode" else "Edit Prompt: $modeName")

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_mode, null)
        val etName = view.findViewById<EditText>(R.id.etDialogModeName)
        val etPrompt = view.findViewById<EditText>(R.id.etDialogModePrompt)

        if (modeName != null) {
            etName.setText(modeName)
            etName.isEnabled = isCustom // only custom names can be changed
            etPrompt.setText(modePrompt)
        }

        builder.setView(view)
        builder.setPositiveButton("Save") { _, _ ->
            val name = etName.text.toString().trim()
            val prompt = etPrompt.text.toString().trim()
            if (name.isNotEmpty() && prompt.isNotEmpty()) {
                if (modeName == null) {
                    // Add new custom mode
                    val existing = prefs.getString("custom_modes_list", "") ?: ""
                    val customList = if (existing.isEmpty()) mutableListOf() else existing.split(",").toMutableList()
                    if (!customList.contains(name)) {
                        customList.add(name)
                        prefs.edit()
                            .putString("custom_modes_list", customList.joinToString(","))
                            .putString("custom_mode_prompt_$name", prompt)
                            .putBoolean("custom_mode_enabled_$name", true)
                            .apply()
                        loadModes()
                        Toast.makeText(this, "Mode '$name' added", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Mode name already exists", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Update prompt for OOTB or custom
                    if (isCustom) {
                        prefs.edit().putString("custom_mode_prompt_$modeName", prompt).apply()
                    } else {
                        prefs.edit().putString("prompt_$modeName", prompt).apply()
                    }
                    loadModes()
                    Toast.makeText(this, "Prompt for '$modeName' updated", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Name and prompt cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    class ModesAdapter(
        private val modes: List<ModeItem>,
        private val onToggle: (ModeItem, Boolean) -> Unit,
        private val onEdit: (ModeItem) -> Unit,
        private val onDelete: (ModeItem) -> Unit
    ) : RecyclerView.Adapter<ModesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvModeName)
            val tvPrompt: TextView = view.findViewById(R.id.tvModePrompt)
            val switchEnabled: Switch = view.findViewById(R.id.switchModeEnabled)
            val btnDelete: Button? = view.findViewById(R.id.btnDeleteModeItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mode, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val mode = modes[position]
            holder.tvName.text = mode.name
            holder.tvPrompt.text = mode.prompt
            holder.switchEnabled.isChecked = mode.isEnabled

            holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(mode, isChecked)
            }

            holder.itemView.setOnClickListener {
                onEdit(mode)
            }

            if (holder.btnDelete != null) {
                if (mode.isCustom) {
                    holder.btnDelete.visibility = View.VISIBLE
                    holder.btnDelete.setOnClickListener { onDelete(mode) }
                } else {
                    holder.btnDelete.visibility = View.GONE
                }
            }
        }

        override fun getItemCount() = modes.size
    }
}
