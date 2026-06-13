# Fall Detection CNN — Calibration Report

**Model:** FallCNN (43,906 parameters)
**Architecture:** Conv1D(32,5) → MaxPool → Conv1D(64,5) → MaxPool → Conv1D(128,3) → GlobalAvgPool → Dense(64) → Dropout → Dense(2)
**Input:** (400, 3) — 2-second window at 200 Hz, 3-axis ADXL345
**Training data:** SisFall dataset (4,396 sequences, 120K training windows)
**Test samples:** 21192

## Results

| Metric | Value |
|--------|-------|
| Accuracy | 0.8260 |
| Sensitivity (Fall Recall) | 0.6273 |
| Specificity | 0.9249 |
| Precision | 0.8062 |
| F1 Score | 0.7056 |
| ROC-AUC | 0.8601 |

## Confusion Matrix

| | Predicted ADL | Predicted Fall |
|---|---|---|
| Actual ADL | 13085 | 1062 |
| Actual Fall | 2626 | 4419 |

## Classification Report

              precision    recall  f1-score   support

      NoFall       0.83      0.92      0.88     14147
        Fall       0.81      0.63      0.71      7045

    accuracy                           0.83     21192
   macro avg       0.82      0.78      0.79     21192
weighted avg       0.82      0.83      0.82     21192

