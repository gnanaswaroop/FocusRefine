package com.focusrefine.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "What's new": the release notes, newest first, read from `assets/changelog.json`. */
class ChangelogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        setContent {
            FocusRefineTheme {
                ChangelogScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val releases = remember { Changelog.load(context) }
    val installed = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = StudioColors.DarkBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painter = painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("What's new", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (installed.isNotEmpty()) {
                Text(
                    "You have version $installed",
                    fontSize = 12.sp, color = StudioColors.TextMuted,
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                )
            }

            if (releases.isEmpty()) {
                StudioCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "The release notes could not be loaded.",
                        fontSize = 13.sp, color = StudioColors.TextSecondary, modifier = Modifier.padding(16.dp)
                    )
                }
            }

            releases.forEach { release ->
                StudioCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillBadge(
                                "Version ${release.version}",
                                backgroundColor = StudioColors.ActiveViolet.copy(alpha = 0.25f),
                                contentColor = StudioColors.ActiveVioletLight
                            )
                            Text(release.date, fontSize = 12.sp, color = StudioColors.TextMuted)
                            if (release.version == installed) {
                                PillBadge("Installed", backgroundColor = StudioColors.GreenSurface, contentColor = StudioColors.GreenText)
                            }
                        }
                        if (release.title.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(release.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        release.sections.forEach { section ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                section.heading,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = when (section.heading) {
                                    "New" -> StudioColors.GreenText
                                    "Fixed" -> StudioColors.GoldText
                                    "Privacy" -> StudioColors.GreenText
                                    else -> StudioColors.ActiveVioletLight
                                }
                            )
                            section.items.forEach { item ->
                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                    Text("•", fontSize = 13.sp, color = StudioColors.TextMuted, modifier = Modifier.width(14.dp))
                                    Text(item, fontSize = 13.sp, color = StudioColors.TextSecondary, lineHeight = 19.sp)
                                }
                            }
                        }
                    }
                }
            }

            StudioFooter(text = "Private by design. Refined by you.", modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        }
    }
}
