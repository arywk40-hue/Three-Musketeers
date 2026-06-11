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

def parse_sample(line: str, has_header: bool = False, delimiter: str = ",") -> Optional[tuple[float, float, float]]:
    """Parse a single line from a SisFall CSV file.
    
    Returns (ax, ay, az) in m/s², or None if the line should be skipped.
    
    SisFall default sensor setup columns (ADXL345 at waist, ±16g):
        Sample, ax_waist(g), ay_waist(g), az_waist(g),
        gx_waist(°/s), gy_waist(°/s), gz_waist(°/s),
        ax_wrist(g), ay_wrist(g), az_wrist(g),
        gx_wrist(°/s), gy_wrist(°/s), gz_wrist(°/s),
        ...
    
    We use the **waist** ADXL345 columns (indices 1, 2, 3) since that
    matches the sensor placement our firmware targets.
    """
    line = line.strip()
    if not line:
        return None
    parts = line.split(delimiter)
    if len(parts) < 4:
        return None
    if has_header and parts[0].strip().lower() in ("sample", "timestamp", "time"):
        return None
    try:
        ax = float(parts[1]) * 9.81  # Convert g → m/s²
        ay = float(parts[2]) * 9.81
        az = float(parts[3]) * 9.81
    except (ValueError, IndexError):
        return None
    return (ax, ay, az)


def detect_file_format(filepath: str) -> tuple[bool, str]:
    """Detect whether the file has a header row and the delimiter."""
    with open(filepath, "r") as f:
        first = f.readline().strip()
        second = f.readline().strip()
    
    # Check if first line looks like a header
    header_keywords = ("sample", "timestamp", "time", "ax", "ay", "az")
    first_lower = first.lower().split(",")[0].strip()
    has_header = any(kw in first_lower for kw in header_keywords)
    
    # Detect delimiter
    comma_count = first.count(",")
    space_count = first.count(" ")
    delim = "," if comma_count > space_count else None
    
    return has_header, delim


def load_sequence(filepath: str) -> list[tuple[float, float, float]]:
    """Load a sequence of (ax, ay, az) samples from a SisFall CSV file."""
    has_header, delim = detect_file_format(filepath)
    samples: list[tuple[float, float, float]] = []
    with open(filepath, "r") as f:
        for line in f:
            result = parse_sample(line, has_header, ",")
            if result is not None:
                samples.append(result)
            elif delim != ",":
                # Try whitespace delimiter
                result = parse_sample(line, has_header, None)
                if result is not None:
                    samples.append(result)
    return samples


# ── Classification ──────────────────────────────────────────────────────────

def classify_sequence(
    samples: list[tuple[float, float, float]],
    engine: DetectionEngine,
    sample_rate_hz: float = 200.0,
) -> bool:
    """Run a sequence through the engine. Returns True if fall detected."""
    engine.reset()
    
    # Decimate to ~1 Hz to match BLE notify rate, or use sliding window
    # If sampling at 200 Hz, take every Nth sample where N = sample_rate_hz
    decimation = max(1, int(sample_rate_hz / 1.0))
    
    for i, (ax, ay, az) in enumerate(samples):
        if i % decimation == 0:
            result = engine.assess(ax, ay, az)
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
) -> ValidationResult:
    """Run validation on SisFall dataset with given thresholds."""
    result = ValidationResult()
    
    fall_dir = os.path.join(data_dir, "D")
    adl_dir = os.path.join(data_dir, "A")
    
    engine = DetectionEngine(
        spike_threshold=spike_threshold,
        stillness_threshold=stillness_threshold,
        spike_min_samples=spike_min_samples,
        stillness_min_samples=stillness_min_samples,
        spike_to_stillness_window=window_samples,
    )
    
    # Process fall sequences (D/)
    if os.path.isdir(fall_dir):
        for fname in sorted(os.listdir(fall_dir)):
            fpath = os.path.join(fall_dir, fname)
            if not os.path.isfile(fpath) or fname.startswith("."):
                continue
            samples = load_sequence(fpath)
            if len(samples) < 10:
                continue
            result.total_sequences += 1
            result.total_fall += 1
            detected = classify_sequence(samples, engine)
            if detected:
                result.true_positives += 1
            else:
                result.false_negatives += 1
    
    # Process ADL sequences (A/)
    if os.path.isdir(adl_dir):
        for fname in sorted(os.listdir(adl_dir)):
            fpath = os.path.join(adl_dir, fname)
            if not os.path.isfile(fpath) or fname.startswith("."):
                continue
            samples = load_sequence(fpath)
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
    
    args = parser.parse_args()
    
    if not os.path.isdir(args.data_dir):
        print(f"Error: --data-dir '{args.data_dir}' is not a directory", file=sys.stderr)
        sys.exit(1)
    
    sweep_thresholds(args)


if __name__ == "__main__":
    main()
