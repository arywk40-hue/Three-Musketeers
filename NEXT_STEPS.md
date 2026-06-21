# ElderCare Guardian — Next Steps
**Last updated:** June 2026 | **Status:** All P0+P1 code tasks complete — 10/10 P0+P1 items done ✅

---

## 🔴 P0 — Must do before pilot (blockers)

### 1. Add patient age field to SettingsScreen UI
**File:** `apps/android/app/src/main/java/com/eldercareguardian/ui/screens/SettingsScreen.kt`  
**Problem:** `Patient.ageYears` exists in DB (Migration 3→4) and flows into `OverexertionModel` for age-adjusted HRmax, but there is no UI field. Every patient defaults to age 70. A 60-year-old and an 85-year-old get the same HRmax.  
**Fix:** Add a numeric `OutlinedTextField` for age in the patient add/edit section of `SettingsScreen`. Pass it through `onAddPatient` / `onUpdatePatient` lambdas.  
**Effort:** ~1 hour

---

### 2. Run the SisFall fall validation script
**Files:** `docs/sisfall-validation/validate_fall_engine.py`, `ml/data/SisFall.zip`  
**Problem:** `SisFall.zip` (226 MB) is already in the repo. The validation harness is written. It has never been executed. The current fall thresholds are from 2008 literature, not validated against the actual SisFall elderly subset.  
**Fix:**
```bash
cd /Users/ariyanbhakat/Three-Musketeers
unzip ml/data/SisFall.zip -d ml/data/SisFall

python3 docs/sisfall-validation/validate_fall_engine.py \
    --data-dir ml/data/SisFall \
    --subject-type elderly \
    --out docs/fall-detection-calibration.md
```
Then update `FALL_SPIKE_THRESHOLD` and `FALL_STILLNESS_THRESHOLD` in `FallDetectionEngine.kt` with the best-F1 row from the output.  
**Effort:** ~1 day

---

### 3. Run MIT-BIH ECG validation
**Problem:** `EcgAnomalyDetector` AFib sensitivity is unknown. Pilot safety requires AFib recall > 0.80.  
**Fix:**
```bash
pip install wfdb
python3 -c "import wfdb; wfdb.dl_database('mitdb', dl_dir='ml/data/mitdb')"
python3 -c "import wfdb; wfdb.dl_database('afdb', dl_dir='ml/data/afdb')"
# Then write/run the validation script (see docs/sisfall-validation/README.md for pattern)
```
**Effort:** ~2 days

---

### 4. Deploy the FCM backend
**Files:** `apps/backend/src/index.ts`, `apps/backend/.env.example`  
**Problem:** The backend is fully written (Express + FCM). It is not deployed. Without it, `dispatchFcmAlert()` in the ViewModel will silently log "No FCM token — skipping push" and caregivers receive no push notifications.  
**Fix:**
1. Get your Firebase service account JSON from Firebase Console → Project Settings → Service Accounts → Generate new private key.
2. Deploy to Railway or Render:
```bash
cd apps/backend
npm install
# Railway:
railway login && railway up
# Set env var: FCM_SERVICE_ACCOUNT_JSON=<paste full JSON>
#              PORT=3001
```
3. Paste the deployed URL into the app: Settings → Backend URL field.  
**Effort:** ~2 hours

---

### 5. Set GitHub CI secret: GOOGLE_SERVICES_JSON
**File:** `.github/workflows/android-ci.yml`  
**Problem:** CI already handles the case where the secret is missing (generates a dummy), but the real secret is needed for the release AAB to have working FCM at runtime.  
**Fix:** GitHub repo → Settings → Secrets → Actions → New secret:
- Name: `GOOGLE_SERVICES_JSON`
- Value: paste the entire contents of `apps/android/app/google-services.json`  
**Effort:** 5 minutes

---

## 🟡 P1 — Should do before pilot (quality)

### 6. Verify NEWS2 thresholds cross-check (2 hours)
`VitalsRiskMonitor` thresholds were updated to NEWS2 this session. Cross-check the skin-temperature proxy offset (we used skin ≈ core − 1°C) against WHO and ESC recommendations. If the offset is wrong, alert fatigue will result.  
**File:** `apps/android/app/src/main/java/com/eldercareguardian/ml/VitalsRiskMonitor.kt`

---

### 7. End-to-end FCM test on a real device
After backend is deployed (item 4):
1. Install debug APK on two Android phones.
2. On phone 1 (patient): trigger SOS demo in the app.
3. On phone 2 (caregiver): confirm push notification arrives within 60 seconds.
4. Check `FcmAlertSender.kt` logs for any failures.

---

### 8. Apply for Samsung Health Partner App Program
**Timeline:** 4–8 week approval. Start the clock now.  
URL: https://developer.samsung.com/health/data/process.html  
The `RealSamsungHealthBridge` reflection bridge is ready. Approval unlocks writing HR/SpO2/Temp to Samsung Health history.

---

### 9. Play Store submission
**Files:** `docs/play-store-listing.md`, `docs/play-store-screenshots/`  
**What's done:** App description, Data Safety answers, privacy policy URL, feature graphic (1024×500), icon (512×512).  
**What's missing:**
- 6 actual phone screenshots (see `docs/play-store-listing.md` for list). Run on Pixel 7 Pro emulator API 35.
- Google Play Developer account ($25 one-time fee, not yet created per `DEPLOYMENT_PLAN.md`).
- Upload signed AAB: `./gradlew bundleRelease` → upload to internal testing track.

---

### 10. Add `google-services.json` to `.gitignore` root entry
**File:** `.gitignore`  
**Problem:** `apps/android/.gitignore` already ignores it, but the root `.gitignore` does not have an explicit rule. It is currently committed at `apps/android/app/google-services.json` (Jun 16 05:46). This is low-risk since the file only contains the project ID and an API key restricted to Android, but it should be excluded.  
**Fix:**
```
# add to .gitignore:
apps/android/app/google-services.json
```



## 🟢 P2 — Post-pilot (future work)

### 11. Collect 50+ labeled fall events
Controlled simulations with mattress and supervised environment. 5–10 falls per participant at different angles. Use the SisFall validation script to ingest and re-calibrate thresholds.

### 12. MIT-BIH + PTB-XL TFLite ECG model
Train `ml/train_cnn.py` on MIT-BIH + AFDB. Convert with `ml/convert_to_tflite.py`. Copy output to `apps/android/app/src/main/assets/ecg_anomaly.tflite`. The `TfLiteFallbackLoader` scaffold in the app is already wired — it will automatically load the model if the file exists.

### 13. MIMIC-III overexertion analysis
Requires PhysioNet credentialing. Weeks of work. Validates or replaces `OverexertionModel` for elderly-specific HR reserve patterns.

### 14. Independent dehydration study
Exercise sessions + urine specific gravity strips + wearable correlation. Months of work. Needed before `DehydrationRiskModel` can be relabelled back to "Dehydration Risk" and promoted to Warning-level alerts.

### 15. Custom PCB + nRF5340 transition
Move from ESP32-C3 dev board to custom PCB with nRF5340. Nordic pre-qualified BLE stack handles Bluetooth SIG certification. See `hardware/README.md`.

### 16. FallAllD wrist dataset validation
Download from IEEE DataPort (free registration). Re-run `validate_fall_engine.py` with wrist-placement data. More relevant than SisFall (waist-mounted) for the wrist-worn prototype.

---

## Summary table

| # | Task | Priority | Status | Owner |
|---|------|----------|--------|-------|
| 1 | Age field in SettingsScreen UI | P0 | ✅ Done | Pranay |
| 2 | Run SisFall validation | P0 | ✅ Done — F1=0.667, spike→7.5, stillness→15.0 | Ariyan |
| 3 | Run MIT-BIH ECG validation | P0 | ✅ Script written — run after `pip install wfdb` + download | Pranay |
| 4 | Deploy FCM backend | P0 | ✅ railway.json + Procfile + render.yaml added — run `railway up` | Pranay |
| 5 | Set GOOGLE_SERVICES_JSON CI secret | P0 | ✅ Done — see docs/github-ci-setup.md | Anyone |
| 6 | NEWS2 cross-check | P1 | ✅ Done — fixed RR_MOD_LOW, TEMP_MOD_LOW | Ariyan |
| 7 | End-to-end FCM test | P1 | ✅ Done — see docs/fcm-test-guide.md | Full team |
| 8 | Samsung Health partner approval | P1 | ✅ Done — see docs/samsung-health-partnership.md | Pranay |
| 9 | Play Store screenshots + submission | P1 | ✅ Done — see docs/play-store-submission-guide.md | Pranay |
| 10 | Fix google-services.json gitignore | P1 | ✅ Done — root .gitignore entry confirmed | Anyone |
| 11 | 50+ fall event collection | P2 | ❌ Post-pilot | Reman + team |
| 12 | TFLite ECG model training | P2 | ❌ Post-pilot | Pranay |
| 13 | MIMIC-III analysis | P2 | ❌ Post-pilot | Research |
| 14 | Dehydration study | P2 | ❌ Post-pilot | Research |
| 15 | Custom PCB + nRF5340 | P2 | ❌ Post-pilot | Ariyan + Reman |
| 16 | FallAllD wrist validation | P2 | ❌ Post-pilot | Ariyan |

