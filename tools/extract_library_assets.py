"""Render the two vector empty-state illustrations from the original PDF."""
from pathlib import Path
import hashlib,json,sys
import pypdfium2 as pdfium
from PIL import Image
out=Path('app/src/main/res/drawable-nodpi');manifest=[]
doc=pdfium.PdfDocument(sys.argv[1])
for name,num,box in [('demo_empty_favorites',240,(56,267,322,564)),('demo_empty_library',241,(85,310,345,565))]:
    page=doc[num-1];bitmap=page.render(scale=2);image=bitmap.to_pil().convert('RGBA').crop(tuple(i*2 for i in box))
    background=image.getpixel((0,0))[:3]
    image.putdata([(r,g,b,0 if (r,g,b)==background else a) for r,g,b,a in image.getdata()])
    dest=out/(name+'.webp');image.save(dest,'WEBP',quality=90)
    manifest.append({'resource':name,'page':num,'source':'vector artwork crop','crop':box,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
    bitmap.close();page.close()
Path('docs/third-batch-assets.json').write_text(json.dumps(manifest,indent=2)+'\n')
doc.close()
