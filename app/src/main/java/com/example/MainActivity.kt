package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.RemoteSupportRepository
import com.example.service.DeviceTelemetryManager
import com.example.ui.AuthScreen
import com.example.ui.ControllerModeScreen
import com.example.ui.HostModeScreen
import com.example.ui.RemoteSupportViewModel
import com.example.ui.SavedDevicesScreen
import com.example.ui.signOutUser
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            QuickLinkMainWorkspace(
                currentUserId = user.uid,
                userEmailOrName = user.displayName ?: user.email ?: "Connected Account"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLinkMainWorkspace(
    currentUserId: String,
    userEmailOrName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val prefs = remember {
        context.getSharedPreferences("quicklink_role_prefs", Context.MODE_PRIVATE)
    }

    // Receiver-Only Lock Mode: When locked on the 2nd phone, that phone ONLY shows the QuickSupport
    // Host screen and hides the Control tab so the other person cannot try to control any phone!
    var isReceiverOnlyLocked by remember {
        mutableStateOf(prefs.getBoolean("receiver_only_locked", false))
    }
    var savedAdminPin by remember {
        mutableStateOf(prefs.getString("admin_lock_pin", "") ?: "")
    }
    var showRoleLockDialog by remember { mutableStateOf(false) }
    var pinDialogInput by remember { mutableStateOf("") }

    val viewModel: RemoteSupportViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RemoteSupportViewModel(
                    repository = RemoteSupportRepository(db),
                    telemetryManager = DeviceTelemetryManager(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val hostState by viewModel.hostState.collectAsStateWithLifecycle()
    val controllerState by viewModel.controllerState.collectAsStateWithLifecycle()
    val savedDevicesState by viewModel.savedDevicesState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val effectiveTab = if (isReceiverOnlyLocked) 0 else selectedTab

    if (effectiveTab != 0) {
        BackHandler {
            selectedTab = 0
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hostState.isHostingActive || controllerState.connectedSession != null) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isReceiverOnlyLocked) {
                                        "QuickSupport Receiver"
                                    } else {
                                        stringResource(id = R.string.app_name)
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (isReceiverOnlyLocked) {
                                        "রিসিভার লক মোড (শুধুমাত্র কন্ট্রোল হতে দেবে)"
                                    } else {
                                        userEmailOrName
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                pinDialogInput = ""
                                showRoleLockDialog = true
                            },
                            modifier = Modifier.testTag("role_lock_button")
                        ) {
                            Icon(
                                imageVector = if (isReceiverOnlyLocked) {
                                    Icons.Default.Lock
                                } else {
                                    Icons.Default.AdminPanelSettings
                                },
                                contentDescription = "Toggle Receiver-Only Lock Mode",
                                tint = if (isReceiverOnlyLocked) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen && !isReceiverOnlyLocked) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = effectiveTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.ScreenShare,
                                    contentDescription = stringResource(id = R.string.tab_host)
                                )
                            },
                            label = { Text(stringResource(id = R.string.tab_host)) },
                            modifier = Modifier.testTag("nav_tab_host")
                        )
                        NavigationBarItem(
                            selected = effectiveTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = stringResource(id = R.string.tab_control)
                                )
                            },
                            label = { Text(stringResource(id = R.string.tab_control)) },
                            modifier = Modifier.testTag("nav_tab_control")
                        )
                        NavigationBarItem(
                            selected = effectiveTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = stringResource(id = R.string.tab_devices)
                                )
                            },
                            label = { Text(stringResource(id = R.string.tab_devices)) },
                            modifier = Modifier.testTag("nav_tab_devices")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen && !isReceiverOnlyLocked) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            NavigationRailItem(
                                selected = effectiveTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.ScreenShare,
                                        contentDescription = stringResource(id = R.string.tab_host)
                                    )
                                },
                                label = { Text(stringResource(id = R.string.tab_host)) },
                                modifier = Modifier.testTag("nav_rail_host")
                            )
                            NavigationRailItem(
                                selected = effectiveTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = stringResource(id = R.string.tab_control)
                                    )
                                },
                                label = { Text(stringResource(id = R.string.tab_control)) },
                                modifier = Modifier.testTag("nav_rail_control")
                            )
                            NavigationRailItem(
                                selected = effectiveTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Devices,
                                        contentDescription = stringResource(id = R.string.tab_devices)
                                    )
                                },
                                label = { Text(stringResource(id = R.string.tab_devices)) },
                                modifier = Modifier.testTag("nav_rail_devices")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (effectiveTab) {
                        0 -> HostModeScreen(
                            hostState = hostState,
                            onStartHosting = { viewModel.startHostingSession() },
                            onStopHosting = { viewModel.stopHostingSession() },
                            onRegenerateCode = { viewModel.regenerateHostCredentials() },
                            onQuickSelfTest = {
                                if (!isReceiverOnlyLocked) {
                                    selectedTab = 1
                                    viewModel.startInstantSelfTestLoopback()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "রিসিভার লক মোডে শুধুমাত্র এই ফোনটি শেয়ার করা যাবে",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )

                        1 -> ControllerModeScreen(
                            controllerState = controllerState,
                            onSessionCodeChange = { viewModel.updateControllerSessionInput(it) },
                            onPinChange = { viewModel.updateControllerPinInput(it) },
                            onConnectClicked = { viewModel.connectToRemotePhone() },
                            onInstantSelfTest = { viewModel.startInstantSelfTestLoopback() },
                            onToggleTouchMode = { viewModel.toggleControllerTouchMode(it) },
                            onScreenTap = { nx, ny -> viewModel.sendRemoteScreenTap(nx, ny) },
                            onScreenSwipe = { sx, sy, ex, ey ->
                                viewModel.sendRemoteScreenSwipe(sx, sy, ex, ey)
                            },
                            onSystemAction = { cmd, payload ->
                                viewModel.sendSystemOrHardwareAction(cmd, payload)
                            },
                            onDisconnect = { viewModel.disconnectControllerSession() }
                        )

                        2 -> SavedDevicesScreen(
                            savedDevicesState = savedDevicesState,
                            onQuickReconnect = { saved ->
                                selectedTab = 1
                                viewModel.connectToRemotePhone(
                                    sessionCodeOverride = saved.sessionCode,
                                    pinOverride = saved.accessPin
                                )
                            },
                            onDeleteDevice = { id -> viewModel.deleteSavedDevice(id) }
                        )
                    }
                }
            }
        }
    }

    if (showRoleLockDialog) {
        AlertDialog(
            onDismissRequest = { showRoleLockDialog = false },
            title = {
                Text(
                    if (isReceiverOnlyLocked) {
                        "অ্যাডমিন কন্ট্রোল মোড আনলক করুন"
                    } else {
                        "২য় ফোনের জন্য Receiver-Only Lock চালু করুন"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isReceiverOnlyLocked) {
                            "এই ফোনটি এখন শুধুমাত্র QuickSupport Receiver মোডে লক করা আছে (এই ফোন থেকে অন্য কোনো ফোন দেখা বা কন্ট্রোল করা যাবে না)। আনলক করতে আপনার Admin PIN দিন:"
                        } else {
                            "আপনি যে ২য় ফোনটি কন্ট্রোল করতে চান, সেটিতে ৪-ডিজিটের একটি Admin PIN দিয়ে লক করে দিন। ফলে ওই ফোন থেকে কেউ আপনার ফোন বা অন্য কারো ফোন কন্ট্রোল করার অপশনই পাবে না—শুধু আপনিই ওই ফোনটি কন্ট্রোল করতে পারবেন!"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = pinDialogInput,
                        onValueChange = { pinDialogInput = it },
                        label = { Text("4-Digit Admin PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_lock_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = pinDialogInput.trim()
                        if (!isReceiverOnlyLocked) {
                            val pinToSave = trimmed.ifBlank { "1234" }
                            prefs.edit()
                                .putBoolean("receiver_only_locked", true)
                                .putString("admin_lock_pin", pinToSave)
                                .apply()
                            savedAdminPin = pinToSave
                            isReceiverOnlyLocked = true
                            selectedTab = 0
                            showRoleLockDialog = false
                            Toast.makeText(
                                context,
                                "Receiver-Only Lock চালু হয়েছে! (PIN: $pinToSave)",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            if (trimmed == savedAdminPin || savedAdminPin.isBlank()) {
                                prefs.edit()
                                    .putBoolean("receiver_only_locked", false)
                                    .apply()
                                isReceiverOnlyLocked = false
                                showRoleLockDialog = false
                                Toast.makeText(
                                    context,
                                    "Admin Controller Mode আনলক হয়েছে!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "ভুল Admin PIN দেওয়া হয়েছে!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isReceiverOnlyLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isReceiverOnlyLocked) "আনলক করুন" else "Receiver Lock অন করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoleLockDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
