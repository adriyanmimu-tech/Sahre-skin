package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.service.DeviceTelemetryManager
import com.example.service.RemoteControlAccessibilityService
import com.example.service.ScreenCaptureForegroundService
import com.example.service.ScreenCaptureStateHolder

@Composable
fun HostModeScreen(
    hostState: HostUiState,
    onStartHosting: () -> Unit,
    onStopHosting: () -> Unit,
    onRegenerateCode: () -> Unit,
    onQuickSelfTest: () -> Unit
) {
    val context = LocalContext.current
    val isCapturingScreen by ScreenCaptureStateHolder.isCapturing.collectAsState()
    val isA11yConnected by RemoteControlAccessibilityService.isServiceConnected.collectAsState()
    val a11yEnabled = isA11yConnected ||
        RemoteControlAccessibilityService.isAccessibilityServiceEnabled(context)

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
                action = ScreenCaptureForegroundService.ACTION_START_CAPTURE
                putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            if (!hostState.isHostingActive) {
                onStartHosting()
            }
        } else {
            Toast.makeText(
                context,
                "স্ক্রিন ক্যাপচার অনুমতি বাতিল করা হয়েছে (ডায়াগনস্টিক মিরর মোড চলবে)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Session ID Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (hostState.isHostingActive) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hostState.isHostingActive) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hostState.isHostingActive) {
                                    "LIVE HOSTING ACTIVE • অন্য ফোন থেকে কানেক্ট করুন"
                                } else {
                                    "YOUR QUICKLINK SESSION ID"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hostState.isHostingActive) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        if (!hostState.isHostingActive) {
                            IconButton(
                                onClick = onRegenerateCode,
                                modifier = Modifier.testTag("regenerate_session_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Generate new Session ID",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = hostState.mySessionCode,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Security PIN: ${hostState.myAccessPin}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            text = hostState.localTelemetry.deviceName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                DeviceTelemetryManager(context).copyToClipboard(
                                    "Session ID: ${hostState.mySessionCode} | PIN: ${hostState.myAccessPin}"
                                )
                                Toast.makeText(
                                    context,
                                    "Session ID ও PIN কপি করা হয়েছে!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_session_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Session ID",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("কপি করুন")
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "QuickLink Remote দিয়ে আমার ফোনে কানেক্ট করো!\nSession ID: ${hostState.mySessionCode}\nSecurity PIN: ${hostState.myAccessPin}"
                                    )
                                }
                                context.startActivity(
                                    Intent.createChooser(sendIntent, "Share Session ID")
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_session_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Session ID",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("শেয়ার করুন")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!hostState.isHostingActive) {
                        Button(
                            onClick = onStartHosting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("toggle_hosting_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start Hosting"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "এই ফোনটি শেয়ার ও কন্ট্রোল চালু করুন (Start Host)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val stopIntent = Intent(
                                    context,
                                    ScreenCaptureForegroundService::class.java
                                ).apply {
                                    action = ScreenCaptureForegroundService.ACTION_STOP_CAPTURE
                                }
                                context.startService(stopIntent)
                                onStopHosting()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("toggle_hosting_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Hosting"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "স্ক্রিন শেয়ার ও কন্ট্রোল বন্ধ করুন (Stop Sharing)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (hostState.statusBanner != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Text(
                        text = hostState.statusBanner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // 2-Step Permission Setup Card (Screen Cast + Accessibility Touch)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "ফুল লাইভ স্ক্রিন ও রিমোট টাচ পারমিশন (Android Setup)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    PermissionActionItem(
                        icon = Icons.Default.ScreenShare,
                        title = "১. লাইভ স্ক্রিন ক্যাপচার (MediaProjection)",
                        subtitle = if (isCapturingScreen) {
                            "চালু আছে — আপনার ফোনের আসল স্ক্রিন লাইভ স্ট্রিম হচ্ছে"
                        } else {
                            "অন্য ফোন থেকে আপনার ফোনের লাইভ স্ক্রিন দেখার জন্য চালু করুন"
                        },
                        isReady = isCapturingScreen,
                        buttonLabel = if (isCapturingScreen) "Active" else "স্ক্রিন শেয়ার অন",
                        testTag = "start_screen_capture_button",
                        onClick = {
                            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                as MediaProjectionManager
                            mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                        }
                    )

                    PermissionActionItem(
                        icon = Icons.Default.TouchApp,
                        title = "২. রিমোট টাচ ও বাটন কন্ট্রোল (Accessibility)",
                        subtitle = if (a11yEnabled) {
                            "চালু আছে — অন্য ফোন থেকে ক্লিক, সোয়াইপ ও Home/Back কাজ করবে"
                        } else {
                            "অন্য ফোন থেকে টাচ ও সোয়াইপ কাজ করানোর জন্য Settings থেকে অন করুন"
                        },
                        isReady = a11yEnabled,
                        buttonLabel = if (a11yEnabled) "Ready" else "সেটিংস ওপেন",
                        testTag = "open_accessibility_settings_button",
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Live Device Telemetry Grid
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "এই ফোনের লাইভ স্ট্যাটাস (Live Telemetry)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = hostState.localTelemetry.activeAppName.take(24),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryMiniTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.BatteryChargingFull,
                            label = "ব্যাটারি",
                            value = "${hostState.localTelemetry.batteryLevel}% ${if (hostState.localTelemetry.isCharging) "⚡" else ""}"
                        )
                        TelemetryMiniTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Wifi,
                            label = "নেটওয়ার্ক",
                            value = hostState.localTelemetry.networkType
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryMiniTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Memory,
                            label = "র‍্যাম (RAM)",
                            value = "${hostState.localTelemetry.ramUsageMb} / ${hostState.localTelemetry.ramTotalMb} MB"
                        )
                        TelemetryMiniTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Storage,
                            label = "ফ্রি স্টোরেজ",
                            value = "${hostState.localTelemetry.storageFreeGb} GB Free"
                        )
                    }

                    Text(
                        text = "সর্বশেষ রিমোট অ্যাকশন: ${hostState.lastGestureFeedback}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // 1-Phone Instant Loopback Demo Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "এখনই ১টি ফোনেই টেস্ট করতে চান?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "২য় ফোন হাতে না থাকলেও ১ ক্লিকে লাইভ স্ক্রিন ও রিমোট কন্ট্রোল কনসোল পরীক্ষা করুন",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = onQuickSelfTest,
                        modifier = Modifier.testTag("host_quick_self_test_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("লাইভ টেস্ট")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isReady: Boolean,
    buttonLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isReady) Icons.Default.CheckCircle else icon,
            contentDescription = title,
            tint = if (isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.testTag(testTag),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun TelemetryMiniTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
