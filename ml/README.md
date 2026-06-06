# ML

ML starts with rule-based showcase alerts and evolves into validated on-device models.

## Showcase

- Rep count from IMU peaks.
- Form score from rule thresholds.
- Dehydration from humidity, temperature, and HR trend.
- Fatigue from HR, SpO2, respiratory rate, and IMU intensity.

## Product

- ECG anomaly: 1D-CNN converted to TFLite.
- Rep counter: LSTM or temporal CNN.
- Form scorer: regression model.
- Dehydration: Random Forest or small neural tabular model.
- Overexertion: XGBoost/SVM/TFLite-compatible classifier.
- BP estimation: calibrated PPG/ECG feature model.
