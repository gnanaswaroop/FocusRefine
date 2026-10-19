package com.focusrefine.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explains how the app works and why it is offline-first and private, with a live check of the privacy facts. */
class HelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        setContent {
            FocusRefineTheme {
                HelpScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var facts by remember { mutableStateOf<PrivacyFacts?>(null) }
    LaunchedEffect(Unit) {
        facts = withContext(Dispatchers.IO) { runCatching { PrivacyFacts.load(context) }.getOrNull() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = StudioColors.DarkBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painter = painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Help & how it works", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Keep the noise out. Let what matters through.", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "FocusRefine watches the notifications your other apps send and decides, for the mode you are in, which ones " +
                            "should interrupt you. The rest are quietly moved to your Smart Inbox, where you can read them later.",
                        fontSize = 13.sp, color = StudioColors.TextSecondary, lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillBadge("Works offline", backgroundColor = StudioColors.GreenSurface, contentColor = StudioColors.GreenText)
                        PillBadge("Nothing leaves your phone", backgroundColor = StudioColors.GreenSurface, contentColor = StudioColors.GreenText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HelpSection("📱", "What it does", open = true) {
                Body("Android lets an app you approve read incoming notifications. You gave FocusRefine that access (\"Notification access\"), and it uses it for one thing: deciding Shown or Muted.")
                Bullet("Shown: the notification stays where it is, as usual.")
                Bullet("Muted: it is removed from your notification shade and saved in the Muted tab of your Smart Inbox. Nothing is deleted, so you can always read it later.")
                Bullet("The decision depends on your current mode (Commute, Deep Work, Sleeping, and so on). Switch modes from the home screen.")
            }

            HelpSection("🧭", "How it decides", open = true) {
                Body("Each notification goes through these steps in order. The first one that applies decides.")
                Numbered(1, "Your own rules", "Anything you taught it by tapping Show or Mute on a notification.")
                Numbered(2, "Safety exceptions", "Calls and priority contacts, navigation, and security codes always come through. You can switch each one off in Settings.")
                Numbered(3, "The on-device AI model", "A small language model compares the meaning of the notification with examples for your mode, so it understands rewordings, not just keywords. It works for the built-in modes.")
                Numbered(4, "Your own words", "For custom modes, and whenever the AI model is off, it looks for the keywords in your mode's description.")
                Body("Tap any notification to see which step decided and why.")
            }

            HelpSection("🔒", "Offline-first and private", open = true) {
                Body("FocusRefine does all of its work on your phone. It has no account, no server, no analytics and no ads, and it is built so that it cannot send your notifications anywhere.")
                PrivacyCheck(facts)
                Spacer(modifier = Modifier.height(8.dp))
                Bullet("Offline-first: the AI model is a file inside the app (about 6 MB). It never downloads anything, so it works the same in airplane mode.")
                Bullet("No network permission: the app does not ask Android for internet access, so even a bug could not send data out. You can see this yourself in Android Settings > Apps > FocusRefine > Permissions.")
                Bullet("No cloud backup: Android's backup is turned off for this app, so your notification history is not copied to your Google account.")
                Bullet("No logging of your messages: notification text is never written to system logs.")
                Bullet("What you teach it is stored as numbers (an embedding of the meaning), not as the message text, in the app's private storage that other apps cannot read.")
                Body("What stays outside the app's control: the apps that send you notifications still know what they sent, and Android itself still delivers them.")
            }

            HelpSection("👆", "Teach it what you want", open = false) {
                Body("Sometimes the app is not sure. Those \"close calls\" are outlined in gold with a \"Not sure\" tag, and a banner at the top of the inbox counts them.")
                Bullet("Tap Review to see only the close calls, least sure first.")
                Bullet("Show it / Mute it flips the decision. Keep muted / Keep shown confirms it. Either way the app learns.")
                Bullet("The same choices are on each notification's detail screen. Turn off \"Learn from this choice\" there to decide without teaching.")
                Bullet("The more you correct, the better it fits you. A handful of corrections changes little; a few dozen make a visible difference for the kinds of notifications you corrected.")
                Bullet("See or remove what it learned: Settings > Learned Rules. Deleting a rule also forgets the lesson. Privacy & Insights > Clear local learning data removes everything it learned.")
            }

            HelpSection("⚙️", "Settings that matter", open = false) {
                Bullet("Mode: what counts as important right now.")
                Bullet("Filtering strength: Gentle lets more through, Strict hides more. Strict can hide urgent alerts that score close to the line, so use it with care.")
                Bullet("Exceptions: calls, navigation and security codes always show. Switch off only what you do not need.")
                Bullet("AI engine: turn the AI model off to use plain keyword matching instead.")
                Bullet("Re-run classification on history: re-checks your saved notifications with your current mode and rules.")
            }

            HelpSection("⚠️", "What it cannot do well", open = false) {
                Body("FocusRefine is a helper, not a guarantee. Please keep this in mind:")
                Bullet("It can mute something urgent, especially an unusual wording or a language other than English. The AI model was built for English.")
                Bullet("Close calls are flagged, but a confident mistake is not. Glance at the Muted tab now and then.")
                Bullet("For people and alerts that must never be missed, also use Android's own priority and emergency alert settings.")
                Bullet("Never rely on FocusRefine alone for emergency alerts: keep your phone's own emergency and public safety alerts switched on.")
            }

            HelpSection("🗂️", "Your data", open = false) {
                Body("What is stored on this phone:")
                Bullet("Your notification history: app, title, text, time, the decision and the reason.")
                Bullet("The rules and lessons you taught it.")
                Bullet("Your settings.")
                Body("It all lives in the app's private storage, is removed when you uninstall the app, and is never uploaded. History is kept until you clear it: Settings > Clear Inbox. To erase what it learned: Privacy & Insights > Clear local learning data.")
            }

            HelpSection("🛠️", "Not working?", open = false) {
                Bullet("Not filtering anything: on the home screen, check that the switch says \"AI Filtering Active\", not \"Filtering Paused\".")
                Bullet("Still nothing: Android Settings > Notifications > Device & app notifications > FocusRefine must be on.")
                Bullet("Stops after a while: allow FocusRefine to run in the background (battery settings) so Android does not stop it.")
                Bullet("Settings shows \"Keyword Matcher Active\": the AI model is off or could not load. Switch it on in Settings, or restart the app.")
                Bullet("A decision looks wrong: open it, read the reason, and tap the other choice. That teaches the app.")
            }

            Spacer(modifier = Modifier.height(8.dp))
            StudioFooter(text = "Private by design. Refined by you.", modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        }
    }
}

/** A collapsible card. */
@Composable
private fun HelpSection(emoji: String, title: String, open: Boolean, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(open) }
    StudioCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$emoji  $title", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (expanded) "˅" else "›", fontSize = 18.sp, color = StudioColors.TextSecondary)
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 13.sp, color = StudioColors.TextSecondary, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("•", fontSize = 13.sp, color = StudioColors.ActiveVioletLight, modifier = Modifier.width(14.dp))
        Text(text, fontSize = 13.sp, color = StudioColors.TextSecondary, lineHeight = 19.sp)
    }
}

@Composable
private fun Numbered(n: Int, title: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$n", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StudioColors.ActiveVioletLight, modifier = Modifier.width(18.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text, fontSize = 13.sp, color = StudioColors.TextSecondary, lineHeight = 19.sp)
        }
    }
}

/** Live results, read from the system when the screen opens. */
@Composable
private fun PrivacyCheck(facts: PrivacyFacts?) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StudioColors.CardSurfaceHighlight,
        border = BorderStroke(1.dp, StudioColors.StudioBorder),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Privacy check on this phone, right now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            if (facts == null) {
                Text("Checking…", fontSize = 12.sp, color = StudioColors.TextMuted)
            } else {
                CheckRow("Network access", if (facts.worksOffline) "None. No internet permission." else "Requested: ${facts.networkPermissions.joinToString()}", facts.worksOffline)
                CheckRow("Cloud backup", if (facts.cloudBackupAllowed) "On" else "Off", !facts.cloudBackupAllowed)
                CheckRow(
                    "AI model",
                    when {
                        !facts.aiEnabled -> "Switched off (keyword matching)"
                        facts.modelLoaded -> "Running on this phone"
                        else -> "Not loaded yet (keyword matching)"
                    },
                    true
                )
                CheckRow(
                    "Stored here",
                    "${count(facts.storedNotifications, "notification")}, ${count(facts.learnedRules, "rule")} you taught it, " +
                        "${count(facts.personalLessons, "lesson")} learned",
                    true
                )
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, value: String, good: Boolean) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(if (good) "✓" else "!", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (good) StudioColors.GreenText else StudioColors.GoldText, modifier = Modifier.width(16.dp))
        Text("$label: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = StudioColors.TextSecondary)
        Text(value, fontSize = 12.sp, color = if (good) Color.White else StudioColors.GoldText)
    }
}

private fun count(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"
