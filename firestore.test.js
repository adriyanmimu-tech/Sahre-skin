const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_host_123";
const BOB_UID = "bob_controller_456";
const EVE_UID = "eve_intruder_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildValidSession(hostUid, overrides = {}) {
  return {
    sessionCode: "482-910-335",
    accessPin: "7294",
    hostUserId: hostUid,
    hostDeviceName: "Pixel 9 Pro (Android 16)",
    controllerUserId: "",
    controllerDeviceName: "",
    status: "WAITING",
    latestFrameBase64: "",
    frameWidth: 720,
    frameHeight: 1600,
    frameSeq: 0,
    batteryLevel: 88,
    isCharging: true,
    networkType: "Wi-Fi 5GHz",
    ramUsageMb: 4200,
    ramTotalMb: 8192,
    storageFreeGb: 64,
    activeAppName: "QuickLink Remote",
    torchEnabled: false,
    accessibilityEnabled: true,
    screenCaptureEnabled: true,
    clipboardText: "",
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

test("Unauthenticated user: cannot read or create remote_sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("remote_sessions").doc("482910335").get());
  await assertFails(
    unauthDb.collection("remote_sessions").doc("482910335").set(buildValidSession(ALICE_UID))
  );
});

test("Host user: can create and update their own remote_session, rejects shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("remote_sessions").doc("482910335");

  await assertSucceeds(docRef.set(buildValidSession(ALICE_UID)));

  // Host can update frame and battery telemetry
  await assertSucceeds(
    docRef.update({
      batteryLevel: 90,
      frameSeq: 1,
      latestFrameBase64: "dGVzdF9mcmFtZQ==",
      updatedAt: new Date(),
    })
  );

  // Shadow field injection must fail
  await assertFails(
    docRef.update({
      isVerifiedAdmin: true,
      updatedAt: new Date(),
    })
  );
});

test("Controller user: can join session and send RemoteCommand, host can mark executed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  const sessionRefAlice = aliceDb.collection("remote_sessions").doc("482910335");
  await assertSucceeds(sessionRefAlice.set(buildValidSession(ALICE_UID)));

  // Bob joins as controller
  const sessionRefBob = bobDb.collection("remote_sessions").doc("482910335");
  await assertSucceeds(
    sessionRefBob.update({
      controllerUserId: BOB_UID,
      controllerDeviceName: "Galaxy S25 Ultra",
      status: "ACTIVE",
      updatedAt: new Date(),
    })
  );

  // Bob sends a TAP command
  const cmdRefBob = sessionRefBob.collection("commands").doc("cmd_1");
  await assertSucceeds(
    cmdRefBob.set({
      sessionId: "482910335",
      hostUserId: ALICE_UID,
      senderUserId: BOB_UID,
      type: "TAP",
      xPermille: 520,
      yPermille: 640,
      endXPermille: 520,
      endYPermille: 640,
      durationMs: 80,
      payload: "",
      executed: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Eve (unrelated user) cannot read Bob's command on Alice's session
  await assertFails(
    eveDb.collection("remote_sessions").doc("482910335").collection("commands").doc("cmd_1").get()
  );

  // Alice (host) can mark command as executed
  const cmdRefAlice = sessionRefAlice.collection("commands").doc("cmd_1");
  await assertSucceeds(
    cmdRefAlice.update({
      executed: true,
      updatedAt: new Date(),
    })
  );
});

test("User-scoped saved_devices: enforces strict owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const aliceDeviceRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("saved_devices")
    .doc("dev_1");

  await assertSucceeds(
    aliceDeviceRef.set({
      userId: ALICE_UID,
      sessionCode: "482-910-335",
      deviceName: "My Second Phone",
      accessPin: "7294",
      lastRole: "CONTROLLER",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Alice can list her saved devices
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_devices").get()
  );

  // Bob cannot list or get Alice's saved devices
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("saved_devices").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("saved_devices").doc("dev_1").get()
  );
});
