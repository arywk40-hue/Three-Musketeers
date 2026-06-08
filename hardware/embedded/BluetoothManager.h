#ifndef BLUETOOTH_MANAGER_H
#define BLUETOOTH_MANAGER_H

#include <Arduino.h>
#include "Sensors.h"

void initBluetooth(String deviceName);
void sendHighFrequencyPacket(BiometricData data);
void sendLowFrequencyPacket(BiometricData data);

#endif
