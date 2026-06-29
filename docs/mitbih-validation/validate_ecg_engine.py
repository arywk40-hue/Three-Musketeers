#!/usr/bin/env python3
"""
MIT-BIH ECG Validation Harness for EcgAnomalyDetector
=======================================================
Reimplements the Kotlin EcgAnomalyDetector / HeartRateExtractor logic in pure
Python and evaluates it against MIT-BIH Arrhythmia DB + AF Database.

Requirements
------------
    pip install wfdb numpy

Download datasets (one-time, ~300 MB):
    python3 -c "import wfdb; wfdb.dl_database('mitdb', dl_dir='ml/data/mitdb')"
    python3 -c "import wfdb; wfdb.dl_database('afdb',  dl_dir='ml/data/afdb')"

Usage
-----
    python3 docs/mitbih-validation/validate_ecg_engine.py \\
        --mitdb   ml/data/mitdb \\
        --afdb    ml/data/afdb \\
        --out     docs/mitbih-validation/ecg-validation-report.md

Pilot safety requirement: AFib recall (sensitivity) >= 0.80
"""

import argparse
import math
import os
import sys
from pathlib import Path
from typing import List, Optional, Tuple

# ─── Python reimplementation of HeartRateExtractor ─────────────────────────

SAMPLE_RATE_HZ = 360  # MIT-BIH native rate
REFRACTORY_MS = 200
DEFAULT_THRESHOLD = 0.55
ADAPTIVE_FRACTION = 0.4
BASELINE_WINDOW_SEC = 0.5
WINDOW_SAMPLES = 256  # match Kotlin (1 second of synthetic ECG at 256 Hz)


def subtract_baseline(samples: List[float], window: int) -> List[float]:
    out = []
    s = 0.0
    for i, v in enumerate(samples):
        s += v
        if i >= window:
            s -= samples[i - window]
        w = i + 1 if i < window else window
        out.append(v - s / w)
    return out


def extract_rr_intervals(
    ecg: List[float],
    sample_rate: int = SAMPLE_RATE_HZ,
    threshold: float = DEFAULT_THRESHOLD,
    refractory_ms: int = REFRACTORY_MS,
) -> Tuple[List[int], Optional[int]]:
    """Returns (rr_intervals_ms, mean_hr_bpm)."""
    if len(ecg) < sample_rate // 4:
        return [], None

    refractory_samples = (refractory_ms * sample_rate) // 1000
    step_ms = 1000.0 / sample_rate
    baseline_window = max(1, int(BASELINE_WINDOW_SEC * sample_rate))

    centered = subtract_baseline(ecg, baseline_window)
    max_c = max(centered) if centered else 0.0
    adaptive_threshold = max(threshold, max_c * ADAPTIVE_FRACTION)

    rr_intervals: List[int] = []
    last_peak = -refractory_samples
    prev = float("-inf")

    for i, v in enumerate(centered):
        is_rising = v > adaptive_threshold and v >= prev
        is_last = i + 1 >= len(centered)
        is_peak = is_rising and not is_last and centered[i + 1] < v
        past_refractory = (i - last_peak) >= refractory_samples

        if is_peak and past_refractory:
            if last_peak >= 0:
                interval_ms = int((i - last_peak) * step_ms)
                if 250 <= interval_ms <= 2000:
                    rr_intervals.append(interval_ms)
            last_peak = i
        prev = v

    if not rr_intervals:
        return [], None

    mean_interval = sum(rr_intervals) / len(rr_intervals)
    mean_hr = int(60_000 / mean_interval) if mean_interval > 0 else None
    return rr_intervals, mean_hr


def rmssd(rr: List[int]) -> Optional[int]:
    if len(rr) < 2:
        return None
    sq_diffs = [(rr[i] - rr[i - 1]) ** 2 for i in range(1, len(rr))]
    return int(math.sqrt(sum(sq_diffs) / len(sq_diffs)))


def rr_irregularity(rr: List[int]) -> float:
    if len(rr) < 2:
        return 0.0
    mean = sum(rr) / len(rr)
    if mean <= 0:
        return 0.0
    mad = sum(abs(v - mean) for v in rr) / len(rr)
    return mad / mean


# ─── Python reimplementation of EcgAnomalyDetector ─────────────────────────

TACHY_THRESHOLD_BPM = 100
BRADY_THRESHOLD_BPM = 50
AFIB_RMSSD_THRESHOLD_MS = 50
AFIB_IRREGULARITY_THRESHOLD = 0.20
MIN_RR_INTERVALS = 4

ECG_NORMAL = "Normal"
ECG_TACHY = "Tachycardia"
ECG_BRADY = "Bradycardia"
ECG_AFIB = "AFib"
ECG_UNKNOWN = "Unknown"


def assess_ecg(ecg_window: List[float], reported_hr: Optional[int] = None) -> str:
    rr, mean_hr = extract_rr_intervals(ecg_window)
    if mean_hr is None:
        mean_hr = reported_hr
    if mean_hr is None:
        return ECG_UNKNOWN

    # Check AFib first — AFib can coexist with tachycardia/bradycardia
    if len(rr) >= MIN_RR_INTERVALS:
        r = rmssd(rr)
        if r is not None:
            irreg = rr_irregularity(rr)
            if r > AFIB_RMSSD_THRESHOLD_MS and irreg > AFIB_IRREGULARITY_THRESHOLD:
                return ECG_AFIB

    # Rate-based classification (AFib ruled out or insufficient data)
    if mean_hr >= TACHY_THRESHOLD_BPM:
        return ECG_TACHY
    if mean_hr <= BRADY_THRESHOLD_BPM:
        return ECG_BRADY
    return ECG_NORMAL


# ─── MIT-BIH annotation helpers ────────────────────────────────────────────

# MIT-BIH rhythm annotation symbols that indicate AFib
AFIB_RHYTHM_LABELS = {"(AFIB", "(AF"}

# Beat annotation symbols for normal and ectopic
NORMAL_BEAT_LABELS = {"N", "L", "R", "e", "j"}
AFIB_BEAT_LABELS = set()  # AFib is rhythm-level in MIT-BIH, not beat-level


def is_afib_record_mitdb(ann) -> bool:
    """Return True if the record has any AFIB rhythm annotation."""
    for symbol in ann.aux_note:
        if symbol.strip("\x00") in AFIB_RHYTHM_LABELS:
            return True
    return False


def get_afib_sample_ranges(ann, signal_length: int):
    """
    Return a list of (start, end) sample index pairs where AFib rhythm is active.
    """
    ranges = []
    in_afib = False
    start = 0
    for i, note in enumerate(ann.aux_note):
        clean = note.strip("\x00")
        if clean in AFIB_RHYTHM_LABELS:
            in_afib = True
            start = ann.sample[i]
        elif clean.startswith("(") and in_afib:
            ranges.append((start, ann.sample[i]))
            in_afib = False
    if in_afib:
        ranges.append((start, signal_length))
    return ranges


# ─── Sliding window evaluation ──────────────────────────────────────────────

def evaluate_record_mitdb(record_path: str, window_size: int = WINDOW_SAMPLES) -> List[Tuple[str, str]]:
    """
    Slide a window over a MIT-BIH record and return (true_label, predicted_label) pairs.
    true_label is 'AFib' or 'Normal'.
    """
    try:
        import wfdb
    except ImportError:
        print("ERROR: wfdb not installed. Run: pip install wfdb", file=sys.stderr)
        sys.exit(1)

    results = []
    rec = wfdb.rdrecord(record_path)
    ann = wfdb.rdann(record_path, "atr")

    # Use lead 0 (MLII in most MIT-BIH records)
    signal = rec.p_signal[:, 0].tolist()
    n = len(signal)

    afib_ranges = get_afib_sample_ranges(ann, n)

    def is_afib_at(sample_start: int, sample_end: int) -> bool:
        # A window is AFib if it overlaps with any AFib range
        for a, b in afib_ranges:
            if sample_start < b and sample_end > a:
                return True
        return False

    step = window_size // 2  # 50% overlap
    for start in range(0, n - window_size, step):
        end = start + window_size
        window = signal[start:end]
        true_label = ECG_AFIB if is_afib_at(start, end) else ECG_NORMAL
        pred_label = assess_ecg(window)
        # Collapse non-AFib predictions to Normal for binary evaluation
        if pred_label != ECG_AFIB:
            pred_label = ECG_NORMAL
        results.append((true_label, pred_label))

    return results


def evaluate_afdb_record(record_path: str, window_size: int = WINDOW_SAMPLES) -> List[Tuple[str, str]]:
    """
    Evaluate an AF Database record. All records in afdb are AFib by default;
    rhythm annotations mark non-AFib segments.
    """
    try:
        import wfdb
    except ImportError:
        print("ERROR: wfdb not installed. Run: pip install wfdb", file=sys.stderr)
        sys.exit(1)

    results = []
    rec = wfdb.rdrecord(record_path)
    ann = wfdb.rdann(record_path, "atr")

    signal = rec.p_signal[:, 0].tolist()
    n = len(signal)

    # In afdb, records start in AFib unless annotated otherwise
    non_afib_ranges = []
    in_normal = False
    start_normal = 0
    for i, note in enumerate(ann.aux_note):
        clean = note.strip("\x00")
        if clean in {"(N", "(SBR", "(B"}:
            in_normal = True
            start_normal = ann.sample[i]
        elif clean in AFIB_RHYTHM_LABELS and in_normal:
            non_afib_ranges.append((start_normal, ann.sample[i]))
            in_normal = False
    if in_normal:
        non_afib_ranges.append((start_normal, n))

    def is_normal_at(sample_start: int, sample_end: int) -> bool:
        for a, b in non_afib_ranges:
            if sample_start < b and sample_end > a:
                return True
        return False

    step = window_size // 2
    for start in range(0, n - window_size, step):
        end = start + window_size
        window = signal[start:end]
        true_label = ECG_NORMAL if is_normal_at(start, end) else ECG_AFIB
        pred_label = assess_ecg(window)
        if pred_label != ECG_AFIB:
            pred_label = ECG_NORMAL
        results.append((true_label, pred_label))

    return results


# ─── Metrics ────────────────────────────────────────────────────────────────

def compute_metrics(results: List[Tuple[str, str]]) -> dict:
    tp = fp = tn = fn = 0
    for true, pred in results:
        if true == ECG_AFIB and pred == ECG_AFIB:
            tp += 1
        elif true == ECG_NORMAL and pred == ECG_AFIB:
            fp += 1
        elif true == ECG_NORMAL and pred == ECG_NORMAL:
            tn += 1
        else:
            fn += 1
    sensitivity = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    specificity = tn / (tn + fp) if (tn + fp) > 0 else 0.0
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    f1 = (2 * precision * sensitivity / (precision + sensitivity)
          if (precision + sensitivity) > 0 else 0.0)
    return {
        "tp": tp, "fp": fp, "tn": tn, "fn": fn,
        "sensitivity": sensitivity, "specificity": specificity,
        "precision": precision, "f1": f1,
        "total": tp + fp + tn + fn,
    }


# ─── Report generation ──────────────────────────────────────────────────────

def write_report(metrics_mitdb: dict, metrics_afdb: dict, metrics_combined: dict,
                 out_path: Optional[str]) -> None:
    from datetime import datetime

    lines = [
        "# ECG Anomaly Detector Validation Report",
        "",
        f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M')}",
        "**Datasets:** MIT-BIH Arrhythmia Database (mitdb) + AF Database (afdb)",
        "**Algorithm:** EcgAnomalyDetector — RMSSD + RR irregularity (Python reimplementation)",
        "**Window:** 256 samples @ 360 Hz ≈ 0.71 s, 50% overlap",
        "**Pilot safety requirement:** AFib sensitivity ≥ 0.80",
        "",
        "---",
        "",
        "## Results",
        "",
        "| Dataset | Sensitivity | Specificity | Precision | F1 | TP | FP | TN | FN | Windows |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]

    for label, m in [("MIT-BIH mitdb", metrics_mitdb), ("AF Database afdb", metrics_afdb), ("Combined", metrics_combined)]:
        lines.append(
            f"| {label} | {m['sensitivity']:.3f} | {m['specificity']:.3f} | "
            f"{m['precision']:.3f} | {m['f1']:.3f} | {m['tp']} | {m['fp']} | "
            f"{m['tn']} | {m['fn']} | {m['total']} |"
        )

    m = metrics_combined
    pilot_pass = m["sensitivity"] >= 0.80
    status_icon = "✅" if pilot_pass else "❌"
    verdict = "PASS" if pilot_pass else "FAIL — sensitivity below 0.80 threshold"

    lines += [
        "",
        "---",
        "",
        "## Pilot Safety Verdict",
        "",
        f"{status_icon} **AFib sensitivity: {m['sensitivity']:.3f}** — {verdict}",
        "",
    ]

    if not pilot_pass:
        lines += [
            "### Remediation",
            "",
            "AFib sensitivity is below the 0.80 pilot safety threshold. Options:",
            "",
            "1. **Lower AFIB_RMSSD_THRESHOLD_MS** (currently 50 ms) — more windows will be flagged as AFib.",
            "2. **Lower AFIB_IRREGULARITY_THRESHOLD** (currently 0.20) — less strict irregularity requirement.",
            "3. **Increase WINDOW_SAMPLES** — more RR intervals per window improves RMSSD stability.",
            "4. **Train TFLite CNN** (see `ml/train_cnn.py`) and replace rule-based engine.",
            "",
            "Re-run this script after each change to verify the new sensitivity.",
            "",
        ]

    lines += [
        "---",
        "",
        "## EcgAnomalyDetector.kt Constants",
        "",
        "```kotlin",
        f"private const val AFIB_RMSSD_THRESHOLD_MS = {AFIB_RMSSD_THRESHOLD_MS}",
        f"private const val AFIB_IRREGULARITY_THRESHOLD = {AFIB_IRREGULARITY_THRESHOLD}f",
        f"private const val MIN_RR_INTERVALS = {MIN_RR_INTERVALS}",
        "```",
        "",
        "---",
        "",
        "*ElderCare Guardian — IIT Mandi · Clinical disclaimer: rule-based screening aid only. Not for diagnostic use.*",
    ]

    report = "\n".join(lines)
    if out_path:
        Path(out_path).parent.mkdir(parents=True, exist_ok=True)
        Path(out_path).write_text(report, encoding="utf-8")
        print(f"Report written to {out_path}")
    else:
        print(report)


# ─── Main ────────────────────────────────────────────────────────────────────

def list_records(data_dir: str) -> List[str]:
    """Return full paths (without extension) to all records in data_dir."""
    p = Path(data_dir)
    # wfdb records are identified by .hea files
    return sorted(str(hea.with_suffix("")) for hea in p.glob("*.hea"))


def main():
    parser = argparse.ArgumentParser(description="Validate EcgAnomalyDetector against MIT-BIH")
    parser.add_argument("--mitdb", default="ml/data/mitdb", help="Path to mitdb directory")
    parser.add_argument("--afdb", default="ml/data/afdb", help="Path to afdb directory")
    parser.add_argument("--out", default=None, help="Output markdown report path")
    parser.add_argument("--limit", type=int, default=None, help="Max records per DB (for quick test)")
    args = parser.parse_args()

    try:
        import wfdb  # noqa: F401
    except ImportError:
        print("ERROR: wfdb not installed.\n  pip install wfdb", file=sys.stderr)
        sys.exit(1)

    # ── MIT-BIH ──
    mitdb_records = list_records(args.mitdb)
    if not mitdb_records:
        print(f"WARNING: No records found in {args.mitdb}. Download with:\n"
              "  python3 -c \"import wfdb; wfdb.dl_database('mitdb', dl_dir='ml/data/mitdb')\"",
              file=sys.stderr)
    if args.limit:
        mitdb_records = mitdb_records[:args.limit]

    mitdb_results: List[Tuple[str, str]] = []
    for rec in mitdb_records:
        print(f"  mitdb: {Path(rec).name}", end="\r", flush=True)
        try:
            mitdb_results.extend(evaluate_record_mitdb(rec))
        except Exception as e:
            print(f"\n  SKIP {rec}: {e}", file=sys.stderr)
    print(f"\nmitdb: {len(mitdb_records)} records, {len(mitdb_results)} windows")

    # ── AF Database ──
    afdb_records = list_records(args.afdb)
    if not afdb_records:
        print(f"WARNING: No records found in {args.afdb}. Download with:\n"
              "  python3 -c \"import wfdb; wfdb.dl_database('afdb', dl_dir='ml/data/afdb')\"",
              file=sys.stderr)
    if args.limit:
        afdb_records = afdb_records[:args.limit]

    afdb_results: List[Tuple[str, str]] = []
    for rec in afdb_records:
        print(f"  afdb: {Path(rec).name}", end="\r", flush=True)
        try:
            afdb_results.extend(evaluate_afdb_record(rec))
        except Exception as e:
            print(f"\n  SKIP {rec}: {e}", file=sys.stderr)
    print(f"\nafdb:  {len(afdb_records)} records, {len(afdb_results)} windows")

    if not mitdb_results and not afdb_results:
        print("ERROR: No results. Download datasets first.", file=sys.stderr)
        sys.exit(1)

    metrics_mitdb = compute_metrics(mitdb_results)
    metrics_afdb = compute_metrics(afdb_results)
    metrics_combined = compute_metrics(mitdb_results + afdb_results)

    write_report(metrics_mitdb, metrics_afdb, metrics_combined, args.out)

    # Exit 1 if pilot safety requirement not met
    if metrics_combined["sensitivity"] < 0.80:
        sys.exit(1)


if __name__ == "__main__":
    main()
