#!/usr/bin/env python3
"""
Train the 1D-CNN fall detection model on preprocessed SisFall data.

Usage:
  python train_cnn.py --data-dir data/processed/ --out models/
"""

import argparse
import os
import sys

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score
from tqdm import tqdm

from config import BATCH_SIZE, EPOCHS, LEARNING_RATE, VALIDATION_SPLIT, RANDOM_SEED
from model import FallCNN, count_parameters


def set_seed(seed: int = RANDOM_SEED):
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def train_epoch(model, loader, criterion, optimizer, device):
    model.train()
    total_loss = 0
    correct = 0
    total = 0
    for X_batch, y_batch in loader:
        X_batch, y_batch = X_batch.to(device), y_batch.to(device)
        optimizer.zero_grad()
        logits = model(X_batch)
        loss = criterion(logits, y_batch)
        loss.backward()
        optimizer.step()

        total_loss += loss.item() * X_batch.size(0)
        preds = logits.argmax(dim=1)
        correct += (preds == y_batch).sum().item()
        total += y_batch.size(0)

    return total_loss / total, correct / total


def validate(model, loader, criterion, device):
    model.eval()
    total_loss = 0
    correct = 0
    total = 0
    with torch.no_grad():
        for X_batch, y_batch in loader:
            X_batch, y_batch = X_batch.to(device), y_batch.to(device)
            logits = model(X_batch)
            loss = criterion(logits, y_batch)

            total_loss += loss.item() * X_batch.size(0)
            preds = logits.argmax(dim=1)
            correct += (preds == y_batch).sum().item()
            total += y_batch.size(0)

    return total_loss / total, correct / total


def predict(model, loader, device):
    model.eval()
    all_preds: list[int] = []
    all_probs: list[float] = []
    with torch.no_grad():
        for X_batch, _ in loader:
            X_batch = X_batch.to(device)
            logits = model(X_batch)
            probs = torch.softmax(logits, dim=1)
            all_preds.extend(logits.argmax(dim=1).cpu().numpy())
            all_probs.extend(probs[:, 1].cpu().numpy())
    return np.array(all_preds), np.array(all_probs)


def main():
    parser = argparse.ArgumentParser(description="Train fall detection CNN")
    parser.add_argument("--data-dir", default="data/processed", help="Preprocessed data directory")
    parser.add_argument("--out", default="models", help="Output directory for models")
    parser.add_argument("--epochs", type=int, default=EPOCHS, help="Number of epochs")
    parser.add_argument("--batch-size", type=int, default=BATCH_SIZE, help="Batch size")
    parser.add_argument("--lr", type=float, default=LEARNING_RATE, help="Learning rate")
    args = parser.parse_args()

    set_seed()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    data_dir = os.path.abspath(args.data_dir)
    out_dir = os.path.abspath(args.out)
    os.makedirs(out_dir, exist_ok=True)

    # Load data
    X_train = np.load(os.path.join(data_dir, "X_train.npy"))
    y_train = np.load(os.path.join(data_dir, "y_train.npy"))
    X_test = np.load(os.path.join(data_dir, "X_test.npy"))
    y_test = np.load(os.path.join(data_dir, "y_test.npy"))

    print(f"Train: {X_train.shape}, Test: {X_test.shape}")
    print(f"Fall ratio in train: {y_train.mean():.3f}, test: {y_test.mean():.3f}")

    # Convert to tensors: (N, C, L) for Conv1d
    X_train_t = torch.FloatTensor(X_train.transpose(0, 2, 1))
    y_train_t = torch.LongTensor(y_train)
    X_test_t = torch.FloatTensor(X_test.transpose(0, 2, 1))
    y_test_t = torch.LongTensor(y_test)

    # Train/val split
    val_size = int(len(X_train) * VALIDATION_SPLIT)
    indices = np.random.permutation(len(X_train))
    train_idx, val_idx = indices[val_size:], indices[:val_size]

    train_dataset = TensorDataset(X_train_t[train_idx], y_train_t[train_idx])
    val_dataset = TensorDataset(X_train_t[val_idx], y_train_t[val_idx])
    test_dataset = TensorDataset(X_test_t, y_test_t)

    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=args.batch_size, shuffle=False)
    test_loader = DataLoader(test_dataset, batch_size=args.batch_size, shuffle=False)

    # Build model
    model = FallCNN().to(device)
    print(f"Model parameters: {count_parameters(model):,}")

    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=args.lr)
    scheduler = optim.lr_scheduler.ReduceLROnPlateau(optimizer, factor=0.5, patience=5)

    best_val_acc = 0.0
    for epoch in range(args.epochs):
        train_loss, train_acc = train_epoch(model, train_loader, criterion, optimizer, device)
        val_loss, val_acc = validate(model, val_loader, criterion, device)
        scheduler.step(val_loss)

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save(model.state_dict(), os.path.join(out_dir, "fall_cnn.pth"))

        if (epoch + 1) % 5 == 0 or epoch == 0:
            print(f"Epoch {epoch+1:3d}/{args.epochs} | "
                  f"Train Loss: {train_loss:.4f} Acc: {train_acc:.4f} | "
                  f"Val Loss: {val_loss:.4f} Acc: {val_acc:.4f} | "
                  f"Best: {best_val_acc:.4f}")

    # Load best model and evaluate on test set
    model.load_state_dict(torch.load(os.path.join(out_dir, "fall_cnn.pth"), weights_only=True))
    test_loss, test_acc = validate(model, test_loader, criterion, device)
    preds, probs = predict(model, test_loader, device)

    print(f"\n=== Test Results ===")
    print(f"Test accuracy: {test_acc:.4f}")
    print(f"Test loss: {test_loss:.4f}")
    print(f"\nClassification Report:")
    print(classification_report(y_test, preds, target_names=["NoFall", "Fall"]))
    print(f"Confusion Matrix:")
    print(confusion_matrix(y_test, preds))
    try:
        auc = roc_auc_score(y_test, probs)
        print(f"ROC-AUC: {auc:.4f}")
    except Exception:
        pass

    # Save calibration report
    report_path = os.path.join(out_dir, "calibration_report.md")
    tn, fp, fn, tp = confusion_matrix(y_test, preds).ravel()
    sensitivity = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    specificity = tn / (tn + fp) if (tn + fp) > 0 else 0.0
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    f1 = 2 * tp / (2 * tp + fp + fn) if (2 * tp + fp + fn) > 0 else 0.0

    report = f"""# Fall Detection CNN — Calibration Report

**Model:** FallCNN ({count_parameters(model):,} parameters)
**Input:** {model.conv1[0]} → {model.conv2[0]} → {model.conv3[0]} → Dense({DENSE_UNITS}) → Dropout → Dense(2)
**Test samples:** {len(y_test)}
**Device:** {device}

## Results

| Metric | Value |
|--------|-------|
| Sensitivity (Recall) | {sensitivity:.4f} |
| Specificity | {specificity:.4f} |
| Precision | {precision:.4f} |
| F1 Score | {f1:.4f} |
| ROC-AUC | {auc:.4f} |
| Accuracy | {test_acc:.4f} |

## Confusion Matrix

| | Predicted ADL | Predicted Fall |
|---|---|---|
| Actual ADL | {tn} | {fp} |
| Actual Fall | {fn} | {tp} |
"""
    with open(report_path, "w") as f:
        f.write(report)
    print(f"\nCalibration report saved to {report_path}")


if __name__ == "__main__":
    main()
