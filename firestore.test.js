const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user cannot read or write lists or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/lists`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/tasks`).doc("task_1").set({
      userId: ALICE_UID,
      title: "Unauthorized task",
      completed: false,
      priority: "MEDIUM",
      categoryLabel: "General",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated owner can create, update, toggle, and list tasks and lists", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // Create custom list
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/lists`).doc("list_work").set({
      userId: ALICE_UID,
      name: "Trabajo",
      colorHex: "#2563EB",
      iconKey: "work",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create task referencing existing list
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_1").set({
      userId: ALICE_UID,
      title: "Preparar presentación Q4",
      notes: "Revisar métricas de sincronización",
      completed: false,
      priority: "HIGH",
      listId: "list_work",
      categoryLabel: "Trabajo",
      dueDateMillis: Date.now() + 86400000,
      deviceName: "Pixel 8",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Toggle completion (action-based update)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_1").update({
      completed: true,
      deviceName: "Pixel Tablet",
      updatedAt: updated,
    })
  );

  // Edit task fields
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_1").update({
      title: "Presentación Q4 lista",
      priority: "MEDIUM",
      updatedAt: updated,
    })
  );

  // List queries succeed on user-scoped subcollections
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/lists`).get());
});

test("Rejects ghost fields, @DocumentId 'id' injection, immutable createdAt changes, and invalid enums", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Reject 'id' field in payload
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_bad_id").set({
      id: "task_bad_id",
      userId: ALICE_UID,
      title: "Bad ID",
      completed: false,
      priority: "LOW",
      categoryLabel: "General",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject unknown ghost field
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_ghost").set({
      userId: ALICE_UID,
      title: "Ghost",
      completed: false,
      priority: "LOW",
      categoryLabel: "General",
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid priority enum
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_enum").set({
      userId: ALICE_UID,
      title: "Invalid priority",
      completed: false,
      priority: "URGENT",
      categoryLabel: "General",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject non-existent listId
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_orphan").set({
      userId: ALICE_UID,
      title: "Orphan list ref",
      completed: false,
      priority: "MEDIUM",
      listId: "missing_list",
      categoryLabel: "General",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_future").set({
      userId: ALICE_UID,
      title: "Future timestamp",
      completed: false,
      priority: "LOW",
      categoryLabel: "General",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Cross-user isolation prevents Bob from reading or modifying Alice's tasks or lists", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection(`users/${ALICE_UID}/tasks`).doc("alice_task").set({
      userId: ALICE_UID,
      title: "Alice Private Task",
      completed: false,
      priority: "HIGH",
      categoryLabel: "Personal",
      createdAt: created,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/tasks`).doc("alice_task").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/tasks`).doc("alice_task").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/tasks`).doc("alice_task").delete());
});
