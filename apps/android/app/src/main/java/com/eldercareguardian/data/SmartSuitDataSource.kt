package com.eldercareguardian.data

import kotlinx.coroutines.flow.Flow

interface SmartSuitDataSource {
    val frames: Flow<SensorFrame>
}
