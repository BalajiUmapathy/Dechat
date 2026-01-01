---
description: Implementation plan for DeChat research features (Guardian Node, Emergency Wipe, SOS Priority).
---
# Implementation Plan: DeChat Research Features

This plan outlines the steps to implement the "Context-Aware" and "Role-Based" features described in the DeChat research paper.

## ⚠️ Important Note on Testing
**Android Emulators generally do NOT support Bluetooth LE Mesh networking between each other.** 
You cannot test the actual *sending* of BLE packets between your Pixel 5 and 6a emulators unless you are using specialized hardware bridging.
*   **What you CAN test in Emulator:** The Logic, Commands, State Changes, Database clearing, and Log outputs.
*   **What you need physical devices for:** Verifying the actual "flash flooding" and transmission between phones.

## 1. Guardian Node (Role-Based Topology)

The goal is to allow a device to identify as a "Guardian" (First Responder) using a spare bit in the packet header.

### Step 1.1: Define the Protocol Flag
**File:** `app/src/main/java/com/bitchat/android/protocol/BinaryProtocol.kt`
*   Add a new flag constant for `IS_GUARDIAN` using the spare 4th bit (0x08).

### Step 1.2: Add User Role State
**File:** `app/src/main/java/com/bitchat/android/ui/ChatViewModel.kt` (or `ChatState`)
*   Add a variable `isGuardianMode` to track if the current user is acting as a Guardian.

### Step 1.3: Update Command Processor
**File:** `app/src/main/java/com/bitchat/android/ui/CommandProcessor.kt`
*   Add command: `/role guardian` (toggles the state on/off).
*   Add command: `/role civilian` (reverts to normal).

### Step 1.4: Inject Flag into Outgoing Packets
**File:** `app/src/main/java/com/bitchat/android/mesh/BluetoothMeshService.kt`
*   In the packet creation logic (inside `sendMessage`), check `isGuardianMode`.
*   If true, bitwise-OR the flag: `packet.flags = packet.flags | Flags.IS_GUARDIAN`.

---

## 2. Emergency `/wipe` Command

The goal is to instantly destroy all local data for security.

### Step 2.1: Implement Wipe Logic
**File:** `app/src/main/java/com/bitchat/android/ui/CommandProcessor.kt`
*   Add command: `/wipe`.
*   **Action:**
    1.  Clear `EncryptedSharedPreferences`.
    2.  Delete the main SQLite database.
    3.  Clear `Keystore` keys.
    4.  Force close/crash the app to reset state.

---

## 3. SOS Priority Mode (Context-Aware Routing)

The goal is to differentiate high-priority emergency signals from routine traffic.

### Step 3.1: Define SOS Message Logic
**File:** `app/src/main/java/com/bitchat/android/ui/CommandProcessor.kt`
*   Add command: `/sos [optional message]`.
*   This triggers a special sending function `sendSOSMessage`.

### Step 3.2: Implement Context-Aware Routing
**File:** `app/src/main/java/com/bitchat/android/mesh/BluetoothMeshService.kt`
*   Create function `sendSOSMessage(content: String)`.
*   **Context-Aware Logic:**
    *   Set **TTL = 10** (instead of standard 3/5) for maximum reach.
    *   **Bypass the Gossip Queue:** Do not add to the "store-and-forward" delay buffer.
    *   Call `connectionManager.broadcastPacket` **immediately**.

---

## Testing Guide (Android Studio)

Since emulators can't "mesh", we will verify the **Logic** via **Logcat**.

1.  **Open Logcat** in Android Studio.
2.  Filter standard: `package:com.bitchat.android`.
3.  **Test Guardian:**
    *   Type `/role guardian` in the app.
    *   **Verify Log:** `Switching to GUARDIAN mode`.
    *   Send a message "Test Guardian".
    *   **Verify Log:** `Encoding packet with flags: [ ... IS_GUARDIAN ]`.
4.  **Test SOS:**
    *   Type `/sos Help me`.
    *   **Verify Log:** `!!! SOS MODE ACTIVATED !!!`.
    *   **Verify Log:** `Broadcasting SOS packet with TTL=10 (Bypassing Gossip Cache)`.
5.  **Test Wipe:**
    *   Type `/wipe`.
    *   **Verify:** App should close/restart. Re-open it -> You should see the "Setup" screen (all chats gone).
