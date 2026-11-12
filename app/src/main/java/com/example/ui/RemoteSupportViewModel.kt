package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CommandType
import com.example.data.DeviceTelemetrySnapshot
import com.example.data.RemoteCommand
import com.example.data.RemoteSession
import com.example.data.RemoteSupportRepository
import com.example.data.SavedDevice
import com.example.service.DeviceTelemetryManager
import com.example.service.RemoteControlAccessibilityService
import com.example.service.ScreenCaptureStateHolder
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class HostUiState(
    val mySessionCode: String = "",
    val myAccessPin: String = "",
    val isHostingActive: Boolean = false,
    val hostedSession: RemoteSession? = null,
    val recentCommands: List<RemoteCommand> = emptyList(),
    val localTelemetry: DeviceTelemetrySnapshot = DeviceTelemetrySnapshot(),
    val lastGestureFeedback: String = "রিমোট কমান্ডের জন্য প্রস্তুত",
    val lastTouchXPermille: Long = 500L,
    val lastTouchYPermille: Long = 500L,
    val statusBanner: String? = null
)

data class ControllerUiState(
    val targetSessionCodeInput: String = "",
    val targetPinInput: String = "",
    val isConnecting: Boolean = false,
    val connectedSession: RemoteSession? = null,
    val sessionCommands: List<RemoteCommand> = emptyList(),
    val isTouchMode: Boolean = true,
    val lastTapMarker: Pair<Float, Float>? = null,
    val errorBanner: String? = null,
    val infoBanner: String? = null
)

class RemoteSupportViewModel(
    private val repository: RemoteSupportRepository,
    private val telemetryManager: DeviceTelemetryManager,
    private val currentUserId: String
) : ViewModel() {

    private val _hostState = MutableStateFlow(
        HostUiState(
            mySessionCode = generateFormattedSessionCode(),
            myAccessPin = generatePinCode(),
            localTelemetry = telemetryManager.collectTelemetrySnapshot()
        )
    )
    val hostState: StateFlow<HostUiState> = _hostState.asStateFlow()

    private val _controllerState = MutableStateFlow(ControllerUiState())
    val controllerState: StateFlow<ControllerUiState> = _controllerState.asStateFlow()

    val savedDevicesState: StateFlow<UiState<List<SavedDevice>>> =
        repository.observeSavedDevices(currentUserId)
            .map<List<SavedDevice>, UiState<List<SavedDevice>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing saved devices", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load saved devices"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private var hostSessionObserverJob: Job? = null
    private var hostCommandsObserverJob: Job? = null
    private var hostStreamLoopJob: Job? = null
    private var controllerSessionObserverJob: Job? = null
    private var controllerCommandsObserverJob: Job? = null
    private val executedCommandIds = mutableSetOf<String>()

    init {
        // Keep local telemetry card updated even before hosting starts
        viewModelScope.launch {
            while (isActive) {
                val snap = telemetryManager.collectTelemetrySnapshot()
                _hostState.value = _hostState.value.copy(localTelemetry = snap)
                delay(2500L)
            }
        }
    }

    fun regenerateHostCredentials() {
        if (_hostState.value.isHostingActive) return
        _hostState.value = _hostState.value.copy(
            mySessionCode = generateFormattedSessionCode(),
            myAccessPin = generatePinCode(),
            statusBanner = "নতুন Session ID ও PIN তৈরি হয়েছে"
        )
    }

    fun startHostingSession() {
        val current = _hostState.value
        val code = current.mySessionCode
        val pin = current.myAccessPin
        val telemetry = telemetryManager.collectTelemetrySnapshot()

        viewModelScope.launch {
            val initialMirror = ScreenCaptureStateHolder.generateDiagnosticScreenFrameBase64(
                deviceName = telemetry.deviceName,
                activeAppName = telemetry.activeAppName,
                batteryLevel = telemetry.batteryLevel,
                isCharging = telemetry.isCharging,
                networkType = telemetry.networkType,
                ramUsageMb = telemetry.ramUsageMb,
                ramTotalMb = telemetry.ramTotalMb,
                lastGestureText = current.lastGestureFeedback,
                lastTouchXPermille = current.lastTouchXPermille,
                lastTouchYPermille = current.lastTouchYPermille
            )

            val result = repository.createOrStartHostSession(
                sessionCode = code,
                accessPin = pin,
                hostDeviceName = telemetry.deviceName,
                telemetry = telemetry,
                initialFrameBase64 = initialMirror.base64Jpeg,
                frameWidth = initialMirror.width.toLong(),
                frameHeight = initialMirror.height.toLong()
            )
            result.onSuccess { session ->
                _hostState.value = _hostState.value.copy(
                    isHostingActive = true,
                    hostedSession = session,
                    statusBanner = "হোস্টিং চালু হয়েছে! অন্য ফোনে এই ID দিয়ে কানেক্ট করুন।"
                )
                repository.savePairedDevice(
                    sessionCode = code,
                    deviceName = "${telemetry.deviceName} (My Host)",
                    accessPin = pin,
                    lastRole = "HOST"
                )
                observeActiveHostSession(code)
                startHostLiveStreamLoop(code)
            }.onFailure { err ->
                _hostState.value = _hostState.value.copy(
                    statusBanner = "হোস্টিং শুরু করা যায়নি: ${err.localizedMessage}"
                )
            }
        }
    }

    private fun observeActiveHostSession(sessionCode: String) {
        hostSessionObserverJob?.cancel()
        hostSessionObserverJob = viewModelScope.launch {
            repository.observeSession(sessionCode)
                .catch { e ->
                    Log.w(TAG, "Host session observer error: ${e.message}")
                }
                .collect { session ->
                    if (session != null) {
                        val oldClip = _hostState.value.hostedSession?.clipboardText.orEmpty()
                        if (session.clipboardText.isNotBlank() && session.clipboardText != oldClip) {
                            telemetryManager.copyToClipboard(session.clipboardText)
                        }
                        _hostState.value = _hostState.value.copy(
                            hostedSession = session,
                            isHostingActive = session.status != "ENDED"
                        )
                    }
                }
        }

        hostCommandsObserverJob?.cancel()
        hostCommandsObserverJob = viewModelScope.launch {
            repository.observeCommandsForHost(sessionCode, currentUserId)
                .catch { e ->
                    Log.w(TAG, "Host commands observer error: ${e.message}")
                }
                .collect { commands ->
                    _hostState.value = _hostState.value.copy(
                        recentCommands = commands.takeLast(25)
                    )
                    val unexecuted = commands.filter { !it.executed && executedCommandIds.add(it.id) }
                    for (cmd in unexecuted) {
                        executeIncomingCommandOnHost(sessionCode, cmd)
                    }
                }
        }
    }

    private fun startHostLiveStreamLoop(sessionCode: String) {
        hostStreamLoopJob?.cancel()
        hostStreamLoopJob = viewModelScope.launch {
            var seq = 1L
            while (isActive && _hostState.value.isHostingActive) {
                val telemetry = telemetryManager.collectTelemetrySnapshot()
                val realCaptured = ScreenCaptureStateHolder.latestFrame.value
                val frameToSend = if (realCaptured != null && realCaptured.base64Jpeg.isNotBlank()) {
                    realCaptured
                } else {
                    ScreenCaptureStateHolder.generateDiagnosticScreenFrameBase64(
                        deviceName = telemetry.deviceName,
                        activeAppName = telemetry.activeAppName,
                        batteryLevel = telemetry.batteryLevel,
                        isCharging = telemetry.isCharging,
                        networkType = telemetry.networkType,
                        ramUsageMb = telemetry.ramUsageMb,
                        ramTotalMb = telemetry.ramTotalMb,
                        lastGestureText = _hostState.value.lastGestureFeedback,
                        lastTouchXPermille = _hostState.value.lastTouchXPermille,
                        lastTouchYPermille = _hostState.value.lastTouchYPermille
                    )
                }

                seq += 1
                repository.updateHostFrameAndTelemetry(
                    sessionId = sessionCode,
                    frameBase64 = frameToSend.base64Jpeg,
                    frameWidth = frameToSend.width.toLong(),
                    frameHeight = frameToSend.height.toLong(),
                    frameSeq = seq,
                    telemetry = telemetry
                )
                delay(1100L)
            }
        }
    }

    private suspend fun executeIncomingCommandOnHost(sessionCode: String, cmd: RemoteCommand) {
        val a11y = RemoteControlAccessibilityService.instance
        val summary = when (cmd.type) {
            CommandType.TAP -> {
                a11y?.performRemoteTap(cmd.xPermille, cmd.yPermille)
                _hostState.value = _hostState.value.copy(
                    lastTouchXPermille = cmd.xPermille,
                    lastTouchYPermille = cmd.yPermille
                )
                "রিমোট ট্যাপ: (${cmd.xPermille / 10}%, ${cmd.yPermille / 10}%)"
            }
            CommandType.LONG_PRESS -> {
                a11y?.performRemoteLongPress(cmd.xPermille, cmd.yPermille, cmd.durationMs)
                _hostState.value = _hostState.value.copy(
                    lastTouchXPermille = cmd.xPermille,
                    lastTouchYPermille = cmd.yPermille
                )
                "লং প্রেস: (${cmd.xPermille / 10}%, ${cmd.yPermille / 10}%)"
            }
            CommandType.SWIPE -> {
                a11y?.performRemoteSwipe(
                    cmd.xPermille,
                    cmd.yPermille,
                    cmd.endXPermille,
                    cmd.endYPermille,
                    cmd.durationMs
                )
                _hostState.value = _hostState.value.copy(
                    lastTouchXPermille = cmd.endXPermille,
                    lastTouchYPermille = cmd.endYPermille
                )
                "সোয়াইপ জেসচার সম্পন্ন"
            }
            CommandType.POINTER_MARK -> {
                _hostState.value = _hostState.value.copy(
                    lastTouchXPermille = cmd.xPermille,
                    lastTouchYPermille = cmd.yPermille
                )
                "লেজার পয়েন্টার: (${cmd.xPermille / 10}%, ${cmd.yPermille / 10}%)"
            }
            CommandType.GLOBAL_BACK,
            CommandType.GLOBAL_HOME,
            CommandType.GLOBAL_RECENTS,
            CommandType.GLOBAL_NOTIFICATIONS,
            CommandType.GLOBAL_LOCK -> {
                a11y?.performSystemNavigation(cmd.type)
                "সিস্টেম বাটন: ${cmd.type.removePrefix("GLOBAL_")}"
            }
            CommandType.TOGGLE_TORCH -> {
                val on = telemetryManager.toggleFlashlight()
                if (on) "রিমোট টর্চলাইট ON" else "রিমোট টর্চলাইট OFF"
            }
            CommandType.RING_DEVICE -> {
                telemetryManager.triggerAudibleRingAndVibration()
                "রিমোট রিং ও ভাইব্রেশন বাজানো হয়েছে"
            }
            CommandType.SET_CLIPBOARD -> {
                telemetryManager.copyToClipboard(cmd.payload)
                "ক্লিপবোর্ড টেক্সট কপি হয়েছে: ${cmd.payload.take(25)}"
            }
            CommandType.CHAT_MESSAGE -> {
                "মেসেজ: ${cmd.payload}"
            }
            else -> cmd.type
        }

        _hostState.value = _hostState.value.copy(lastGestureFeedback = summary)
        repository.markCommandExecuted(sessionCode, cmd.id)
    }

    fun stopHostingSession() {
        val code = _hostState.value.mySessionCode
        hostStreamLoopJob?.cancel()
        hostSessionObserverJob?.cancel()
        hostCommandsObserverJob?.cancel()
        viewModelScope.launch {
            repository.updateSessionStatus(code, "ENDED")
            _hostState.value = _hostState.value.copy(
                isHostingActive = false,
                hostedSession = null,
                statusBanner = "স্ক্রিন শেয়ারিং ও রিমোট কন্ট্রোল বন্ধ করা হয়েছে"
            )
        }
    }

    // --- CONTROLLER FLOW ---

    fun updateControllerSessionInput(code: String) {
        _controllerState.value = _controllerState.value.copy(
            targetSessionCodeInput = code,
            errorBanner = null
        )
    }

    fun updateControllerPinInput(pin: String) {
        _controllerState.value = _controllerState.value.copy(
            targetPinInput = pin,
            errorBanner = null
        )
    }

    fun connectToRemotePhone(sessionCodeOverride: String? = null, pinOverride: String? = null) {
        val code = (sessionCodeOverride ?: _controllerState.value.targetSessionCodeInput).trim()
        val pin = (pinOverride ?: _controllerState.value.targetPinInput).trim()
        val myDevice = telemetryManager.collectTelemetrySnapshot().deviceName

        _controllerState.value = _controllerState.value.copy(
            targetSessionCodeInput = code,
            targetPinInput = pin,
            isConnecting = true,
            errorBanner = null
        )

        viewModelScope.launch {
            val joinResult = repository.joinSessionAsController(
                sessionCodeInput = code,
                pinInput = pin,
                controllerDeviceName = myDevice
            )
            joinResult.onSuccess { session ->
                _controllerState.value = _controllerState.value.copy(
                    isConnecting = false,
                    connectedSession = session,
                    infoBanner = "${session.hostDeviceName}-এর সাথে লাইভ কানেক্ট হয়েছে!"
                )
                repository.savePairedDevice(
                    sessionCode = session.sessionCode,
                    deviceName = session.hostDeviceName,
                    accessPin = pin,
                    lastRole = "CONTROLLER"
                )
                observeControlledSession(session.sessionCode)
            }.onFailure { err ->
                _controllerState.value = _controllerState.value.copy(
                    isConnecting = false,
                    errorBanner = err.localizedMessage ?: "কানেক্ট করা যায়নি"
                )
            }
        }
    }

    /**
     * Allows a user with 1 phone right now to immediately test the Live Remote Control Console
     * by launching a self-hosted loop session and connecting to it in 1 tap.
     */
    fun startInstantSelfTestLoopback() {
        val current = _hostState.value
        val code = current.mySessionCode
        val pin = current.myAccessPin
        val telemetry = telemetryManager.collectTelemetrySnapshot()

        _controllerState.value = _controllerState.value.copy(
            targetSessionCodeInput = code,
            targetPinInput = pin,
            isConnecting = true,
            errorBanner = null
        )

        viewModelScope.launch {
            val initialMirror = ScreenCaptureStateHolder.generateDiagnosticScreenFrameBase64(
                deviceName = telemetry.deviceName,
                activeAppName = telemetry.activeAppName,
                batteryLevel = telemetry.batteryLevel,
                isCharging = telemetry.isCharging,
                networkType = telemetry.networkType,
                ramUsageMb = telemetry.ramUsageMb,
                ramTotalMb = telemetry.ramTotalMb,
                lastGestureText = current.lastGestureFeedback,
                lastTouchXPermille = current.lastTouchXPermille,
                lastTouchYPermille = current.lastTouchYPermille
            )

            val createRes = repository.createOrStartHostSession(
                sessionCode = code,
                accessPin = pin,
                hostDeviceName = telemetry.deviceName,
                telemetry = telemetry,
                initialFrameBase64 = initialMirror.base64Jpeg,
                frameWidth = initialMirror.width.toLong(),
                frameHeight = initialMirror.height.toLong()
            )
            createRes.onSuccess {
                _hostState.value = _hostState.value.copy(isHostingActive = true)
                observeActiveHostSession(code)
                startHostLiveStreamLoop(code)
                connectToRemotePhone(code, pin)
            }.onFailure { err ->
                _controllerState.value = _controllerState.value.copy(
                    isConnecting = false,
                    errorBanner = err.localizedMessage ?: "সেশন শুরু করা যায়নি"
                )
            }
        }
    }

    private fun observeControlledSession(sessionCode: String) {
        controllerSessionObserverJob?.cancel()
        controllerSessionObserverJob = viewModelScope.launch {
            repository.observeSession(sessionCode)
                .catch { e ->
                    Log.w(TAG, "Controller session observer error: ${e.message}")
                }
                .collect { session ->
                    if (session != null) {
                        _controllerState.value = _controllerState.value.copy(
                            connectedSession = session
                        )
                    }
                }
        }

        controllerCommandsObserverJob?.cancel()
        controllerCommandsObserverJob = viewModelScope.launch {
            repository.observeCommandsForController(sessionCode, currentUserId)
                .catch { e ->
                    Log.w(TAG, "Controller commands observer error: ${e.message}")
                }
                .collect { commands ->
                    _controllerState.value = _controllerState.value.copy(
                        sessionCommands = commands.takeLast(30)
                    )
                }
        }
    }

    fun toggleControllerTouchMode(isTouch: Boolean) {
        _controllerState.value = _controllerState.value.copy(isTouchMode = isTouch)
    }

    fun sendRemoteScreenTap(normX: Float, normY: Float) {
        val session = _controllerState.value.connectedSession ?: return
        val xPerm = (normX.coerceIn(0f, 1f) * 1000f).toLong()
        val yPerm = (normY.coerceIn(0f, 1f) * 1000f).toLong()
        val cmdType = if (_controllerState.value.isTouchMode) {
            CommandType.TAP
        } else {
            CommandType.POINTER_MARK
        }

        _controllerState.value = _controllerState.value.copy(
            lastTapMarker = Pair(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
        )

        viewModelScope.launch {
            repository.sendRemoteCommand(
                sessionId = session.sessionCode,
                hostUserId = session.hostUserId,
                type = cmdType,
                xPermille = xPerm,
                yPermille = yPerm,
                endXPermille = xPerm,
                endYPermille = yPerm,
                durationMs = 80L
            )
        }
    }

    fun sendRemoteScreenSwipe(
        startNormX: Float,
        startNormY: Float,
        endNormX: Float,
        endNormY: Float
    ) {
        val session = _controllerState.value.connectedSession ?: return
        val sx = (startNormX.coerceIn(0f, 1f) * 1000f).toLong()
        val sy = (startNormY.coerceIn(0f, 1f) * 1000f).toLong()
        val ex = (endNormX.coerceIn(0f, 1f) * 1000f).toLong()
        val ey = (endNormY.coerceIn(0f, 1f) * 1000f).toLong()

        _controllerState.value = _controllerState.value.copy(
            lastTapMarker = Pair(endNormX.coerceIn(0f, 1f), endNormY.coerceIn(0f, 1f))
        )

        viewModelScope.launch {
            repository.sendRemoteCommand(
                sessionId = session.sessionCode,
                hostUserId = session.hostUserId,
                type = CommandType.SWIPE,
                xPermille = sx,
                yPermille = sy,
                endXPermille = ex,
                endYPermille = ey,
                durationMs = 300L
            )
        }
    }

    fun sendSystemOrHardwareAction(commandType: String, payload: String = "") {
        val session = _controllerState.value.connectedSession ?: return
        viewModelScope.launch {
            if (commandType == CommandType.SET_CLIPBOARD && payload.isNotBlank()) {
                repository.updateClipboard(session.sessionCode, payload)
            }
            repository.sendRemoteCommand(
                sessionId = session.sessionCode,
                hostUserId = session.hostUserId,
                type = commandType,
                payload = payload
            )
        }
    }

    fun disconnectControllerSession() {
        controllerSessionObserverJob?.cancel()
        controllerCommandsObserverJob?.cancel()
        _controllerState.value = _controllerState.value.copy(
            connectedSession = null,
            sessionCommands = emptyList(),
            lastTapMarker = null,
            infoBanner = "রিমোট সেশন থেকে ডিসকানেক্ট করা হয়েছে"
        )
    }

    fun deleteSavedDevice(deviceId: String) {
        viewModelScope.launch {
            repository.deleteSavedDevice(currentUserId, deviceId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        hostStreamLoopJob?.cancel()
        hostSessionObserverJob?.cancel()
        hostCommandsObserverJob?.cancel()
        controllerSessionObserverJob?.cancel()
        controllerCommandsObserverJob?.cancel()
    }

    companion object {
        private const val TAG = "RemoteSupportVM"

        fun generateFormattedSessionCode(): String {
            val p1 = Random.nextInt(100, 999)
            val p2 = Random.nextInt(100, 999)
            val p3 = Random.nextInt(100, 999)
            return "$p1-$p2-$p3"
        }

        fun generatePinCode(): String {
            return Random.nextInt(1000, 9999).toString()
        }
    }
}
