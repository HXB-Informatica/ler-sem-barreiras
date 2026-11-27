#!/usr/bin/env python3
"""Generate Android launcher densities from the approved master icon."""

from __future__ import annotations

from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs" / "icone_aprovado_original.png"
DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}


def near_white(pixel: tuple[int, int, int, int]) -> bool:
    r, g, b, a = pixel
    return a > 0 and r > 245 and g > 245 and b > 245


def flood_corners(src: Image.Image, replacement: tuple[int, int, int, int]) -> Image.Image:
    """Replace only the outer white canvas, never the white book or speech bubble."""
    img = src.convert("RGBA")
    width, height = img.size
    pixels = img.load()
    seen = [[False] * width for _ in range(height)]
    queue: deque[tuple[int, int]] = deque(
        [(0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)]
    )
    while queue:
        x, y = queue.popleft()
        if x < 0 or y < 0 or x >= width or y >= height or seen[y][x]:
            continue
        seen[y][x] = True
        if not near_white(pixels[x, y]):
            continue
        pixels[x, y] = replacement
        queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return img


def sample_icon_blue(src: Image.Image) -> tuple[int, int, int, int]:
    img = src.convert("RGBA")
    width, height = img.size
    return img.getpixel((width // 8, height // 5))


def crop_past_rounding(src: Image.Image) -> Image.Image:
    """Drop the baked-in rounded corners so Android can mask the icon itself."""
    width, height = src.size
    inset = int(min(width, height) * 0.055)
    return src.crop((inset, inset, width - inset, height - inset))


def fit_on_canvas(src: Image.Image, size: int, padding_ratio: float = 0.0, fill=None) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), fill or (0, 0, 0, 0))
    inner = max(1, int(size * (1 - padding_ratio)))
    fitted = src.resize((inner, inner), Image.Resampling.LANCZOS)
    offset = (size - inner) // 2
    canvas.paste(fitted, (offset, offset), fitted)
    return canvas


def circular(src: Image.Image, size: int, fill) -> Image.Image:
    square = fit_on_canvas(src, size, fill=fill)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(square, (0, 0), mask)
    return out


def write_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def emit_app_res(res_root: Path, bleed: Image.Image, logo: Image.Image, blue) -> None:
    for name, scale in DENSITIES.items():
        write_png(fit_on_canvas(bleed, int(48 * scale), fill=blue), res_root / f"mipmap-{name}" / "ic_launcher.png")
        write_png(circular(bleed, int(48 * scale), blue), res_root / f"mipmap-{name}" / "ic_launcher_round.png")
        write_png(
            fit_on_canvas(bleed, int(108 * scale), padding_ratio=0.25, fill=blue),
            res_root / f"mipmap-{name}" / "ic_launcher_foreground.png",
        )
        bg = Image.new("RGBA", (int(108 * scale), int(108 * scale)), blue)
        write_png(bg, res_root / f"mipmap-{name}" / "ic_launcher_background.png")

    write_png(fit_on_canvas(logo, 512), res_root / "drawable-nodpi" / "lsb_brand.png")
    write_png(fit_on_canvas(logo, 512), res_root / "drawable-nodpi" / "lsb_symbol.png")
    write_png(fit_on_canvas(bleed, 432, padding_ratio=0.25, fill=blue), res_root / "drawable-nodpi" / "ic_launcher_foreground.png")


def write_adaptive_xml(res_root: Path) -> None:
    xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/lsb_icon_blue" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = res_root / "mipmap-anydpi-v26" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(xml, encoding="utf-8")


def write_color(res_root: Path, blue: tuple[int, int, int, int]) -> None:
    import re

    hex_color = f"#{blue[0]:02X}{blue[1]:02X}{blue[2]:02X}"
    colors = res_root / "values" / "colors.xml"
    text = colors.read_text(encoding="utf-8")
    text = re.sub(
        r'<color name="lsb_icon_blue">.*?</color>',
        f'<color name="lsb_icon_blue">{hex_color}</color>',
        text,
    )
    colors.write_text(text, encoding="utf-8")


def main() -> None:
    original = Image.open(SRC)
    blue = sample_icon_blue(original)
    bleed = crop_past_rounding(flood_corners(original, blue))
    logo = flood_corners(original, (0, 0, 0, 0))
    targets = [
        ROOT / "app" / "src" / "main" / "res",
        ROOT / "mobile" / "android" / "app" / "src" / "main" / "res",
    ]
    for res_root in targets:
        emit_app_res(res_root, bleed, logo, blue)
        write_adaptive_xml(res_root)
        write_color(res_root, blue)

    dest = ROOT / "mobile" / "src" / "assets" / "lsb_brand.png"
    dest.parent.mkdir(parents=True, exist_ok=True)
    fit_on_canvas(logo, 512).save(dest, "PNG", optimize=True)
    print(f"Generated launcher from approved logo. Background {blue[0]:02X}{blue[1]:02X}{blue[2]:02X}.")


if __name__ == "__main__":
    main()
