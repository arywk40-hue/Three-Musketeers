#!/usr/bin/env python3
"""
Download SisFall dataset from multiple sources.

Priority:
  1. Google Drive (gdown) — file ID: 1-E-TLd5_J-DDWZXkuYL-moMpoezlMn4Z
  2. Manual download — user provides path
  3. Kaggle — requires kagglehub

Usage:
  python download_sisfall.py --out ./data
  python download_sisfall.py --out ./data --manual /path/to/SisFall.zip
"""

import argparse
import os
import sys
import zipfile
from pathlib import Path
from urllib.request import urlopen

from config import SISFALL_URLS

SISFALL_ZIP = "SisFall.zip"


def download_gdrive(file_id: str, out_path: str) -> bool:
    try:
        import gdown
        url = f"https://drive.google.com/uc?id={file_id}"
        print(f"Downloading from Google Drive ({url})...")
        gdown.download(url, out_path, quiet=False)
        return os.path.exists(out_path) and os.path.getsize(out_path) > 1_000_000
    except Exception as e:
        print(f"Google Drive download failed: {e}")
        return False


def download_kaggle(dataset: str, out_dir: str) -> bool:
    try:
        import kagglehub
        print(f"Downloading from Kaggle ({dataset})...")
        path = kagglehub.dataset_download(dataset)
        print(f"Kaggle download at: {path}")
        # Copy files from kaggle cache to our data dir
        import shutil
        for item in os.listdir(path):
            src = os.path.join(path, item)
            dst = os.path.join(out_dir, item)
            if os.path.isdir(src):
                shutil.copytree(src, dst, dirs_exist_ok=True)
            else:
                shutil.copy2(src, dst)
        return True
    except ImportError:
        print("kagglehub not installed. Install with: pip install kagglehub")
        return False
    except Exception as e:
        print(f"Kaggle download failed: {e}")
        return False


def extract_zip(zip_path: str, out_dir: str) -> bool:
    print(f"Extracting {zip_path} to {out_dir}...")
    try:
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(out_dir)
        return True
    except Exception as e:
        print(f"Extraction failed: {e}")
        return False


def verify_sisfall_structure(data_dir: str) -> bool:
    """Check expected A/ and D/ subdirectories exist."""
    a_dir = os.path.join(data_dir, "A")
    d_dir = os.path.join(data_dir, "D")
    has_a = os.path.isdir(a_dir) and len(os.listdir(a_dir)) > 0
    has_d = os.path.isdir(d_dir) and len(os.listdir(d_dir)) > 0
    if has_a and has_d:
        a_count = len([f for f in os.listdir(a_dir) if f.endswith(".txt")])
        d_count = len([f for f in os.listdir(d_dir) if f.endswith(".txt")])
        print(f"Found SisFall: {a_count} ADL + {d_count} fall sequences")
        return True
    else:
        print(f"A/ exists: {has_a}, D/ exists: {has_d}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Download SisFall dataset")
    parser.add_argument("--out", default="data", help="Output directory")
    parser.add_argument("--manual", default=None, help="Path to manually-downloaded SisFall.zip")
    args = parser.parse_args()

    out_dir = os.path.abspath(args.out)
    os.makedirs(out_dir, exist_ok=True)

    # If manual path provided, use it
    if args.manual:
        print(f"Using manually-downloaded file: {args.manual}")
        if os.path.isdir(args.manual):
            # Already extracted
            if verify_sisfall_structure(args.manual):
                print("Dataset already available at", args.manual)
                return
        elif args.manual.endswith(".zip"):
            extract_zip(args.manual, out_dir)
            if verify_sisfall_structure(out_dir):
                print("Dataset ready at", out_dir)
                return

    # Check if already present
    data_dirs_to_check = [
        os.path.join(out_dir, "SisFall"),
        out_dir,
    ]
    for d in data_dirs_to_check:
        if verify_sisfall_structure(d):
            print("Dataset already available. Skipping download.")
            return

    # Try Google Drive
    zip_path = os.path.join(out_dir, SISFALL_ZIP)
    if download_gdrive(SISFALL_URLS["gdrive"], zip_path):
        extract_zip(zip_path, out_dir)
        if verify_sisfall_structure(out_dir):
            print("Dataset ready at", out_dir)
            os.remove(zip_path)
            return

    # Try Kaggle
    dataset_dir = os.path.join(out_dir, "SisFall")
    os.makedirs(dataset_dir, exist_ok=True)
    if download_kaggle(SISFALL_URLS["kaggle"], dataset_dir):
        if verify_sisfall_structure(dataset_dir):
            print("Dataset ready at", dataset_dir)
            return

    print("\nCould not download SisFall automatically.")
    print("Manual options:")
    print("  1. Download from Kaggle: https://www.kaggle.com/datasets/nvnikhil0001/sis-fall-original-dataset")
    print("  2. Download the zip and run: python download_sisfall.py --manual /path/to/SisFall.zip")
    print("  3. Extract the dataset manually into a directory with A/ and D/ subdirs.")
    sys.exit(1)


if __name__ == "__main__":
    main()
