from PIL import Image, ImageDraw, ImageFont
import math

# ── App Icon (512×512) ─────────────────────────────────────────────
size = 512
img = Image.new("RGBA", (size, size), (15, 118, 110, 255))  # #0F766E
draw = ImageDraw.Draw(img)

# Heart path (scaled from viewport 108 → 512)
def scale_pt(x, y):
    return (x * size / 108, y * size / 108)

# Draw heart
cx, cy = size // 2, size // 2
heart_points = [
    (26, 40), (26, 32), (32, 26), (40, 26),
    (45, 26), (50, 29), (54, 34),
    (58, 29), (63, 26), (68, 26),
    (76, 26), (82, 32), (82, 40),
    (82, 50), (70, 60), (54, 74),
    (38, 60), (26, 50),
]
scaled = [scale_pt(x, y) for x, y in heart_points]
draw.polygon(scaled, fill=(255, 255, 255, 255))

# Draw ECG pulse line inside heart
pulse_pts = [(38, 42), (48, 42), (52, 52), (56, 36), (60, 48), (64, 42), (70, 42)]
scaled_pulse = [scale_pt(x, y) for x, y in pulse_pts]
for i in range(len(scaled_pulse) - 1):
    draw.line(
        [scaled_pulse[i], scaled_pulse[i + 1]],
        fill=(15, 118, 110, 255),
        width=12,
    )

# Rounded corners
mask = Image.new("L", (size, size), 0)
mask_draw = ImageDraw.Draw(mask)
radius = 80
mask_draw.rounded_rectangle([(0, 0), (size - 1, size - 1)], radius=radius, fill=255)
img.putalpha(mask)
img.save("/Users/ariyanbhakat/Three-Musketeers/docs/play-store-screenshots/icon-512.png")
print("Saved icon-512.png")

# ── Feature Graphic (1024×500) ─────────────────────────────────────
fw, fh = 1024, 500
fg = Image.new("RGBA", (fw, fh), (15, 118, 110, 255))
fg_draw = ImageDraw.Draw(fg)

# Gradient overlay (lighter teal at top)
for y in range(fh):
    alpha = int(80 * (1 - y / fh))
    overlay = Image.new("RGBA", (fw, fh), (255, 255, 255, alpha))
fg = Image.alpha_composite(fg, overlay)

# Draw smaller icon on left
small_icon = img.resize((180, 180), Image.LANCZOS)
fg.paste(small_icon, (80, (fh - 180) // 2), small_icon)

# Draw text
try:
    title_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 56)
    subtitle_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 28)
except:
    title_font = ImageFont.load_default()
    subtitle_font = ImageFont.load_default()

fg_draw.text((300, 140), "ElderCare Guardian", fill=(255, 255, 255, 255), font=title_font)
fg_draw.text((300, 220), "IIT Mandi — Elderly Safety Wearable", fill=(200, 230, 225, 255), font=subtitle_font)

fg.save("/Users/ariyanbhakat/Three-Musketeers/docs/play-store-screenshots/feature-graphic-1024x500.png")
print("Saved feature-graphic-1024x500.png")
