package com.focusrefine.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The two legal documents, shown inside the app (the app has no internet permission, so nothing is fetched). */
enum class LegalDoc(val title: String, val updated: String) {
    PRIVACY("Privacy Policy", "31 October 2026"),
    TERMS("Terms of Use", "31 October 2026")
}

private class LegalSection(val heading: String, val paragraphs: List<String>, val bullets: List<String> = emptyList())

/** Opens one document: [LegalDoc.PRIVACY] or [LegalDoc.TERMS]. Not exported. */
class LegalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        val doc = runCatching { LegalDoc.valueOf(intent.getStringExtra(EXTRA_DOC).orEmpty()) }.getOrDefault(LegalDoc.PRIVACY)
        setContent {
            FocusRefineTheme {
                LegalScreen(doc, onBack = { finish() }, onOther = { open(this, it); finish() })
            }
        }
    }

    companion object {
        const val EXTRA_DOC = "doc"

        fun open(context: Context, doc: LegalDoc) {
            context.startActivity(Intent(context, LegalActivity::class.java).putExtra(EXTRA_DOC, doc.name))
        }
    }
}

@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit, onOther: (LegalDoc) -> Unit) {
    val context = LocalContext.current
    val sections = remember(doc) { if (doc == LegalDoc.PRIVACY) privacySections() else termsSections() }
    val other = if (doc == LegalDoc.PRIVACY) LegalDoc.TERMS else LegalDoc.PRIVACY
    var facts by remember { mutableStateOf<PrivacyFacts?>(null) }
    LaunchedEffect(doc) {
        if (doc == LegalDoc.PRIVACY) facts = withContext(Dispatchers.IO) { runCatching { PrivacyFacts.load(context) }.getOrNull() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = StudioColors.DarkBackground) {
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxSize()) {
            StudioTopBar(title = doc.title, onBack = onBack, showDivider = scrollState.value > 0)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                Text(
                    "Last updated ${doc.updated}",
                    fontSize = 12.sp, color = StudioColors.TextMuted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )

                if (doc == LegalDoc.PRIVACY) {
                    StudioCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛡️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.padding(start = 8.dp))
                                Text("In short", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "FocusRefine reads your notifications only to sort them, and it does that on this phone. " +
                                    "It cannot send anything anywhere, because it has no internet permission.",
                                fontSize = 13.sp, color = StudioColors.TextSecondary, lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val f = facts
                            if (f == null) {
                                Text("Checking this installation…", fontSize = 12.sp, color = StudioColors.TextMuted)
                            } else {
                                LiveRow("Internet permission", if (f.worksOffline) "None" else f.networkPermissions.joinToString(), f.worksOffline)
                                LiveRow("Cloud backup", if (f.cloudBackupAllowed) "On" else "Off", !f.cloudBackupAllowed)
                                LiveRow("Kept on this phone", "${f.storedNotifications} notifications, ${f.learnedRules} rules", true)
                            }
                        }
                    }
                }

                sections.forEach { section ->
                    StudioCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(section.heading, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            section.paragraphs.forEach {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, fontSize = 13.sp, color = StudioColors.TextSecondary, lineHeight = 19.sp)
                            }
                            if (section.bullets.isNotEmpty()) Spacer(modifier = Modifier.height(6.dp))
                            section.bullets.forEach {
                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                    Text("•", fontSize = 13.sp, color = StudioColors.TextMuted, modifier = Modifier.padding(end = 8.dp))
                                    Text(it, fontSize = 13.sp, color = StudioColors.TextSecondary, lineHeight = 19.sp)
                                }
                            }
                        }
                    }
                }

                StudioCard(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOther(other) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(other.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StudioColors.ActiveVioletLight)
                        StudioNavChevron(tint = StudioColors.ActiveVioletLight)
                    }
                }

                StudioFooter(text = "Your notifications stay on this device.", modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun LiveRow(label: String, value: String, good: Boolean) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(if (good) "✓ " else "! ", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (good) StudioColors.GreenText else StudioColors.GoldText)
        Text("$label: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = StudioColors.TextSecondary)
        Text(value, fontSize = 12.sp, color = if (good) Color.White else StudioColors.GoldText)
    }
}

private fun privacySections() = listOf(
    LegalSection(
        "What the app reads",
        listOf("To decide what is urgent, FocusRefine uses Android's notification access. For each notification that arrives it reads the app name, the title and the text."),
        listOf(
            "This happens on your phone only. The reading is done by a model that is bundled inside the app.",
            "Nothing is uploaded, shared, sold or used for ads. There is no account, no analytics and no crash reporting."
        )
    ),
    LegalSection(
        "What is stored on your phone",
        listOf("Everything below lives in the app's private storage and nowhere else:"),
        listOf(
            "Your inbox: the title, text, app, time and decision (Shown or Muted) of notifications the app handled, and a short reason for each decision.",
            "Rules you taught it by correcting a decision.",
            "A small personal model built from those corrections. It holds numbers only, never message text.",
            "Your settings: mode, strength, themes and prompts you edited."
        )
    ),
    LegalSection(
        "No network, no backup",
        listOf(
            "The app does not declare the internet permission, so Android blocks it from using the network at all. " +
                "Cloud backup is switched off, so your inbox is not copied to your Google account or to a new phone.",
            "Notification text and titles are never written to system logs."
        )
    ),
    LegalSection(
        "Permissions and why",
        emptyList(),
        listOf(
            "Notification access: lets the app see incoming notifications and move muted ones out of the shade.",
            "Show notifications: the ongoing status notification and timed-session notification.",
            "Foreground service: keeps the filter running while your phone is in your pocket.",
            "Live updates: lets a timed session appear as a status bar chip on Android 16."
        )
    ),
    LegalSection(
        "Your control",
        emptyList(),
        listOf(
            "Switch filtering off any time on Home. The app then does not look at new notifications.",
            "Remove one rule in Learned Rules, or reset all learning in Privacy Insights.",
            "The inbox is not deleted automatically. Clear it from Settings, or uninstall the app, which removes everything the app stored.",
            "Take back notification access in Android settings at any time."
        )
    ),
    LegalSection(
        "Children",
        listOf("FocusRefine is a general-purpose utility. It collects no personal data from anyone, children included.")
    ),
    LegalSection(
        "Changes and questions",
        listOf(
            "If this policy changes, the date above changes and the What's new screen says so. Questions or requests: open an issue at github.com/gnanaswaroop/FocusRefine."
        )
    )
)

private fun termsSections() = listOf(
    LegalSection(
        "Using FocusRefine",
        listOf("By installing or using FocusRefine you agree to these terms. If you do not agree, please uninstall the app."),
        listOf(
            "You may use the app for personal use on devices you own or are allowed to manage.",
            "The source code is free software under the MIT License (see below). A modified copy must not be presented as the official FocusRefine app."
        )
    ),
    LegalSection(
        "Free to use",
        listOf("FocusRefine is free of charge. There are no subscriptions, in-app purchases or advertising, and nobody is charged to install or use it.")
    ),
    LegalSection(
        "A filter, not a guarantee",
        listOf(
            "FocusRefine makes a best guess about which notifications matter in your current mode. Guesses can be wrong: an important notification can be muted, and a routine one can be shown."
        ),
        listOf(
            "Do not rely on the app for emergencies, medical alerts, security alerts, alarms or anything where a missed notification could hurt you or others.",
            "Muted notifications are kept in the Muted tab so you can check them. Keep important apps' own alerts, calls and alarms set up in Android as you normally would.",
            "The app's accuracy is measured on test data and will differ on your notifications. Text in some languages is understood less well."
        )
    ),
    LegalSection(
        "Permissions you grant",
        listOf("The app works only with notification access, which you turn on yourself in Android settings. You can turn it off any time, and the app then stops filtering.")
    ),
    LegalSection(
        "Your content",
        listOf("Your notifications belong to you and to the apps that sent them. The app only processes them on your phone to provide the filter, as described in the Privacy Policy.")
    ),
    LegalSection(
        "Open-source and third-party parts",
        listOf(
            "FocusRefine's own source code is released under the MIT License, which allows you to use, copy, modify and redistribute it, free of charge, as long as its copyright and license notice are kept.",
            "The app includes open-source libraries from third parties, each under its own license, and their terms apply to those parts."
        )
    ),
    LegalSection(
        "No warranty",
        listOf(
            "The app is provided as is and as available, without warranties of any kind, express or implied, including fitness for a particular purpose and non-infringement, to the extent the law allows."
        )
    ),
    LegalSection(
        "Limit of liability",
        listOf(
            "To the extent the law allows, the makers of FocusRefine (the FocusRefine Contributors) are not liable to you or anyone else for any claim or damage, direct or indirect, incidental or consequential, including missed or wrongly shown notifications, lost data, or any loss that follows from using or being unable to use the app. " +
                "They have no obligation to provide support, updates or fixes. Nothing in these terms limits rights you have by law that cannot be limited."
        )
    ),
    LegalSection(
        "Changes and questions",
        listOf(
            "These terms may change with a new version of the app. The date above and the What's new screen show when. " +
                "Questions: open an issue at github.com/gnanaswaroop/FocusRefine."
        )
    )
)
