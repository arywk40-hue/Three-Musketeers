#!/usr/bin/env python3
"""
Evaluate a trained FallCNN on test data.

Usage:
  python evaluate.py --model models/fall_cnn.pth --data-dir data/processed/
  python evaluate.py --tflite models/fall_detection.tflite --data-dir data/processed/
"""

import argparse
import os
import sys

import numpy as np
import torch
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score

from config import WINDOW_SIZE, NUM_AXES
from model import FallCNN


def evaluate_pytorch(model_path: str, X_test: np.ndarray, y_test: np.ndarray):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = FallCNN().to(device)
    model.load_state_dict(torch.load(model_path, map_location=device, weights_only=True))
    model.eval()

    X_t = torch.FloatTensor(X_test.transpose(0, 2, 1)).to(device)
    with torch.no_grad():
        logits = model(X_t)
        probs = torch.softmax(logits, dim=1)
        preds = logits.argmax(dim=1).cpu().numpy()
        probs = probs[:, 1].cpu().numpy()

    return preds, probs


def evaluate_tflite(tflite_path: str, X_test: np.ndarray):
    try:
        import tensorflow as tf
    except ImportError:
        print("TensorFlow not installed.", file=sys.stderr)
        sys.exit(1)

    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    preds: list[int] = []
    probs: list[float] = []

    for i in range(len(X_test)):
        x = X_test[i].transpose(1, 0).astype(np.float32)  # (C, L) → (L, C)
        x = x.reshape(1, *x.shape)
        interpreter.set_tensor(input_details[0]["index"], x)
        interpreter.invoke()
        out = interpreter.get_tensor(output_details[0]["index"])[0]
        preds.append(int(np.argmax(out)))
        probs.append(float(out[1]))

    return np.array(preds), np.array(probs)


def main():
    parser = argparse.ArgumentParser(description="Evaluate fall detection model")
    parser.add_argument("--model", default=None, help="PyTorch model path (.pth)")
    parser.add_argument("--tflite", default=None, help="TFLite model path (.tflite)")
    parser.add_argument("--data-dir", default="data/processed", help="Test data directory")
    args = parser.parse_args()

    if not args.model and not args.tflite:
        print("Specify either --model or --tflite", file=sys.stderr)
        sys.exit(1)

    data_dir = os.path.abspath(args.data_dir)
    X_test = np.load(os.path.join(data_dir, "X_test.npy"))
    y_test = np.load(os.path.join(data_dir, "y_test.npy"))

    print(f"Test samples: {len(X_test)}")
    print(f"Fall ratio: {y_test.mean():.3f}")

    if args.model:
        preds, probs = evaluate_pytorch(args.model, X_test, y_test)
    else:
        preds, probs = evaluate_tflite(args.tflite, X_test)

    print(f"\nClassification Report:")
    print(classification_report(y_test, preds, target_names=["NoFall", "Fall"]))
    print(f"Confusion Matrix:")
    print(confusion_matrix(y_test, preds))
    try:
        auc = roc_auc_score(y_test, probs)
        print(f"ROC-AUC: {auc:.4f}")
    except Exception:
        pass

    tn, fp, fn, tp = confusion_matrix(y_test, preds).ravel()
    sensitivity = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    specificity = tn / (tn + fp) if (tn + fp) > 0 else 0.0
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    f1 = 2 * tp / (2 * tp + fp + fn) if (2 * tp + fp + fn) > 0 else 0.0

    print(f"\nSensitivity: {sensitivity:.4f}")
    print(f"Specificity: {specificity:.4f}")
    print(f"Precision:   {precision:.4f}")
    print(f"F1 Score:    {f1:.4f}")


if __name__ == "__main__":
    main()
