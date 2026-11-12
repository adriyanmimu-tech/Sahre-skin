package com.example.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CommandType
import com.example.data.RemoteSession

@Composable
fun ControllerModeScreen(
    controllerState: ControllerUiState,
    onSessionCodeChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onConnectClicked: () -> Unit,
    onInstantSelfTest: () -> Unit,
    onToggleTouchMode: (Boolean) -> Unit,
    onScreenTap: (Float, Float) -> Unit,
    onScreenSwipe: (Float, Float, Float, Float) -> Unit,
    onSystemAction: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    val activeSession = controllerState.connectedSession
    if (activeSession != null) {
        BackHandler {
            onDisconnect()
        }
        LiveRemoteConsoleView(
            session = activeSession,
            controllerState = controllerState,
            onToggleTouchMode = onToggleTouchMode,
            onScreenTap = onScreenTap,
            onScreenSwipe = onScreenSwipe,
            onSystemAction = onSystemAction,
            onDisconnect = onDisconnect
        )
    } else {
        ConnectToRemotePhoneForm(
            controllerState = controllerState,
            onSessionCodeChange = onSessionCodeChange,
            onPinChange = onPinChange,
            onConnectClicked = onConnectClicked,
            onInstantSelfTest = onInstantSelfTest
        )
    }
}

@Composable
private fun ConnectToRemotePhoneForm(
    controllerState: ControllerUiState,
    onSessionCodeChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onConnectClicked: () -> Unit,
    onInstantSelfTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhonelinkSetup,
                            contentDescription = "Connect Remote Phone",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "অন্য মোবাইল কন্ট্রোল করুন (One-Way Control)",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "শুধু আপনিই অপর ফোনের স্ক্রিন দেখবেন ও কন্ট্রোল করবেন — অপর ফোন কখনোই আপনার স্ক্রিন দেখতে পাবে না",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "One-Way Privacy Shield",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "১০০% একমুখী (One-Way): আপনার এই ফোনের স্ক্রিন কখনোই অন্য ফোনে শেয়ার হবে না। শুধু আপনিই তার ফোন দেখতে ও চালাতে পারবেন।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    OutlinedTextField(
                        value = controllerState.targetSessionCodeInput,
                        onValueChange = onSessionCodeChange,
                        label = { Text("Remote Phone Session ID (যেমন: 482-910-335)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remote_session_id_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedTextField(
                        value = controllerState.targetPinInput,
                        onValueChange = onPinChange,
                        label = { Text("4-Digit Security PIN (যেমন: 7294)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remote_pin_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (controllerState.errorBanner != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = controllerState.errorBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onConnectClicked,
                        enabled = !controllerState.isConnecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("connect_remote_phone_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (controllerState.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Connect"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "লাইভ স্ক্রিন দেখুন ও কন্ট্রোল করুন (Connect)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onInstantSelfTest,
                        enabled = !controllerState.isConnecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("instant_self_test_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("১টি ফোনেই লাইভ কন্ট্রোল ডেমো চালু করুন (Instant Test)")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRemoteConsoleView(
    session: RemoteSession,
    controllerState: ControllerUiState,
    onToggleTouchMode: (Boolean) -> Unit,
    onScreenTap: (Float, Float) -> Unit,
    onScreenSwipe: (Float, Float, Float, Float) -> Unit,
    onSystemAction: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var showChatAndClipboardDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var textPayloadInput by remember { mutableStateOf("") }

    val decodedBitmap = remember(session.latestFrameBase64) {
        if (session.latestFrameBase64.isBlank()) {
            null
        } else {
            try {
                val bytes = Base64.decode(session.latestFrameBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Connected Status Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = session.hostDeviceName.ifBlank { "Remote Phone" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "চলছে: ${session.activeAppName} • 🔋 ${session.batteryLevel}% • Frame #${session.frameSeq}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                IconButton(
                    onClick = { showDiagnosticsDialog = true },
                    modifier = Modifier.testTag("remote_diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "System Diagnostics",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDisconnect,
                    modifier = Modifier.testTag("disconnect_remote_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Disconnect Session",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Touch vs Laser Pointer Mode Selector + Hardware Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = controllerState.isTouchMode,
                    onClick = { onToggleTouchMode(true) },
                    label = { Text("Touch & Swipe") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Touch Mode",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("mode_touch_chip")
                )
                FilterChip(
                    selected = !controllerState.isTouchMode,
                    onClick = { onToggleTouchMode(false) },
                    label = { Text("Laser Pointer") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AdsClick,
                            contentDescription = "Pointer Mode",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("mode_pointer_chip")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onSystemAction(CommandType.TOGGLE_TORCH, "") },
                    modifier = Modifier.testTag("remote_torch_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashlightOn,
                        contentDescription = "Toggle Remote Flashlight",
                        tint = if (session.torchEnabled) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(
                    onClick = { onSystemAction(CommandType.RING_DEVICE, "") },
                    modifier = Modifier.testTag("remote_ring_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Ring Remote Phone",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { showChatAndClipboardDialog = true },
                    modifier = Modifier.testTag("remote_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Send Clipboard or Chat Message",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Interactive Live Remote Screen Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            var dragStart by remember { mutableStateOf(Offset.Zero) }
            var dragEnd by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .aspectRatio(0.5f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .background( Color(0xFF080D1A) )
                    .testTag("live_remote_screen_canvas")
                    .pointerInput(controllerState.isTouchMode) {
                        detectTapGestures { offset ->
                            val normX = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val normY = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            onScreenTap(normX, normY)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                dragStart = start
                                dragEnd = start
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragEnd = change.position
                            },
                            onDragEnd = {
                                val w = size.width.coerceAtLeast(1).toFloat()
                                val h = size.height.coerceAtLeast(1).toFloat()
                                onScreenSwipe(
                                    (dragStart.x / w).coerceIn(0f, 1f),
                                    (dragStart.y / h).coerceIn(0f, 1f),
                                    (dragEnd.x / w).coerceIn(0f, 1f),
                                    (dragEnd.y / h).coerceIn(0f, 1f)
                                )
                            }
                        )
                    }
            ) {
                if (decodedBitmap != null) {
                    Image(
                        bitmap = decodedBitmap,
                        contentDescription = "Live Remote Phone Screen",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "রিমোট ফোনের লাইভ স্ক্রিন ফ্রেম লোড হচ্ছে...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Draw Touch Ripple / Laser Pointer Marker Overlay
                val marker = controllerState.lastTapMarker
                if (marker != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val px = marker.first * size.width
                        val py = marker.second * size.height
                        drawCircle(
                            color = Color(0xFF38BDF8),
                            radius = 26.dp.toPx(),
                            center = Offset(px, py),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFFF43F5E),
                            radius = 8.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Remote System Navigation Dock (Back, Home, Recents, Notifications, Lock)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteNavDockButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Back",
                    testTag = "remote_back_button",
                    onClick = { onSystemAction(CommandType.GLOBAL_BACK, "") }
                )
                RemoteNavDockButton(
                    icon = Icons.Default.Home,
                    label = "Home",
                    testTag = "remote_home_button",
                    onClick = { onSystemAction(CommandType.GLOBAL_HOME, "") }
                )
                RemoteNavDockButton(
                    icon = Icons.Default.Apps,
                    label = "Recents",
                    testTag = "remote_recents_button",
                    onClick = { onSystemAction(CommandType.GLOBAL_RECENTS, "") }
                )
                RemoteNavDockButton(
                    icon = Icons.Default.Notifications,
                    label = "Notifs",
                    testTag = "remote_notifications_button",
                    onClick = { onSystemAction(CommandType.GLOBAL_NOTIFICATIONS, "") }
                )
                RemoteNavDockButton(
                    icon = Icons.Default.Lock,
                    label = "Lock",
                    testTag = "remote_lock_button",
                    onClick = { onSystemAction(CommandType.GLOBAL_LOCK, "") }
                )
            }
        }
    }

    if (showChatAndClipboardDialog) {
        AlertDialog(
            onDismissRequest = { showChatAndClipboardDialog = false },
            title = { Text("ক্লিপবোর্ড টেক্সট বা মেসেজ পাঠান") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "অপর ফোনে সরাসরি টেক্সট কপি করে দিতে বা স্ক্রিনে মেসেজ পাঠাতে নিচে লিখুন:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = textPayloadInput,
                        onValueChange = { textPayloadInput = it },
                        label = { Text("টেক্সট বা মেসেজ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textPayloadInput.isNotBlank()) {
                            onSystemAction(CommandType.SET_CLIPBOARD, textPayloadInput)
                            textPayloadInput = ""
                        }
                        showChatAndClipboardDialog = false
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Clipboard",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ক্লিপবোর্ডে পাঠান")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChatAndClipboardDialog = false }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }

    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text("রিমোট ফোনের সম্পূর্ণ সিস্টেম রিপোর্ট") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ডিভাইস মডেল: ${session.hostDeviceName}")
                    Text("বর্তমান স্ক্রিন/অ্যাপ: ${session.activeAppName}")
                    Text("ব্যাটারি: ${session.batteryLevel}% (${if (session.isCharging) "Charging" else "Discharging"})")
                    Text("নেটওয়ার্ক: ${session.networkType}")
                    Text("র‍্যাম ব্যবহার: ${session.ramUsageMb} MB / ${session.ramTotalMb} MB")
                    Text("খালি স্টোরেজ: ${session.storageFreeGb} GB")
                    Text("স্ক্রিন রেজোলিউশন: ${session.frameWidth} x ${session.frameHeight}")
                    Text("রিমোট টাচ সার্ভিস: ${if (session.accessibilityEnabled) "চালু আছে" else "বন্ধ আছে"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }
}

@Composable
private fun RemoteNavDockButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
