package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class RemoteSupportRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun normalizeSessionId(sessionCode: String): String {
        return sessionCode.replace("[^a-zA-Z0-9]".toRegex(), "")
    }

    suspend fun createOrStartHostSession(
        sessionCode: String,
        accessPin: String,
        hostDeviceName: String,
        telemetry: DeviceTelemetrySnapshot,
        initialFrameBase64: String = "",
        frameWidth: Long = 720L,
        frameHeight: Long = 1600L
    ): Result<RemoteSession> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val sessionId = normalizeSessionId(sessionCode)
        val docRef = db.collection("remote_sessions").document(sessionId)
        val payload = mapOf(
            "sessionCode" to sessionCode,
            "accessPin" to accessPin,
            "hostUserId" to uid,
            "hostDeviceName" to hostDeviceName.take(120),
            "controllerUserId" to "",
            "controllerDeviceName" to "",
            "status" to "WAITING",
            "latestFrameBase64" to initialFrameBase64.take(450000),
            "frameWidth" to frameWidth,
            "frameHeight" to frameHeight,
            "frameSeq" to 0L,
            "batteryLevel" to telemetry.batteryLevel.coerceIn(0L, 100L),
            "isCharging" to telemetry.isCharging,
            "networkType" to telemetry.networkType.ifBlank { "Wi-Fi" }.take(60),
            "ramUsageMb" to telemetry.ramUsageMb.coerceAtLeast(0L),
            "ramTotalMb" to telemetry.ramTotalMb.coerceAtLeast(0L),
            "storageFreeGb" to telemetry.storageFreeGb.coerceAtLeast(0L),
            "activeAppName" to telemetry.activeAppName.take(150),
            "torchEnabled" to telemetry.torchEnabled,
            "accessibilityEnabled" to telemetry.accessibilityEnabled,
            "screenCaptureEnabled" to telemetry.screenCaptureEnabled,
            "clipboardText" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            val snap = docRef.get().await()
            val session = snap.toObject(
                RemoteSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: RemoteSession(
                id = sessionId,
                sessionCode = sessionCode,
                accessPin = accessPin,
                hostUserId = uid,
                hostDeviceName = hostDeviceName
            )
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getSessionById(sessionId: String): Result<RemoteSession?> {
        val cleanId = normalizeSessionId(sessionId)
        val docRef = db.collection("remote_sessions").document(cleanId)
        return try {
            val snap = docRef.get().await()
            val session = snap.toObject(
                RemoteSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeSession(sessionId: String): Flow<RemoteSession?> = flow {
        val uid = requireUserId()
        val cleanId = normalizeSessionId(sessionId)
        val docRef = db.collection("remote_sessions").document(cleanId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        RemoteSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateHostFrameAndTelemetry(
        sessionId: String,
        frameBase64: String,
        frameWidth: Long,
        frameHeight: Long,
        frameSeq: Long,
        telemetry: DeviceTelemetrySnapshot,
        statusOverride: String? = null
    ): Result<Unit> {
        val cleanId = normalizeSessionId(sessionId)
        val docRef = db.collection("remote_sessions").document(cleanId)
        val updates = mutableMapOf<String, Any>(
            "hostDeviceName" to telemetry.deviceName.take(120),
            "latestFrameBase64" to frameBase64.take(450000),
            "frameWidth" to frameWidth.coerceIn(0L, 4000L),
            "frameHeight" to frameHeight.coerceIn(0L, 4000L),
            "frameSeq" to frameSeq.coerceAtLeast(0L),
            "batteryLevel" to telemetry.batteryLevel.coerceIn(0L, 100L),
            "isCharging" to telemetry.isCharging,
            "networkType" to telemetry.networkType.ifBlank { "Wi-Fi" }.take(60),
            "ramUsageMb" to telemetry.ramUsageMb.coerceAtLeast(0L),
            "ramTotalMb" to telemetry.ramTotalMb.coerceAtLeast(0L),
            "storageFreeGb" to telemetry.storageFreeGb.coerceAtLeast(0L),
            "activeAppName" to telemetry.activeAppName.take(150),
            "torchEnabled" to telemetry.torchEnabled,
            "accessibilityEnabled" to telemetry.accessibilityEnabled,
            "screenCaptureEnabled" to telemetry.screenCaptureEnabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (statusOverride != null) {
            updates["status"] = statusOverride
        }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinSessionAsController(
        sessionCodeInput: String,
        pinInput: String,
        controllerDeviceName: String
    ): Result<RemoteSession> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanId = normalizeSessionId(sessionCodeInput)
        if (cleanId.length < 6) {
            return Result.failure(IllegalArgumentException("সঠিক ৯-ডিজিটের Session ID দিন"))
        }
        val docRef = db.collection("remote_sessions").document(cleanId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("এই Session ID দিয়ে কোনো সক্রিয় ফোন পাওয়া যায়নি"))
            }
            val existing = snap.toObject(
                RemoteSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("সেশন ডাটা পড়া যায়নি"))

            if (existing.accessPin.isNotBlank() && existing.accessPin != pinInput.trim()) {
                return Result.failure(IllegalArgumentException("ভুল Security PIN দেওয়া হয়েছে"))
            }
            if (existing.status == "ENDED") {
                return Result.failure(IllegalStateException("এই সেশনটি ইতিমধ্যে শেষ হয়ে গেছে"))
            }

            val updates = mapOf(
                "controllerUserId" to uid,
                "controllerDeviceName" to controllerDeviceName.take(120),
                "status" to "ACTIVE",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            val updatedSnap = docRef.get().await()
            val updatedSession = updatedSnap.toObject(
                RemoteSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: existing.copy(
                controllerUserId = uid,
                controllerDeviceName = controllerDeviceName,
                status = "ACTIVE"
            )
            Result.success(updatedSession)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateClipboard(sessionId: String, clipboardText: String): Result<Unit> {
        val cleanId = normalizeSessionId(sessionId)
        val docRef = db.collection("remote_sessions").document(cleanId)
        return try {
            docRef.update(
                mapOf(
                    "clipboardText" to clipboardText.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSessionStatus(sessionId: String, newStatus: String): Result<Unit> {
        val cleanId = normalizeSessionId(sessionId)
        val docRef = db.collection("remote_sessions").document(cleanId)
        return try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendRemoteCommand(
        sessionId: String,
        hostUserId: String,
        type: String,
        xPermille: Long = 500L,
        yPermille: Long = 500L,
        endXPermille: Long = 500L,
        endYPermille: Long = 500L,
        durationMs: Long = 80L,
        payload: String = ""
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanSessionId = normalizeSessionId(sessionId)
        val cmdRef = db.collection("remote_sessions")
            .document(cleanSessionId)
            .collection("commands")
            .document()
        val data = mapOf(
            "sessionId" to cleanSessionId,
            "hostUserId" to hostUserId,
            "senderUserId" to uid,
            "type" to type,
            "xPermille" to xPermille.coerceIn(0L, 1000L),
            "yPermille" to yPermille.coerceIn(0L, 1000L),
            "endXPermille" to endXPermille.coerceIn(0L, 1000L),
            "endYPermille" to endYPermille.coerceIn(0L, 1000L),
            "durationMs" to durationMs.coerceIn(10L, 5000L),
            "payload" to payload.take(1000),
            "executed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            cmdRef.set(data).await()
            Result.success(cmdRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, cmdRef.path)
            Result.failure(e)
        }
    }

    fun observeCommandsForHost(sessionId: String, hostUserId: String): Flow<List<RemoteCommand>> = flow {
        val uid = requireUserId()
        val cleanSessionId = normalizeSessionId(sessionId)
        val path = "remote_sessions/$cleanSessionId/commands"
        emitAll(
            db.collection("remote_sessions")
                .document(cleanSessionId)
                .collection("commands")
                .whereEqualTo("hostUserId", hostUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RemoteCommand::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCommandsForController(sessionId: String, senderUserId: String): Flow<List<RemoteCommand>> = flow {
        val uid = requireUserId()
        val cleanSessionId = normalizeSessionId(sessionId)
        val path = "remote_sessions/$cleanSessionId/commands"
        emitAll(
            db.collection("remote_sessions")
                .document(cleanSessionId)
                .collection("commands")
                .whereEqualTo("senderUserId", senderUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RemoteCommand::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun markCommandExecuted(sessionId: String, commandId: String): Result<Unit> {
        val cleanSessionId = normalizeSessionId(sessionId)
        val cmdRef = db.collection("remote_sessions")
            .document(cleanSessionId)
            .collection("commands")
            .document(commandId)
        return try {
            cmdRef.update(
                mapOf(
                    "executed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, cmdRef.path)
            Result.failure(e)
        }
    }

    suspend fun savePairedDevice(
        sessionCode: String,
        deviceName: String,
        accessPin: String,
        lastRole: String
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = normalizeSessionId(sessionCode).ifBlank { "dev_${System.currentTimeMillis()}" }
        val docRef = db.collection("users")
            .document(uid)
            .collection("saved_devices")
            .document(docId)
        val payload = mapOf(
            "userId" to uid,
            "sessionCode" to sessionCode.take(32),
            "deviceName" to deviceName.ifBlank { "Paired Phone" }.take(120),
            "accessPin" to accessPin.take(12),
            "lastRole" to if (lastRole == "HOST") "HOST" else "CONTROLLER",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeSavedDevices(userId: String): Flow<List<SavedDevice>> = flow {
        val uid = requireUserId()
        val path = "users/$userId/saved_devices"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("saved_devices")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedDevice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getSavedDevicesOnce(userId: String): Result<List<SavedDevice>> {
        val path = "users/$userId/saved_devices"
        return try {
            val snap = db.collection("users")
                .document(userId)
                .collection("saved_devices")
                .get()
                .await()
            val list = snap.toObjects(
                SavedDevice::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedDevice(userId: String, deviceId: String): Result<Unit> {
        val docRef = db.collection("users")
            .document(userId)
            .collection("saved_devices")
            .document(deviceId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
