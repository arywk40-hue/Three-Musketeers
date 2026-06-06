package com.smartsuit.ble

enum class BleConnectionState {
    Idle,
    Scanning,
    Connecting,
    Connected,
    Disconnected,
    Unsupported,
    PermissionMissing,
    Error,
}
