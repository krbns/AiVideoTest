"""Crop only illustrative content: outer status bars, titles and controls stay native."""
from pathlib import Path
import hashlib
import json
import sys
import pypdfium2 as pdf

source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/Users/kurban/Downloads/157 - Higgsfield AI  (Vlad, 06.08.2025) (Copy) (Copy).pdf')
out = Path('app/src/main/res/drawable-nodpi')
doc = pdf.PdfDocument(source)
manifest = []
for name, number, crop in [
    ('demo_intro_welcome', 6, (0, 48, 390, 648)),
    ('demo_intro_prompt', 7, (0, 48, 390, 648)),
    ('demo_intro_share', 8, (0, 48, 390, 648)),
    ('demo_intro_reviews', 9, (0, 48, 390, 648)),
    ('demo_intro_notifications', 11, (0, 48, 390, 648)),
    ('demo_rating_hearts', 25, (55, 233, 335, 513)),
]:
    page = doc[number - 1]
    bitmap = page.render(scale=2)
    image = bitmap.to_pil().convert('RGB').crop(tuple(round(x * 2) for x in crop))
    dest = out / (name + '.webp')
    image.save(dest, 'WEBP', quality=88)
    manifest.append({'resource': name, 'page': number, 'crop': crop, 'size': image.size,
                     'source': 'Illustration crop only; headings and buttons are Compose. Phone chrome is part of the illustrated mockup.',
                     'sha256': hashlib.sha256(dest.read_bytes()).hexdigest()})
    bitmap.close(); page.close()
Path('docs/fifth-batch-assets.json').write_text(json.dumps(manifest, indent=2) + '\n')
doc.close()
