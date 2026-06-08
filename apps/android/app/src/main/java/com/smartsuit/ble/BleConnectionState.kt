package com.smartsuit.ble

enum class BleConnectionState {
    /** No BLE operation in progress. */
    Idle,
    /** Scanning for ElderCare_v1 advertisement. */
    Scanning,
    /** GATT connection request sent, awaiting STATE_CONNECTED callback. */
    Connecting,
    /** GATT physically connected, bond not yet confirmed. */
    Connected,
    /** Waiting for Android bond state machine (pairing dialog). */
    Bonding,
    /** Device is bonded + connected. Notifications are being subscribed. */
    Bonded,
    /** GATT connection dropped unexpectedly. Auto-reconnect will be attempted. */
    Disconnected,
    /** Waiting for the back-off delay before the next reconnect attempt. */
    Reconnecting,
    /** BLE is not supported or Bluetooth is disabled on this device. */
    Unsupported,
    /** Required BLE permission not granted. */
    PermissionMissing,
    /** Unrecoverable error (max reconnect attempts exceeded, or init failure). */
    Error,
}
