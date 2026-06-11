# ML

ML starts with rule-based elder-safety alerts and evolves into validated on-device models.

## Showcase

- Fall risk from IMU impact, orientation change, and inactivity.
- SOS and caregiver alert state from button/app trigger plus vitals.
- Dehydration from humidity, temperature, and HR trend.
- Abnormal vitals from HR, SpO2, respiratory rate, and motion context.

## Product

- ECG anomaly: 1D-CNN converted to TFLite.
- Fall detection: temporal CNN or lightweight classifier.
- Caregiver alert triage: rule engine first, classifier later.
- Dehydration: Random Forest or small neural tabular model.
- Abnormal vitals: XGBoost/SVM/TFLite-compatible classifier.
- BP estimation: calibrated PPG/ECG feature model.
