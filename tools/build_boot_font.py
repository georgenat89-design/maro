"""Bake bounded Rubik Bold glyphs at native small, medium and display resolutions."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont
root = Path(__file__).resolve().parents[1]
out = root / 'src/main/resources/assets/maro/textures/gui'
for size, cell, suffix in [(16, 32, '_16'), (32, 64, '_32'), (96, 128, '')]:
    font = ImageFont.truetype(str(root / 'src/main/resources/assets/maro/font/rubik_bold.ttf'), size)
    image = Image.new('RGBA', (cell * 16, cell * 6))
    padding = 6 if size == 96 else 3
    advances = []
    for i, code in enumerate(range(32, 127)):
        char = chr(code)
        # A separate cell prevents accents/punctuation from bleeding into neighbouring glyphs.
        glyph = Image.new('RGBA', (cell, cell))
        ImageDraw.Draw(glyph).text((padding, padding - font.getbbox('H')[1]), char, font=font, fill='white')
        image.alpha_composite(glyph, (i % 16 * cell, i // 16 * cell))
        advances.append(round(font.getlength(char), 3))
    image.save(out / ('boot_font' + suffix + '.png'), optimize=True)
    if size == 96:
        (out / 'boot_font.json').write_text(json.dumps(advances), encoding='utf-8')
    print('Baked Rubik Bold', size, (out / ('boot_font' + suffix + '.png')).stat().st_size, 'bytes')
