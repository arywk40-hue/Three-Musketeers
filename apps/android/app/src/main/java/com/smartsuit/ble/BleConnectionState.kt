package com.smartsuit.ble

enum class BleConnectionState {
    Idle,
    Scanning,
    Connecting,
    Connected,
    Bonding,
    Bonded,
    Disconnected,
    Unsupported,
    PermissionMissing,
    Error,
}
