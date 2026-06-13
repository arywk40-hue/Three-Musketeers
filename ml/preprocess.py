#!/usr/bin/env python3
"""
Preprocess SisFall raw CSV files into windowed numpy arrays.

SisFall directory layout:
  SisFall_dataset/
    SA01/  (young adult)
      D01_SA01_R01.txt  (ADL: walking slowly)
      F01_SA01_R01.txt  (fall: forward slip)
    SE01/  (elderly)
      ...

File columns (9 values, semicolon-terminated):
  ADXL345_x, ADXL345_y, ADXL345_z, ITG3200_x, ITG3200_y, ITG3200_z,
  MMA8451Q_x, MMA8451Q_y, MMA8451Q_z

We use only ADXL345 (3-axis accelerometer, cols 0-2).
ADXL345_z may be left-justified 13-bit in 16-bit; we detect and correct this.

Usage:
  python preprocess.py --data-dir data/SisFall_dataset --out data/processed/
"""

import argparse
import os
import sys

import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from tqdm import tqdm

from config import (
    SAMPLE_RATE,
    WINDOW_SIZE,
    STRIDE,
    NUM_AXES,
    TEST_SPLIT,
    RANDOM_SEED,
)

COLUMN_NAMES = [
    "ADXL345_x", "ADXL345_y", "ADXL345_z",
    "ITG3200_x", "ITG3200_y", "ITG3200_z",
    "MMA8451Q_x", "MMA8451Q_y", "MMA8451Q_z",
]

ACCEL_COLS = ["ADXL345_x", "ADXL345_y", "ADXL345_z"]

# ADXL345 at ±16g, 13-bit (right-justified): 32g / 8192 = 0.00390625 g/LSB
ADXL345_LSB_TO_G = 32.0 / 8192.0
G_TO_MS2 = 9.81


def read_sisfall_file(filepath: str) -> np.ndarray:
    """Read a SisFall .txt file and return ADXL345 (N, 3) in m/s²."""
    df = pd.read_csv(filepath, header=None, names=COLUMN_NAMES, sep=",",
                     dtype=str, skipinitialspace=True)
    df[COLUMN_NAMES] = df[COLUMN_NAMES].apply(lambda x: x.str.strip().str.rstrip(";"))
    for col in COLUMN_NAMES:
        df[col] = pd.to_numeric(df[col], errors="coerce")
    df = df.dropna()

    accel = df[ACCEL_COLS].values.astype(np.float64)

    # Detect left-justified axis: if max absolute value > 4096, shift right by 3
    for i in range(3):
        col_max = np.max(np.abs(accel[:, i]))
        if col_max > 6000:
            accel[:, i] = accel[:, i] / 8.0

    # Convert LSB → m/s²
    accel *= ADXL345_LSB_TO_G * G_TO_MS2
    return accel.astype(np.float32)


def is_fall_file(filename: str) -> bool:
    """Return True if filename starts with F (fall activity)."""
    return filename.startswith("F")


def create_windows(data: np.ndarray) -> np.ndarray:
    """Sliding window. Returns (num_windows, window_size, num_axes)."""
    windows: list[np.ndarray] = []
    for start in range(0, len(data) - WINDOW_SIZE + 1, STRIDE):
        windows.append(data[start:start + WINDOW_SIZE])
    return np.stack(windows) if windows else np.empty((0, WINDOW_SIZE, NUM_AXES))


def normalise_windows(windows: np.ndarray) -> np.ndarray:
    """Z-score normalise per axis."""
    for i in range(windows.shape[2]):
        mean = windows[:, :, i].mean()
        std = windows[:, :, i].std()
        if std > 0:
            windows[:, :, i] = (windows[:, :, i] - mean) / std
    return windows


def main():
    parser = argparse.ArgumentParser(description="Preprocess SisFall dataset")
    parser.add_argument("--data-dir", required=True,
                        help="SisFall root (containing SA01/, SE01/, ... subdirs)")
    parser.add_argument("--out", default="data/processed", help="Output directory")
    args = parser.parse_args()

    data_dir = os.path.abspath(args.data_dir)
    out_dir = os.path.abspath(args.out)
    os.makedirs(out_dir, exist_ok=True)

    # Collect all .txt files from all subject directories
    subject_dirs = sorted([
        d for d in os.listdir(data_dir)
        if os.path.isdir(os.path.join(data_dir, d))
        and d.startswith(("SA", "SE"))
    ])

    if not subject_dirs:
        print(f"Error: no subject directories (SA*, SE*) found in {data_dir}",
              file=sys.stderr)
        sys.exit(1)

    all_windows: list[np.ndarray] = []
    all_labels: list[int] = []
    total_sequences = 0
    skipped_sequences = 0

    print(f"Found {len(subject_dirs)} subject directories in {data_dir}")

    for subj in subject_dirs:
        subj_dir = os.path.join(data_dir, subj)
        txt_files = sorted([
            f for f in os.listdir(subj_dir)
            if f.endswith(".txt") and not f.startswith(".")
        ])
        if not txt_files:
            continue

        for fname in tqdm(txt_files, desc=subj, leave=False):
            fpath = os.path.join(subj_dir, fname)
            try:
                data = read_sisfall_file(fpath)
            except Exception as e:
                skipped_sequences += 1
                continue

            if len(data) < WINDOW_SIZE:
                skipped_sequences += 1
                continue

            windows = create_windows(data)
            if len(windows) == 0:
                skipped_sequences += 1
                continue

            total_sequences += 1
            all_windows.append(windows)
            label = 1 if is_fall_file(fname) else 0
            all_labels.extend([label] * len(windows))

    if len(all_windows) == 0:
        print("Error: no windows generated. Check dataset format and paths.",
              file=sys.stderr)
        sys.exit(1)

    X = np.concatenate(all_windows, axis=0)
    y = np.array(all_labels, dtype=np.int64)

    print(f"\nTotal sequences processed: {total_sequences}")
    print(f"Skipped sequences: {skipped_sequences}")
    print(f"Total windows: {len(X)}")
    print(f"Window shape: {X.shape[1:]}")

    fall_count = int(y.sum())
    adl_count = len(y) - fall_count
    print(f"Fall windows: {fall_count} ({fall_count/len(y)*100:.1f}%)")
    print(f"ADL windows: {adl_count} ({adl_count/len(y)*100:.1f}%)")

    print("Normalising per axis (z-score)...")
    X = normalise_windows(X)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=TEST_SPLIT, random_state=RANDOM_SEED, stratify=y,
    )

    np.save(os.path.join(out_dir, "X_train.npy"), X_train)
    np.save(os.path.join(out_dir, "y_train.npy"), y_train)
    np.save(os.path.join(out_dir, "X_test.npy"), X_test)
    np.save(os.path.join(out_dir, "y_test.npy"), y_test)

    print(f"\nSaved to {out_dir}:")
    print(f"  X_train: {X_train.shape}")
    print(f"  y_train: {y_train.shape}")
    print(f"  X_test:  {X_test.shape}")
    print(f"  y_test:  {y_test.shape}")


if __name__ == "__main__":
    main()
