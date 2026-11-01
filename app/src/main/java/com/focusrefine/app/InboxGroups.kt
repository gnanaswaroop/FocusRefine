package com.focusrefine.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Muted notifications from one app, shown as one card that expands to its items.
 * First version: grouped by app only. Similarity splitting within an app is a later step (issue #23).
 */
data class MutedAppGroup(val packageName: String, val items: List<NotificationEntity>) {
    val closeCalls: Int get() = items.count { it.isUncertain() }

    /** One line for the card, for example "12 from Amazon · 3 close calls". [appName] is the app's label. */
    fun summary(appName: String): String {
        val base = "${items.size} from $appName"
        return when (closeCalls) {
            0 -> base
            1 -> "$base · 1 close call"
            else -> "$base · $closeCalls close calls"
        }
    }
}

/** One row of the inbox list: a single notification, or a group of muted ones from the same app. */
sealed interface InboxRow {
    data class Single(val notif: NotificationEntity) : InboxRow
    data class Group(val group: MutedAppGroup) : InboxRow
}

/**
 * Turns the list (newest first) into rows. With [groupByApp] on, an app that has two or more items becomes one group,
 * placed where its newest item was. An app with a single item stays a plain card, so nothing is hidden for no reason.
 */
fun inboxRows(notifications: List<NotificationEntity>, groupByApp: Boolean): List<InboxRow> {
    if (!groupByApp) return notifications.map { InboxRow.Single(it) }
    val byApp = notifications.groupBy { it.packageName }
    val placed = mutableSetOf<String>()
    val rows = mutableListOf<InboxRow>()
    for (notif in notifications) {
        val items = byApp.getValue(notif.packageName)
        when {
            items.size < 2 -> rows += InboxRow.Single(notif)
            placed.add(notif.packageName) -> rows += InboxRow.Group(MutedAppGroup(notif.packageName, items))
        }
    }
    return rows
}

/** Adds [rows] to a lazy list. Groups expand in place; every item keeps its own Shown/Muted actions. */
fun LazyListScope.inboxRowItems(rows: List<InboxRow>, onChanged: () -> Unit) {
    items(
        rows,
        key = { row ->
            when (row) {
                is InboxRow.Single -> "notif-${row.notif.id}"
                is InboxRow.Group -> "group-${row.group.packageName}"
            }
        }
    ) { row ->
        when (row) {
            is InboxRow.Single -> InboxNotificationCard(notif = row.notif, onChanged = onChanged)
            is InboxRow.Group -> MutedAppGroupCard(group = row.group, onChanged = onChanged)
        }
    }
}

/** Header row (app, count, close calls) that expands to the individual muted items. */
@Composable
fun MutedAppGroupCard(group: MutedAppGroup, onChanged: () -> Unit) {
    val context = LocalContext.current
    val appName = remember(group.packageName) { appLabel(context, group.packageName) }
    var expanded by rememberSaveable(group.packageName) { mutableStateOf(false) }
    StudioCard(border = BorderStroke(1.dp, StudioColors.StudioBorder)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(packageName = group.packageName, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.summary(appName),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 2
                    )
                    Text(
                        text = if (expanded) "Muted · tap to hide them" else "Muted · tap to see them",
                        fontSize = 11.sp,
                        color = StudioColors.TextMuted
                    )
                }
                StudioChevron(expanded = expanded)
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    group.items.forEach { InboxNotificationCard(notif = it, onChanged = onChanged) }
                }
            }
        }
    }
}

/** The app's own name from the package manager, so the summary reads "from Spotify". Falls back to the package id. */
fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (_: Exception) {
    appNameFromPackage(packageName)
}

/** A readable app name from its package id, for example "com.amazon.mShop.android.shopping" -> "Shopping". */
fun appNameFromPackage(packageName: String): String {
    val segments = packageName.split(".").filter { it.length > 2 && it !in setOf("com", "org", "net", "android", "apps", "app") }
    val rawName = segments.lastOrNull() ?: packageName.substringAfterLast(".")
    return rawName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
