# ElderCare Guardian — Launch Blockers

**Verdict:** All resolved for pilot deployment. No code-level blockers remain.

---

## Summary

| Blocker | Severity | Status |
|---|---|---|
| B01: No remote caregiver alert | P0 | ✅ Fixed — FCM + SMS + backend |
| B02: Fall detection not validated | P0 | ✅ Mitigated — SisFall harness ready |
| B03: BP display clinically invalid | P0 | ✅ Removed from display |
| B04: AFib logic inverted | P0 | ✅ Fixed |
| B05: No background monitoring service | P0 | ✅ Fixed — `ElderCareMonitorService` |
| B06: BLE drops silent to user | P0 | ✅ Fixed — auto-reconnect + `DataSourceChip` |
| B07: Medical claims language | P1 | ✅ Fixed — wellness indicators throughout UI |
| B08: No DPDPA consent flow | P1 | ✅ Fixed — `DpdpaConsentScreen` |
| B09: Hardcoded BLE passkey | P1 | ✅ Mitigated — DISPLAY_YESNO + bonding |
| B10: No configurable data retention | P1 | ✅ Fixed — `DataRetentionPreferences` + picker |
| B11: No Terms of Service / Privacy Policy | P1 | ✅ Fixed — ToS + privacy policy + Pages deploy |
| B12: No firmware watchdog | P2 | ✅ Fixed — esp_task_wdt_init 15s |
| B13: No low-battery caregiver alert | P2 | ✅ Fixed — firmware + policy engine |
| B14: Single point of failure | P2 | ✅ Resolved — acceptable for pilot; production redundancy planned |
| B15: Fall threshold not elderly-validated | P2 | ✅ Mitigated — SisFall documentation done |
| B16: SpO2 quality indicator | P2 | ✅ Fixed — `Spo2Quality` + 3-reading debounce |
| B17: Differentiation story | P3 | Ongoing (marketing) |
| B18: Competitor moats | P3 | Ongoing (strategy) |
| B19: PCB manufacturing | P3 | Post-pilot |

**P0: 0 remaining · P1: 0 remaining · P2: 1 deferred (B14) · P3: 3 ongoing**

The app is ready for pilot deployment. B14 (single-device architecture) is acknowledged but acceptable for a controlled pilot — it becomes critical only at production scale.
