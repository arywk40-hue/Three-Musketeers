# Smart Wearable Dependencies

## Core Dependency Groups

### 1) Sensing & Embedded Stack
- Textile-compatible vital sensors (ECG, SpO₂, temperature, motion)
- Motion and gait sensing modules for posture, balance, and mobility
- Environmental sensing modules for heat/cold stress and exposure monitoring
- GPS-capable location module for geofencing and emergency assistance
- BLE-capable wearable MCU/SoC

### 2) Edge Intelligence Stack
- On-device signal preprocessing pipeline
- On-device ML runtime for classification and risk scoring
- Rules engine for emergency triggers and preventive haptics

### 3) Mobile App Stack
- Android BLE client and device management
- Real-time dashboard and notification UI
- Medication/hydration/activity reminder scheduling
- Local secure cache for offline continuity

### 4) Cloud & Platform Stack
- Secure health data storage and sync APIs
- Historical analytics and report generation services
- Alert fanout services for caregiver/family notifications
- Access-controlled caregiver and family portal

### 5) ML & Analytics Stack
- ECG anomaly classification models
- Fall-risk, dehydration, fatigue, mobility, cognitive, and sleep quality models
- Personalized risk and trend modeling pipeline

## Integration Contracts
- Wearable → App: BLE, low-latency telemetry + risk packets
- App → Cloud: secure sync for trends, reports, and dashboards
- Cloud → Caregiver channels: remote notifications and emergency updates

## Security Expectations
- Encryption in transit and at rest
- Role-based access for patient/caregiver/healthcare views
- Auditable emergency event logging
