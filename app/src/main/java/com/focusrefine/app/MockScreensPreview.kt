package com.focusrefine.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color Tokens
private val DarkBackground = Color(0xFF121212)
private val SurfaceCard = Color(0xFF1E1E1E)
private val SurfaceVariant = Color(0xFF2A2A2A)
private val AccentViolet = Color(0xFFBB86FC)
private val SuccessGreen = Color(0xFF2E7D32)
private val ErrorRed = Color(0xFFC62828)
private val WarningAmber = Color(0xFFE65100)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)
private val BorderColor = Color(0xFF373737)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun SplashMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceVariant
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.adaptive_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "FocusRefine",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Filter the noise, focus on what truly matters",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(36.dp))
                CircularProgressIndicator(color = AccentViolet)
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "v1.0.0 (Build 1)",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun DashboardAccessDisabledMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.adaptive_foreground), contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FocusRefine", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)) {
                        Text("Settings", color = TextPrimary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Red Access Card
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, ErrorRed, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1B1D))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("✕ Notification Access Disabled", color = Color(0xFFFFCDD2), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), modifier = Modifier.weight(1f)) {
                                Text("Enable Access", fontSize = 12.sp)
                            }
                            TextButton(onClick = {}, modifier = Modifier.weight(1f)) {
                                Text("Why is this required?", color = AccentViolet, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Notification Access is required to start filtering", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun WhyAccessDialogMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceCard
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Why Notification Access Is Required", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "FocusRefine needs Notification Access to intercept, evaluate, and filter incoming notifications in real-time according to your active modes and learned rules.\n\nYour notifications are processed entirely on-device and never shared, stored externally, or uploaded.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {}) { Text("Cancel", color = TextSecondary) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)) {
                                Text("Got it, Enable Now", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun DashboardActiveMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.adaptive_foreground), contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FocusRefine", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)) {
                        Text("Settings", color = TextPrimary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("✓ Notification Access Enabled", color = Color(0xFFA5D6A7), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Control Card
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Filtering Active", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            Switch(checked = true, onCheckedChange = {})
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderColor)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Current Mode", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                            Spacer(modifier = Modifier.width(16.dp))
                            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                                Text("Commute ▼", color = AccentViolet)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Inbox Header & Tabs
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Inbox", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = {}) { Text("Muted (14)", fontWeight = FontWeight.Bold, color = AccentViolet) }
                    TextButton(onClick = {}) { Text("Shown (3)", color = TextSecondary) }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = "", onValueChange = {}, placeholder = { Text("Search notifications...", color = TextSecondary) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Spacer(modifier = Modifier.height(12.dp))

                // Card List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Google Maps", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                                Text("10:42 AM", fontSize = 11.sp, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Turn right on Oak Street in 200 feet", fontSize = 13.sp, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("com.google.android.apps.maps", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                                IconButton(onClick = {}) { Icon(painter = painterResource(id = R.drawable.ic_swap_horiz), contentDescription = null, tint = AccentViolet) }
                                IconButton(onClick = {}) { Icon(painter = painterResource(id = R.drawable.ic_open_in_new), contentDescription = null, tint = TextSecondary) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun ModeDropdownMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select Active Filtering Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Surface(color = Color(0xFF3700B3), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("✓ Commute (Active)", color = AccentViolet, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                            }
                            Text("Focus & Reading", color = TextPrimary, modifier = Modifier.padding(12.dp))
                            Text("Sleep", color = TextPrimary, modifier = Modifier.padding(12.dp))
                            Text("Gaming (Custom)", color = TextPrimary, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun SettingsMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)) { Text("Back", color = TextPrimary) }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Configure Modes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text("View, edit, or add filtering modes and rules", fontSize = 13.sp, color = TextSecondary)
                    }
                }

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Learned Rules & Customizations", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text("Review, correct, or delete rules learned from swipes", fontSize = 13.sp, color = TextSecondary)
                    }
                }

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Classification Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = Color(0xFF3E2723), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text("💡 On-Device AI Status: Gemini Nano active via AICore.", color = Color(0xFFFFB74D), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Use On-Device AI", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            Switch(checked = true, onCheckedChange = {})
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Text("Dark Theme ▼", color = AccentViolet)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun ConfigureModesMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.adaptive_foreground), contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Modes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)) { Text("Back", color = TextPrimary) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Manage your filtering modes, prompts, and active rules below.", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Commute", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                Switch(checked = true, onCheckedChange = {})
                            }
                            Text("navigation, turn left, turn right, meeting, traffic, call", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Gaming (Custom)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                Switch(checked = true, onCheckedChange = {})
                            }
                            Text("discord, voice call, raid", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = {}, colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                                Text("Delete Mode", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)) {
                    Text("Add New Custom Mode", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun LearnedRulesMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.adaptive_foreground), contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Learned Rules", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)) { Text("Back", color = TextPrimary) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Review rules learned from your swipes and reclassifications.", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("WhatsApp: Urgent message from Boss", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                Text("Status: Shown", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFA5D6A7))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Toggle Status", fontSize = 12.sp) }
                                OutlinedButton(onClick = {}, colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) { Text("Delete", fontSize = 12.sp) }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                    Text("Clear All Learned Rules")
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 200)
@Composable
fun NotificationShadeMockPreview() {
    FocusRefineTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF000000)) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF242424))) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painter = painterResource(id = R.drawable.adaptive_foreground), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FocusRefine", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                            Text("Ongoing • now", fontSize = 11.sp, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("FocusRefine Active", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Text("Suppressed: 18 notifications (Mode: Commute)", fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = {}) {
                            Text("STOP", fontSize = 12.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
