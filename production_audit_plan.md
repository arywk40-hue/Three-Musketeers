# ElderCare Guardian Production Audit Plan

This document is the next-pass production audit placeholder for the ElderCare Guardian repository.

## Scope

The repository includes an Android app, firmware, hardware docs, ML docs, and deployment/planning material, indicating a full product system rather than a demo-only application [cite:5][cite:15].

The current audit materials already identify major production blockers including a hardcoded BLE passkey, no automatic BLE reconnect path, no real caregiver alert escalation beyond local notification, clinically unsafe blood-pressure estimation, lack of full background monitoring support, and architecture issues in the Android app [cite:16].

## High-priority findings

- BLE security must be redesigned before deployment because the firmware currently uses a fixed debug passkey and placeholder UUID strategy [cite:16].
- Monitoring reliability must be improved because the app can silently fall back to simulator behavior or otherwise fail to surface device disconnection clearly enough for a real eldercare workflow [cite:16].
- Care escalation must be implemented end to end because the existing path is documented as local-only rather than a real caregiver alerting system [cite:16].
- Android architecture must be modularized because the current app contains a large monolithic Compose file and hardcoded no-op integration seams [cite:16].
- Product claims must be tightened because the repo still describes future and clinically sensitive capabilities that require validation, labeling, or removal for production use [cite:6][cite:16].

## Next action

The recommended next step is a full repository production audit document with code-level issue inventory, deployment risk ranking, and an implementation sequence across Android, firmware, backend, compliance, and operations [cite:16].
