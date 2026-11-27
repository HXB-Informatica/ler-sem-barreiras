#!/usr/bin/env python3
"""Generate Android launcher densities from the approved master icon."""

from __future__ import annotations

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

# Sampled from the approved artwork (not the UI palette).
ICON_BLUE = (11, 79, 143, 255)  # close to #0B4F8F


def near_white(pixel: tuple[int, int, int, int]) -> bool:
    r, g, b, a = pixel
    return a > 0 and r > 245 and g > 245 and b > 245


def make_transparent_corners(src: Image.Image) -> Image.Image:
    img = src.convert("RGBA")
    pixels = img.load()
    width, height = img.size
    for y in range(height):
        for x in range(width):
            if near_white(pixels[x, y]):
                pixels[x, y] = (0, 0, 0, 0)
    return img


def fit_on_canvas(src: Image.Image, size: int, padding_ratio: float = 0.0, fill=None) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), fill or (0, 0, 0, 0))
    inner = max(1, int(size * (1 - padding_ratio)))
    fitted = src.resize((inner, inner), Image.Resampling.LANCZOS)
    offset = (size - inner) // 2
    canvas.paste(fitted, (offset, offset), fitted)
    return canvas


def circular(src: Image.Image, size: int) -> Image.Image:
    square = fit_on_canvas(src, size, fill=ICON_BLUE)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(square, (0, 0), mask)
    return out


def write_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def emit_app_res(res_root: Path, artwork: Image.Image) -> None:
    for name, scale in DENSITIES.items():
        launcher = fit_on_canvas(artwork, int(48 * scale), fill=ICON_BLUE)
        write_png(launcher, res_root / f"mipmap-{name}" / "ic_launcher.png")
        write_png(circular(artwork, int(48 * scale)), res_root / f"mipmap-{name}" / "ic_launcher_round.png")
        write_png(
            fit_on_canvas(artwork, int(108 * scale), padding_ratio=0.18, fill=ICON_BLUE),
            res_root / f"mipmap-{name}" / "ic_launcher_foreground.png",
        )
        bg = Image.new("RGBA", (int(108 * scale), int(108 * scale)), ICON_BLUE)
        write_png(bg, res_root / f"mipmap-{name}" / "ic_launcher_background.png")

    brand = fit_on_canvas(artwork, 512, fill=ICON_BLUE)
    write_png(brand, res_root / "drawable-nodpi" / "lsb_brand.png")
    symbol = fit_on_canvas(artwork, 512, padding_ratio=0.04)
    write_png(symbol, res_root / "drawable-nodpi" / "lsb_symbol.png")


def write_adaptive_xml(res_root: Path) -> None:
    xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = res_root / "mipmap-anydpi-v26" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(xml, encoding="utf-8")


def main() -> None:
    artwork = make_transparent_corners(Image.open(SRC))
    targets = [
        ROOT / "app" / "src" / "main" / "res",
        ROOT / "mobile" / "android" / "app" / "src" / "main" / "res",
    ]
    for res_root in targets:
        emit_app_res(res_root, artwork)
        write_adaptive_xml(res_root)

    brand = fit_on_canvas(artwork, 512, fill=ICON_BLUE)
    dest = ROOT / "mobile" / "src" / "assets" / "lsb_brand.png"
    dest.parent.mkdir(parents=True, exist_ok=True)
    brand.save(dest, "PNG", optimize=True)
    print("Generated launcher and brand assets.")


if __name__ == "__main__":
    main()
