package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RemoteSupportRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createSession_andJoinAndSendCommand_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = RemoteSupportRepository(firestore)
        val rawCode = "${(100..999).random()}-${(100..999).random()}-${(100..999).random()}"

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createOrStartHostSession(
                sessionCode = rawCode,
                accessPin = "4829",
                hostDeviceName = "Pixel 9 Host",
                telemetry = DeviceTelemetrySnapshot(
                    deviceName = "Pixel 9 Host",
                    batteryLevel = 92L,
                    isCharging = true
                )
            )
        }
        assertTrue("Host session creation should succeed", createRes.isSuccess)

        val bobUid = signInTestUser(BOB_EMAIL)
        val joinRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.joinSessionAsController(
                sessionCodeInput = rawCode,
                pinInput = "4829",
                controllerDeviceName = "Galaxy S25 Controller"
            )
        }
        assertTrue("Controller join should succeed", joinRes.isSuccess)
        assertEquals(bobUid, joinRes.getOrThrow().controllerUserId)

        val cmdRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendRemoteCommand(
                sessionId = rawCode,
                hostUserId = aliceUid,
                type = CommandType.TAP,
                xPermille = 450L,
                yPermille = 620L
            )
        }
        assertTrue("Controller sending TAP command should succeed", cmdRes.isSuccess)

        signInTestUser(ALICE_EMAIL)
        val hostCommands = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCommandsForHost(rawCode, aliceUid).first { it.isNotEmpty() }
        }
        assertTrue(hostCommands.any { it.type == CommandType.TAP && it.xPermille == 450L })
    }

    @Test
    fun savedDevices_crossUserAndUnauthenticatedAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = RemoteSupportRepository(firestore)
        val code = "912-334-${(100..999).random()}"

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.savePairedDevice(
                sessionCode = code,
                deviceName = "Alice Second Phone",
                accessPin = "1234",
                lastRole = "CONTROLLER"
            )
        }
        assertTrue(saveRes.isSuccess)

        // Cross-user access by Bob must fail with PERMISSION_DENIED
        signInTestUser(BOB_EMAIL)
        val crossUserResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getSavedDevicesOnce(aliceUid)
        }
        assertTrue(crossUserResult.isFailure)
        val crossError = crossUserResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(crossError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossError?.code)

        // Unauthenticated flow observation must fail with PERMISSION_DENIED
        auth.signOut()
        var thrownException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeSavedDevices(aliceUid).first()
            }
        } catch (e: Throwable) {
            thrownException = e
        }
        assertNotNull("Unauthenticated listener must fail", thrownException)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_host@quicklink.test"
        const val BOB_EMAIL = "bob_ctrl@quicklink.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
