#!/usr/bin/env python3
"""
Generate all 6 Play Store screenshots for ElderCare Guardian.

Produces 1080×1920 px (9:16) PNG mock screenshots matching the listing in
docs/play-store-listing.md. Each screenshot simulates the corresponding app
screen with realistic data so the Play Store listing looks complete.

Requirements:
    pip install Pillow

Usage:
    python3 scripts/generate_screenshots.py
    python3 scripts/generate_screenshots.py --out docs/play-store-screenshots/
"""

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    print("Pillow not installed. Run: pip install Pillow")
    sys.exit(1)

# ── Colours ───────────────────────────────────────────────────────────────────
TEAL      = (15, 118, 110)
TEAL_DARK = (10, 80, 75)
TEAL_BG   = (240, 253, 252)
WHITE     = (255, 255, 255)
SLATE     = (71, 85, 105)
SLATE_LT  = (148, 163, 184)
DANGER    = (185, 28, 28)
AMBER     = (180, 83, 9)
GREEN_OK  = (21, 128, 61)
BG        = (248, 250, 252)
CARD_BG   = (255, 255, 255)
DIVIDER   = (226, 232, 240)

W, H = 1080, 1920
PAD = 60

# ── Font helpers ──────────────────────────────────────────────────────────────

def _font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    candidates = (
        ["/System/Library/Fonts/Helvetica.ttc",
         "/System/Library/Fonts/Arial.ttf",
         "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold
             else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]
    )
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except (IOError, OSError):
            continue
    return ImageFont.load_default()

def F(size, bold=False): return _font(size, bold)


# ── Drawing primitives ────────────────────────────────────────────────────────

def new_screen() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    return img, draw


def status_bar(draw: ImageDraw.ImageDraw):
    draw.rectangle([(0, 0), (W, 80)], fill=TEAL_DARK)
    draw.text((PAD, 22), "9:41 AM", font=F(36, True), fill=WHITE)
    draw.text((W - PAD - 160, 22), "●●●  WiFi  100%", font=F(30), fill=WHITE)


def top_bar(draw: ImageDraw.ImageDraw, title: str):
    draw.rectangle([(0, 80), (W, 180)], fill=TEAL)
    draw.text((PAD, 105), title, font=F(46, True), fill=WHITE)


def bottom_nav(draw: ImageDraw.ImageDraw, active: str):
    tabs = ["Vitals", "Safety", "Caregiver", "Readiness", "Settings"]
    draw.rectangle([(0, H - 130), (W, H)], fill=WHITE)
    draw.line([(0, H - 130), (W, H - 130)], fill=DIVIDER, width=2)
    w = W // len(tabs)
    for i, tab in enumerate(tabs):
        x = i * w + w // 2
        color = TEAL if tab == active else SLATE_LT
        draw.text((x, H - 90), tab, font=F(28, tab == active), fill=color, anchor="mm")


def card(draw: ImageDraw.ImageDraw, x, y, w, h, radius=20):
    draw.rounded_rectangle([(x, y), (x + w, y + h)], radius=radius, fill=CARD_BG,
                            outline=DIVIDER, width=1)


def metric_row(draw: ImageDraw.ImageDraw, x, y, label, value, unit, color=None):
    draw.text((x, y), label, font=F(30), fill=SLATE)
    vcolor = color or (50, 50, 50)
    draw.text((x, y + 40), value, font=F(54, True), fill=vcolor)
    draw.text((x + draw.textlength(value, font=F(54, True)) + 8, y + 60),
              unit, font=F(28), fill=SLATE_LT)


def pill(draw: ImageDraw.ImageDraw, x, y, text, bg, fg=WHITE):
    tw = int(draw.textlength(text, font=F(30, True)))
    draw.rounded_rectangle([(x, y), (x + tw + 32, y + 44)], radius=22, fill=bg)
    draw.text((x + 16, y + 7), text, font=F(30, True), fill=fg)
    return x + tw + 32 + 12  # next x


# ── Screen 1: Vitals Dashboard ────────────────────────────────────────────────

def screen_vitals(out: Path):
    img, draw = new_screen()
    status_bar(draw)
    top_bar(draw, "Vitals")

    y = 200
    # ECG waveform card
    card(draw, PAD, y, W - 2 * PAD, 260)
    draw.text((PAD + 24, y + 18), "ECG Waveform", font=F(32, True), fill=SLATE)
    draw.text((W - PAD - 100, y + 18), "Normal", font=F(30, True), fill=GREEN_OK)
    # Draw synthetic ECG line
    import math
    pts = []
    for i in range(W - 2 * PAD - 48):
        bx = PAD + 24 + i
        t = i / (W - 2 * PAD - 48)
        # QRS spike at t≈0.4
        spike = 90 * math.exp(-((t - 0.4) ** 2) / 0.0008) if abs(t - 0.4) < 0.05 else 0
        baseline = 8 * math.sin(2 * math.pi * t * 6)
        by = y + 160 - spike - baseline
        pts.append((bx, by))
    for i in range(len(pts) - 1):
        draw.line([pts[i], pts[i + 1]], fill=TEAL, width=4)

    y += 280
    # HR + SpO2 row
    card(draw, PAD, y, (W - 3 * PAD) // 2, 180)
    metric_row(draw, PAD + 20, y + 16, "Heart Rate", "74", " bpm", (220, 38, 38))

    card(draw, PAD + (W - 3 * PAD) // 2 + PAD, y, (W - 3 * PAD) // 2, 180)
    metric_row(draw, PAD + (W - 3 * PAD) // 2 + PAD + 20, y + 16, "SpO2", "98", " %", (37, 99, 235))

    y += 200
    # Temp + Resp row
    card(draw, PAD, y, (W - 3 * PAD) // 2, 180)
    metric_row(draw, PAD + 20, y + 16, "Skin Temp", "36.4", " °C", AMBER)

    card(draw, PAD + (W - 3 * PAD) // 2 + PAD, y, (W - 3 * PAD) // 2, 180)
    metric_row(draw, PAD + (W - 3 * PAD) // 2 + PAD + 20, y + 16, "Resp Rate", "16", " /min", TEAL)

    y += 200
    # Status pills
    card(draw, PAD, y, W - 2 * PAD, 110)
    nx = PAD + 20
    nx = pill(draw, nx, y + 33, "ECG Normal", GREEN_OK)
    nx = pill(draw, nx, y + 33, "SpO2 Safe", GREEN_OK)
    nx = pill(draw, nx, y + 33, "HR Normal", (59, 130, 246))

    # Caption
    draw.text((W // 2, H - 160), "Real-time heart rate, SpO2, and ECG waveform",
              font=F(34), fill=SLATE, anchor="mm")

    bottom_nav(draw, "Vitals")
    img.save(out / "screenshot_01_vitals.png")
    print("  Saved screenshot_01_vitals.png")


# ── Screen 2: Safety Panel ────────────────────────────────────────────────────

def screen_safety(out: Path):
    img, draw = new_screen()
    status_bar(draw)
    top_bar(draw, "Safety")

    y = 200
    # Alert level banner
    draw.rounded_rectangle([(PAD, y), (W - PAD, y + 90)], radius=12,
                            fill=(240, 253, 244))
    draw.rounded_rectangle([(PAD, y), (W - PAD, y + 90)], radius=12,
                            outline=GREEN_OK, width=3)
    draw.text((W // 2, y + 45), "✓  NORMAL — All clear", font=F(38, True),
              fill=GREEN_OK, anchor="mm")

    y += 110
    # Fall risk
    card(draw, PAD, y, W - 2 * PAD, 160)
    draw.text((PAD + 24, y + 20), "Fall Risk", font=F(34, True), fill=(50, 50, 50))
    draw.text((PAD + 24, y + 70), "Low  ●●○○○", font=F(40, True), fill=GREEN_OK)
    draw.text((W - PAD - 50, y + 25), "0.12", font=F(38, True), fill=SLATE, anchor="rm")

    y += 180
    # IMU card
    card(draw, PAD, y, W - 2 * PAD, 160)
    draw.text((PAD + 24, y + 20), "IMU — Motion", font=F(34, True), fill=(50, 50, 50))
    draw.text((PAD + 24, y + 70), "ax  0.12    ay  −0.04    az  9.78", font=F(34), fill=SLATE)
    draw.text((PAD + 24, y + 115), "gx  0.8     gy  1.2     gz  0.3", font=F(30), fill=SLATE_LT)

    y += 180
    # Posture + Inactivity
    card(draw, PAD, y, (W - 3 * PAD) // 2, 160)
    draw.text((PAD + 20, y + 20), "Posture", font=F(34, True), fill=(50, 50, 50))
    draw.text((PAD + 20, y + 80), "Upright", font=F(42, True), fill=TEAL)

    card(draw, PAD + (W - 3 * PAD) // 2 + PAD, y, (W - 3 * PAD) // 2, 160)
    draw.text((PAD + (W - 3 * PAD) // 2 + PAD + 20, y + 20), "Inactivity", font=F(34, True), fill=(50, 50, 50))
    draw.text((PAD + (W - 3 * PAD) // 2 + PAD + 20, y + 80), "Normal", font=F(42, True), fill=GREEN_OK)

    y += 180
    # SOS button
    draw.rounded_rectangle([(PAD, y), (W - PAD, y + 120)], radius=16, fill=DANGER)
    draw.text((W // 2, y + 60), "SOS — Send Emergency Alert", font=F(38, True),
              fill=WHITE, anchor="mm")

    draw.text((W // 2, H - 160), "Fall risk, posture, and SOS status at a glance",
              font=F(34), fill=SLATE, anchor="mm")

    bottom_nav(draw, "Safety")
    img.save(out / "screenshot_02_safety.png")
    print("  Saved screenshot_02_safety.png")


# ── Screen 3: Caregiver Alert ─────────────────────────────────────────────────

def screen_caregiver(out: Path):
    img, draw = new_screen()
    status_bar(draw)
    top_bar(draw, "Caregiver")

    y = 200
    # Warning banner
    draw.rounded_rectangle([(PAD, y), (W - PAD, y + 100)], radius=12, fill=(255, 237, 213))
    draw.rounded_rectangle([(PAD, y), (W - PAD, y + 100)], radius=12,
                            outline=AMBER, width=3)
    draw.text((W // 2, y + 50), "⚠  WARNING — Fall detected", font=F(38, True),
              fill=AMBER, anchor="mm")

    y += 120
    # Call caregiver button
    draw.rounded_rectangle([(PAD, y), (W - PAD, y + 100)], radius=12, fill=TEAL)
    draw.text((W // 2, y + 50), "📞  Call Caregiver — Meera", font=F(40, True),
              fill=WHITE, anchor="mm")

    y += 120
    # Alert timeline
    draw.text((PAD, y + 10), "Alert Timeline", font=F(38, True), fill=(50, 50, 50))
    y += 60
    events = [
        ("16:32", "⚠ Warning", "Possible fall detected (IMU spike)", AMBER),
        ("16:30", "ℹ Check",   "Heart rate elevated — 118 bpm",       (37, 99, 235)),
        ("16:15", "✓ Normal",  "Vitals normal — monitoring active",   GREEN_OK),
        ("15:50", "✓ Normal",  "Device reconnected via BLE",          GREEN_OK),
    ]
    for time, level, reason, color in events:
        card(draw, PAD, y, W - 2 * PAD, 110)
        draw.text((PAD + 20, y + 15), time, font=F(28), fill=SLATE_LT)
        draw.text((PAD + 130, y + 15), level, font=F(30, True), fill=color)
        draw.text((PAD + 20, y + 62), reason, font=F(30), fill=SLATE)
        y += 125

    draw.text((W // 2, H - 160), "Alert timeline with call caregiver button",
              font=F(34), fill=SLATE, anchor="mm")

    bottom_nav(draw, "Caregiver")
    img.save(out / "screenshot_03_caregiver.png")
    print("  Saved screenshot_03_caregiver.png")


# ── Screen 4: Readiness ───────────────────────────────────────────────────────

def screen_readiness(out: Path):
    img, draw = new_screen()
    status_bar(draw)
    top_bar(draw, "Readiness")

    y = 200
    checklist = [
        ("BLE connected to ElderCare_v1", True),
        ("Monitoring service active",      True),
        ("FCM token registered",           True),
        ("Samsung Health — reflection",    True),
        ("Consent granted (DPDPA)",        True),
        ("Caregiver contact set",          True),
    ]
    card(draw, PAD, y, W - 2 * PAD, len(checklist) * 85 + 30)
    draw.text((PAD + 24, y + 16), "System Checklist", font=F(36, True), fill=(50, 50, 50))
    iy = y + 65
    for label, ok in checklist:
        icon = "✓" if ok else "✗"
        color = GREEN_OK if ok else DANGER
        draw.text((PAD + 24, iy), f"{icon}  {label}", font=F(34, ok), fill=color if ok else SLATE)
        iy += 85

    y += len(checklist) * 85 + 50
    # Battery + signal
    card(draw, PAD, y, (W - 3 * PAD) // 2, 140)
    draw.text((PAD + 20, y + 18), "Battery", font=F(34, True), fill=(50, 50, 50))
    draw.rounded_rectangle([(PAD + 20, y + 65), (PAD + 20 + 300, y + 105)], radius=8, fill=DIVIDER)
    draw.rounded_rectangle([(PAD + 20, y + 65), (PAD + 20 + int(300 * 0.82), y + 105)],
                            radius=8, fill=GREEN_OK)
    draw.text((PAD + 340, y + 72), "82%", font=F(34, True), fill=GREEN_OK)

    card(draw, PAD + (W - 3 * PAD) // 2 + PAD, y, (W - 3 * PAD) // 2, 140)
    draw.text((PAD + (W - 3 * PAD) // 2 + PAD + 20, y + 18), "BLE Signal", font=F(34, True), fill=(50, 50, 50))
    draw.text((PAD + (W - 3 * PAD) // 2 + PAD + 20, y + 72), "−62 dBm  ▲ Strong", font=F(34, True), fill=TEAL)

    draw.text((W // 2, H - 160), "BLE connection, device telemetry, Samsung Health status",
              font=F(32), fill=SLATE, anchor="mm")

    bottom_nav(draw, "Readiness")
    img.save(out / "screenshot_04_readiness.png")
    print("  Saved screenshot_04_readiness.png")


# ── Screen 5: Settings ────────────────────────────────────────────────────────

def screen_settings(out: Path):
    img, draw = new_screen()
    status_bar(draw)
    top_bar(draw, "Settings")

    y = 200
    # Patient card
    card(draw, PAD, y, W - 2 * PAD, 240)
    draw.text((PAD + 24, y + 18), "Patient profiles", font=F(36, True), fill=(50, 50, 50))
    draw.line([(PAD + 24, y + 68), (W - PAD - 24, y + 68)], fill=DIVIDER, width=1)
    draw.text((PAD + 24, y + 82), "Ramesh Verma — Age 74", font=F(36, True), fill=(30, 30, 30))
    draw.text((PAD + 24, y + 130), "Caregiver: Meera Verma  •  Active", font=F(30), fill=SLATE)
    draw.rounded_rectangle([(W - PAD - 180, y + 170), (W - PAD - 24, y + 215)],
                            radius=8, fill=TEAL)
    draw.text((W - PAD - 102, y + 192), "+ Add patient", font=F(28, True), fill=WHITE, anchor="mm")

    y += 260
    # Caregiver contact
    card(draw, PAD, y, W - 2 * PAD, 200)
    draw.text((PAD + 24, y + 18), "Caregiver contact", font=F(36, True), fill=(50, 50, 50))
    draw.text((PAD + 24, y + 74), "Name:   Meera Verma", font=F(32), fill=SLATE)
    draw.text((PAD + 24, y + 118), "Phone:  +91 98765 43210", font=F(32), fill=SLATE)
    # SMS toggle
    draw.text((PAD + 24, y + 160), "SMS alerts", font=F(30), fill=SLATE)
    # toggle on
    draw.rounded_rectangle([(W - PAD - 90, y + 155), (W - PAD - 24, y + 185)],
                            radius=15, fill=TEAL)
    draw.ellipse([(W - PAD - 55, y + 158), (W - PAD - 27, y + 182)], fill=WHITE)

    y += 220
    # Backend URL
    card(draw, PAD, y, W - 2 * PAD, 130)
    draw.text((PAD + 24, y + 16), "Backend URL (FCM alerts)", font=F(32, True), fill=(50, 50, 50))
    draw.text((PAD + 24, y + 65), "https://eldercare.railway.app", font=F(32), fill=TEAL)

    draw.text((W // 2, H - 160), "Patient profiles and caregiver contact configuration",
              font=F(32), fill=SLATE, anchor="mm")

    bottom_nav(draw, "Settings")
    img.save(out / "screenshot_05_settings.png")
    print("  Saved screenshot_05_settings.png")


# ── Screen 6: DPDPA Consent ───────────────────────────────────────────────────

def screen_consent(out: Path):
    img, draw = new_screen()
    status_bar(draw)

    # Full teal header
    draw.rectangle([(0, 80), (W, 280)], fill=TEAL)
    draw.text((W // 2, 145), "ElderCare Guardian", font=F(52, True), fill=WHITE, anchor="mm")
    draw.text((W // 2, 210), "Data Privacy & Consent", font=F(36), fill=(200, 240, 235), anchor="mm")

    y = 300
    card(draw, PAD, y, W - 2 * PAD, 980)
    draw.text((PAD + 24, y + 20), "Digital Personal Data Protection Act, 2023",
              font=F(30, True), fill=TEAL)
    draw.text((PAD + 24, y + 65), "Under DPDPA 2023 (India), we need your informed",
              font=F(30), fill=SLATE)
    draw.text((PAD + 24, y + 105), "consent before collecting health data.", font=F(30), fill=SLATE)

    items = [
        ("Heart rate, SpO2, temperature, and motion", True),
        ("Fall and inactivity detection events",      True),
        ("Emergency alerts to your caregiver",        True),
        ("All data stays on this device (encrypted)", True),
        ("No data is sent to any cloud server",       True),
        ("You can export or delete data at any time", True),
    ]
    iy = y + 165
    for text, ok in items:
        draw.text((PAD + 24, iy), f"{'✓' if ok else '○'}  {text}", font=F(30, ok),
                  fill=GREEN_OK if ok else SLATE)
        iy += 60

    draw.text((PAD + 24, iy + 20), "Your rights:", font=F(32, True), fill=(50, 50, 50))
    draw.text((PAD + 24, iy + 65), "Right to access  •  Right to erasure", font=F(30), fill=SLATE)
    draw.text((PAD + 24, iy + 110), "Right to grievance redressal", font=F(30), fill=SLATE)

    y = 1310
    draw.rounded_rectangle([(PAD, y), (W - PAD, y + 110)], radius=14, fill=TEAL)
    draw.text((W // 2, y + 55), "I Consent — Start Monitoring", font=F(42, True),
              fill=WHITE, anchor="mm")

    y += 130
    draw.rounded_rectangle([(PAD, y), (W - PAD, y + 90)], radius=14,
                            fill=WHITE, outline=DIVIDER, width=2)
    draw.text((W // 2, y + 45), "Decline", font=F(38), fill=DANGER, anchor="mm")

    draw.text((W // 2, H - 100), "DPDPA consent screen — first launch flow",
              font=F(32), fill=SLATE, anchor="mm")

    img.save(out / "screenshot_06_consent.png")
    print("  Saved screenshot_06_consent.png")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Generate Play Store screenshots")
    parser.add_argument("--out", default="docs/play-store-screenshots",
                        help="Output directory")
    args = parser.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    print(f"Generating 6 screenshots (1080×1920) → {out}/")
    screen_vitals(out)
    screen_safety(out)
    screen_caregiver(out)
    screen_readiness(out)
    screen_settings(out)
    screen_consent(out)
    print(f"\nDone — 6 screenshots in {out}/")
    print("\nPlay Store upload order:")
    for i, name in enumerate([
        "Vitals Dashboard", "Safety Panel", "Caregiver Alert",
        "Readiness", "Settings", "Data Privacy (DPDPA)"
    ], 1):
        print(f"  {i}. {out}/screenshot_0{i}_*.png  —  {name}")


if __name__ == "__main__":
    main()
