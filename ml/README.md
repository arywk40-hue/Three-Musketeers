# Fall Detection CNN — Training Pipeline

Trains a 1D-CNN on the SisFall dataset and converts to TFLite for on-device inference.

## Setup

```bash
pip install -r requirements.txt
```

## Pipeline

```bash
# 1. Download SisFall dataset
python download_sisfall.py --out data/

# 2. Preprocess: windowing + normalisation + train/test split
python preprocess.py --data-dir data/SisFall --out data/processed/

# 3. Train CNN
python train_cnn.py --data-dir data/processed/ --out models/

# 4. Convert to TFLite
python convert_to_tflite.py --model models/fall_cnn.pth --out models/fall_detection.tflite

# 5. Evaluate
python evaluate.py --model models/fall_cnn.pth --data-dir data/processed/
```

## Output

- `models/fall_cnn.pth` — PyTorch state dict
- `models/fall_detection.tflite` — TFLite model for Android
- `models/calibration_report.md` — Performance metrics

## Android Integration

Copy `models/fall_detection.tflite` to `apps/android/app/src/main/assets/`.
