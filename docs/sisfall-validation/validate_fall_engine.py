"""
ElderCare Guardian — FallDetectionEngine validation against SisFall dataset.

Usage:
    python3 validate_fall_engine.py --data-dir /path/to/SisFall \
        --out report.md

The SisFall dataset is available at:
    https://www.researchgate.net/publication/276271186_SisFall_Dataset

Expected directory layout:
    SisFall/
        A/   # Activities of Daily Living (ADL) — non-fall
            SA01A01.txt, SA01A02.txt, ...
        D/   # Falls
            SA01D01.txt, SA01D02.txt, ...

File format (SisFall default sensor setup — ADXL345 at waist):
    Sample,ax,ay,az,gx,gy,gz,...  (CSV with header)
or plain whitespace-delimited columns without header.

The script reimplements FallDetectionEngine's three-phase temporal-window
algorithm in pure Python so that the validation can run on a developer
workstation without Android tooling.

Usage:
    -h          Help
    --data-dir  Path to SisFall root (containing A/ and D/ subdirs)
    --out       Output markdown calibration report path
    --thresholds Comma-separated spike thresholds to sweep (default: 14.7,17.2,19.6,22.0,24.5)
    --stillness Comma-separated stillness thresholds to sweep (default: 3.0,4.0,5.0)
    --min-samples Minimum consecutive spike samples (default: 2)
    --stillness-samples Minimum consecutive still samples (default: 3)
    --window-samples Spike-to-stillness window (default: 5)
"""

import argparse
import csv
import math
import os
import sys
from collections import deque
from dataclasses import dataclass, field
from typing import Optional


# ── FallDetectionEngine reimplementation in Python ──────────────────────────

@dataclass
class FallAssessment:
    risk_status: str  # Low | Medium | High
    risk_score: float
    accel_magnitude: float


class DetectionEngine:
    """Mirrors com.eldercareguardian.ml.FallDetectionEngine."""

    def __init__(
        self,
        spike_threshold: float = 19.6,
        stillness_threshold: float = 4.0,
        spike_min_samples: int = 2,
        stillness_min_samples: int = 3,
        spike_to_stillness_window: int = 5,
        window_size: int = 20,
    ):
        self.spike_threshold = spike_threshold
        self.stillness_threshold = stillness_threshold
        self.spike_min_samples = spike_min_samples
        self.stillness_min_samples = stillness_min_samples
        self.spike_to_stillness_window = spike_to_stillness_window
        self.window_size = window_size

        self.magnitude_window: deque[float] = deque(maxlen=window_size)
        self.samples_after_spike: int = 2**31 - 1
        self.consecutive_spike_samples: int = 0
        self.consecutive_still_samples: int = 0
        self.spike_detected: bool = False

    def assess(self, ax: float, ay: float, az: float) -> FallAssessment:
        mag = math.sqrt(ax * ax + ay * ay + az * az)
        self.magnitude_window.append(mag)

        # Phase B: Impact spike detection
        if mag > self.spike_threshold:
            self.consecutive_spike_samples += 1
            self.consecutive_still_samples = 0
        else:
            self.consecutive_spike_samples = 0

        if self.consecutive_spike_samples >= self.spike_min_samples and not self.spike_detected:
            self.spike_detected = True
            self.samples_after_spike = 0

        if self.spike_detected:
            self.samples_after_spike += 1
            if self.samples_after_spike > self.spike_to_stillness_window:
                self.spike_detected = False
                self.consecutive_still_samples = 0

        # Phase C: Post-impact stillness detection
        if mag < self.stillness_threshold:
            self.consecutive_still_samples += 1
        else:
            self.consecutive_still_samples = 0

        # Score calculation
        if self.spike_detected and self.consecutive_still_samples >= self.stillness_min_samples:
            score = 0.90
        elif self.spike_detected:
            score = 0.45
        elif self.consecutive_still_samples >= self.stillness_min_samples * 2 and mag < self.stillness_threshold:
            score = 0.20
        else:
            score = 0.05

        status = "High" if score >= 0.80 else ("Medium" if score >= 0.35 else "Low")

        # Reset after confirmed High
        if status == "High":
            self.spike_detected = False
            self.consecutive_still_samples = 0
            self.consecutive_spike_samples = 0
            self.samples_after_spike = 2**31 - 1

        return FallAssessment(status, score, mag)

    def reset(self):
        self.magnitude_window.clear()
        self.spike_detected = False
        self.consecutive_spike_samples = 0
        self.consecutive_still_samples = 0
        self.samples_after_spike = 2**31 - 1


# ── CSV Parsing ──────────────────────────────────────────────────────────────

# SisFall sensor constants
ADXL345_RANGE_G = 16.0
ADXL345_RESOLUTION_BITS = 13
ADXL345_SCALE = (2.0 * ADXL345_RANGE_G) / (2.0 ** ADXL345_RESOLUTION_BITS)  # g per bit

MMA8451Q_RANGE_G = 8.0
MMA8451Q_RESOLUTION_BITS = 14
MMA8451Q_SCALE = (2.0 * MMA8451Q_RANGE_G) / (2.0 ** MMA8451Q_RESOLUTION_BITS)

ITG3200_RANGE_DPS = 2000.0
ITG3200_RESOLUTION_BITS = 16
ITG3200_SCALE = (2.0 * ITG3200_RANGE_DPS) / (2.0 ** ITG3200_RESOLUTION_BITS)


def parse_sisfall_line(line: str, use_wrist: bool = False) -> Optional[tuple[float, float, float]]:
    """Parse a single line from a SisFall file.
    
    Returns (ax, ay, az) in m/s² from the selected sensor.
    
    SisFall columns (9 columns, semicolon- or comma-delimited, no header):
    0: ax_waist (ADXL345) - bits
    1: ay_waist (ADXL345) - bits  
    2: az_waist (ADXL345) - bits
    3: gx_waist (ITG3200) - bits
    4: gy_waist (ITG3200) - bits
    5: gz_waist (ITG3200) - bits
    6: ax_wrist (MMA8451Q) - bits
    7: ay_wrist (MMA8451Q) - bits
    8: az_wrist (MMA8451Q) - bits
    """
    line = line.strip()
    if not line:
        return None
    
    # Try semicolon first
    parts = line.split(";")
    if len(parts) < 9:
        # Try comma
        parts = line.split(",")
    if len(parts) < 9:
        # Try whitespace
        parts = line.split()
    if len(parts) < 9:
        return None
    
    try:
        # Strip trailing semicolon from last element if comma-delimited
        if len(parts) >= 9 and parts[8].endswith(";"):
            parts[8] = parts[8].rstrip(";")
        
        if use_wrist:
            # MMA8451Q wrist sensor columns 6,7,8
            ax_bits = float(parts[6].strip())
            ay_bits = float(parts[7].strip())
            az_bits = float(parts[8].strip())
            scale = MMA8451Q_SCALE
        else:
            # ADXL345 waist sensor columns 0,1,2
            ax_bits = float(parts[0].strip())
            ay_bits = float(parts[1].strip())
            az_bits = float(parts[2].strip())
            scale = ADXL345_SCALE
        
        ax = ax_bits * scale * 9.81
        ay = ay_bits * scale * 9.81
        az = az_bits * scale * 9.81
    except (ValueError, IndexError):
        return None
    return (ax, ay, az)


def load_sisfall_sequence(filepath: str, use_wrist: bool = False) -> list[tuple[float, float, float]]:
    """Load a sequence of (ax, ay, az) samples from a SisFall file."""
    samples: list[tuple[float, float, float]] = []
    with open(filepath, "r") as f:
        for line in f:
            result = parse_sisfall_line(line, use_wrist=use_wrist)
            if result is not None:
                samples.append(result)
    return samples


def is_fall_file(filename: str) -> bool:
    """Determine if a SisFall file is a fall (F prefix) or ADL (D prefix)."""
    # File format: <CODE>_<SUBJECT>_<TRIAL>.txt
    # F01-F15 = falls, D01-D19 = ADL
    code = filename.split("_")[0]
    return code.startswith("F")


def collect_sisfall_files(data_dir: str, elderly_only: bool = False) -> tuple[list[str], list[str]]:
    """Collect fall and ADL file paths from SisFall dataset.
    
    Expected structure: data_dir/SA01/, data_dir/SE01/, etc.
    """
    fall_files: list[str] = []
    adl_files: list[str] = []
    
    for subject_dir in sorted(os.listdir(data_dir)):
        subject_path = os.path.join(data_dir, subject_dir)
        if not os.path.isdir(subject_path):
            continue
        if subject_dir.startswith("."):
            continue
        if elderly_only and not subject_dir.startswith("SE"):
            continue
        if not elderly_only and not (subject_dir.startswith("SA") or subject_dir.startswith("SE")):
            continue
            
        for fname in sorted(os.listdir(subject_path)):
            if not fname.endswith(".txt") or fname.startswith("."):
                continue
            fpath = os.path.join(subject_path, fname)
            if is_fall_file(fname):
                fall_files.append(fpath)
            else:
                adl_files.append(fpath)
    
    return fall_files, adl_files


# ── Classification ──────────────────────────────────────────────────────────

def classify_sequence(
    samples: list[tuple[float, float, float]],
    engine: DetectionEngine,
    sample_rate_hz: float = 200.0,
) -> bool:
    """Run a sequence through the engine. Returns True if fall detected.
    
    SisFall data includes gravity, but the Kotlin engine operates on
    gravity-compensated data from the BLE sensor (ESP32 firmware subtracts
    gravity before transmission). We subtract gravity magnitude from the
    raw acceleration before feeding to the engine.
    
    Downsample by taking max magnitude in 100ms windows (20 samples at 200 Hz).
    This matches the ~1 Hz BLE notify rate while preserving peak magnitudes.
    """
    engine.reset()
    
    GRAVITY = 9.81
    
    window_size = max(1, int(sample_rate_hz / 10.0))  # 100ms windows
    for i in range(0, len(samples), window_size):
        window = samples[i:i+window_size]
        if not window:
            continue
        # Take the sample with maximum magnitude in this window
        max_sample = max(window, key=lambda s: s[0]*s[0] + s[1]*s[1] + s[2]*s[2])
        ax, ay, az = max_sample
        
        # Subtract gravity: compute raw magnitude then subtract 1g
        # This approximates the gravity-compensated output from the BLE sensor
        raw_mag = math.sqrt(ax*ax + ay*ay + az*az)
        compensated_mag = abs(raw_mag - GRAVITY)
        
        # Create a synthetic gravity-compensated vector along the original direction
        if raw_mag > 0.001:
            scale = compensated_mag / raw_mag
            ax_comp = ax * scale
            ay_comp = ay * scale
            az_comp = az * scale
        else:
            ax_comp, ay_comp, az_comp = 0.0, 0.0, 0.0
        
        result = engine.assess(ax_comp, ay_comp, az_comp)
        if result.risk_status == "High":
            return True
    return False


@dataclass
class ValidationResult:
    true_positives: int = 0
    false_positives: int = 0
    true_negatives: int = 0
    false_negatives: int = 0
    total_fall: int = 0
    total_adl: int = 0
    total_sequences: int = 0

    @property
    def sensitivity(self) -> float:
        denom = self.true_positives + self.false_negatives
        return self.true_positives / denom if denom > 0 else 0.0

    @property
    def specificity(self) -> float:
        denom = self.true_negatives + self.false_positives
        return self.true_negatives / denom if denom > 0 else 0.0

    @property
    def precision(self) -> float:
        denom = self.true_positives + self.false_positives
        return self.true_positives / denom if denom > 0 else 0.0

    @property
    def f1_score(self) -> float:
        num = 2 * self.true_positives
        denom = 2 * self.true_positives + self.false_positives + self.false_negatives
        return num / denom if denom > 0 else 0.0


def validate_dataset(
    data_dir: str,
    spike_threshold: float,
    stillness_threshold: float,
    spike_min_samples: int,
    stillness_min_samples: int,
    window_samples: int,
    max_files: int = 100,
    elderly_only: bool = False,
    use_wrist: bool = False,
) -> ValidationResult:
    """Run validation on SisFall dataset with given thresholds."""
    result = ValidationResult()
    
    fall_files, adl_files = collect_sisfall_files(data_dir, elderly_only=elderly_only)
    
    # Limit files for faster validation
    fall_files = fall_files[:max_files]
    adl_files = adl_files[:max_files]
    
    engine = DetectionEngine(
        spike_threshold=spike_threshold,
        stillness_threshold=stillness_threshold,
        spike_min_samples=spike_min_samples,
        stillness_min_samples=stillness_min_samples,
        spike_to_stillness_window=window_samples,
    )
    
    # Process fall sequences
    for fpath in fall_files:
        samples = load_sisfall_sequence(fpath, use_wrist=use_wrist)
        if len(samples) < 10:
            continue
        result.total_sequences += 1
        result.total_fall += 1
        detected = classify_sequence(samples, engine)
        if detected:
            result.true_positives += 1
        else:
            result.false_negatives += 1
    
    # Process ADL sequences
    for fpath in adl_files:
        samples = load_sisfall_sequence(fpath, use_wrist=use_wrist)
        if len(samples) < 10:
            continue
        result.total_sequences += 1
        result.total_adl += 1
        detected = classify_sequence(samples, engine)
        if detected:
            result.false_positives += 1
        else:
            result.true_negatives += 1
    
    return result


# ── CLI ──────────────────────────────────────────────────────────────────────

def sweep_thresholds(args):
    """Sweep through threshold combinations and produce a report."""
    spike_vals = [float(x) for x in args.thresholds.split(",")]
    still_vals = [float(x) for x in args.stillness.split(",")]
    
    # Output markdown report
    lines: list[str] = []
    lines.append(f"# Fall Detection Calibration Report")
    lines.append(f"")
    lines.append(f"**Generated:** {__import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M')}")
    lines.append(f"**Dataset:** SisFall ({args.data_dir})")
    lines.append(f"**Spike threshold sweep:** {', '.join(f'{v} m/s²' for v in spike_vals)}")
    lines.append(f"**Stillness threshold sweep:** {', '.join(f'{v} m/s²' for v in still_vals)}")
    lines.append(f"**Spike min samples:** {args.min_samples}")
    lines.append(f"**Stillness min samples:** {args.stillness_samples}")
    lines.append(f"**Spike-to-stillness window:** {args.window_samples}")
    lines.append(f"")
    lines.append(f"## Results Matrix")
    lines.append(f"")
    lines.append(f"| Spike (m/s²) | Stillness (m/s²) | Sensitivity | Specificity | Precision | F1 Score | TP | FP | TN | FN |")
    lines.append(f"|---|---|---|---|---|---|---|---|---|---|")
    
    best_f1 = 0.0
    best_result: Optional[ValidationResult] = None
    best_params = ("", "")
    
    for st in still_vals:
        for sp in spike_vals:
            result = validate_dataset(
                args.data_dir,
                spike_threshold=sp,
                stillness_threshold=st,
                spike_min_samples=args.min_samples,
                stillness_min_samples=args.stillness_samples,
                window_samples=args.window_samples,
                max_files=200,
                elderly_only=args.elderly_only,
                use_wrist=args.use_wrist,
            )
            lines.append(
                f"| {sp:.1f} | {st:.1f} | "
                f"{result.sensitivity:.3f} | {result.specificity:.3f} | "
                f"{result.precision:.3f} | {result.f1_score:.3f} | "
                f"{result.true_positives} | {result.false_positives} | "
                f"{result.true_negatives} | {result.false_negatives} |"
            )
            if result.f1_score > best_f1:
                best_f1 = result.f1_score
                best_result = result
                best_params = (f"{sp:.1f}", f"{st:.1f}")
    
    lines.append(f"")
    lines.append(f"## Recommended Configuration")
    lines.append(f"")
    if best_result and best_result.total_sequences > 0:
        lines.append(f"- **Spike threshold:** {best_params[0]} m/s² ({float(best_params[0]) / 9.81:.2f}g)")
        lines.append(f"- **Stillness threshold:** {best_params[1]} m/s²")
        lines.append(f"- **Sensitivity:** {best_result.sensitivity:.3f}")
        lines.append(f"- **Specificity:** {best_result.specificity:.3f}")
        lines.append(f"- **Precision:** {best_result.precision:.3f}")
        lines.append(f"- **F1 Score:** {best_result.f1_score:.3f}")
        lines.append(f"- **Total sequences evaluated:** {best_result.total_sequences}")
        lines.append(f"")
        lines.append(f"### Thresholds to apply to FallDetectionEngine.kt")
        lines.append(f"")
        lines.append(f"```kotlin")
        lines.append(f"private const val FALL_SPIKE_THRESHOLD = {best_params[0]}f")
        lines.append(f"private const val FALL_STILLNESS_THRESHOLD = {best_params[1]}f")
        lines.append(f"```")
    else:
        lines.append(f"No sequences found in dataset directories. Check --data-dir path.")
        lines.append(f"Expected structure: <data-dir>/D/ for falls, <data-dir>/A/ for ADL.")
    
    report = "\n".join(lines)
    
    if args.out:
        with open(args.out, "w") as f:
            f.write(report)
        print(f"Calibration report written to {args.out}")
    else:
        print(report)
    
    print(f"\nBest F1: {best_f1:.3f}")


def main():
    parser = argparse.ArgumentParser(description="SisFall Validation Runner")
    parser.add_argument("--data-dir", required=True, help="Path to SisFall dataset root")
    parser.add_argument("--out", default=None, help="Output markdown report path")
    parser.add_argument(
        "--thresholds",
        default="14.7,17.2,19.6,22.0,24.5",
        help="Comma-separated spike thresholds (m/s²) to sweep",
    )
    parser.add_argument(
        "--stillness",
        default="3.0,4.0,5.0",
        help="Comma-separated stillness thresholds (m/s²) to sweep",
    )
    parser.add_argument("--min-samples", type=int, default=2, help="Spike min consecutive samples")
    parser.add_argument("--stillness-samples", type=int, default=3, help="Stillness min consecutive samples")
    parser.add_argument("--window-samples", type=int, default=5, help="Spike-to-stillness window")
    parser.add_argument("--elderly-only", action="store_true", help="Only use SE (elderly) subjects")
    parser.add_argument("--use-wrist", action="store_true", help="Use wrist sensor (MMA8451Q) instead of waist (ADXL345)")
    
    args = parser.parse_args()
    
    if not os.path.isdir(args.data_dir):
        print(f"Error: --data-dir '{args.data_dir}' is not a directory", file=sys.stderr)
        sys.exit(1)
    
    sweep_thresholds(args)


if __name__ == "__main__":
    main()
