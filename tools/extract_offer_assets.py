"""Extract the standalone offer artwork from PDF page 13 (no status bar/text baked in)."""
import hashlib
import json
import sys
import tempfile
from pathlib import Path
import pypdfium2 as pdf
from PIL import Image

source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/Users/kurban/Downloads/157 - Higgsfield AI  (Vlad, 06.08.2025) (Copy) (Copy).pdf')
with tempfile.TemporaryDirectory(prefix='aivideotest-offer-art-') as scratch:
    doc = pdf.PdfDocument(source)
    page = doc[12]
    obj = list(page.get_objects(filter=[pdf.raw.FPDF_PAGEOBJ_IMAGE]))[0]
    obj.extract(Path(scratch) / 'hero')
    raw = next(Path(scratch).glob('hero.*'))
    image = Image.open(raw).convert('RGB')
    left, bottom, right, top = obj.get_bounds()
    crop_top = round(max(0, top - page.get_height()) / (top - bottom) * image.height)
    image = image.crop((0, crop_top, image.width, image.height))
    image.thumbnail((1024, 1400))
    dest = Path('app/src/main/res/drawable-nodpi/demo_offer_hero.webp')
    image.save(dest, 'WEBP', quality=88)
    Path('docs/fourth-batch-assets.json').write_text(json.dumps([{
        'resource': dest.stem, 'page': 13, 'image_object_index': 0,
        'source': 'Standalone artwork; crop off-page top only. Native gradient is rendered in Compose.',
        'size': image.size, 'sha256': hashlib.sha256(dest.read_bytes()).hexdigest()
    }], indent=2) + '\n')
    page.close()
    doc.close()
