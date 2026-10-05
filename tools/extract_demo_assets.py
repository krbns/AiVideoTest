import pypdfium2 as pdf
from PIL import Image
from pathlib import Path
import hashlib, json, sys, tempfile, shutil
work = Path(tempfile.mkdtemp(prefix="aivideo-assets-"))
src=Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/Users/kurban/Downloads/157 - Higgsfield AI  (Vlad, 06.08.2025) (Copy) (Copy).pdf')
out=Path('app/src/main/res/drawable-nodpi');out.mkdir(parents=True,exist_ok=True)
doc=pdf.PdfDocument(src); manifests=[]; hashes={}
for name,n,i in [('demo_gold',43,0),('demo_beach',43,3),('demo_penguin',43,5),('demo_japan',43,7),('demo_underwater',43,9),('demo_fashion',43,11),('demo_anime_1',44,6),('demo_anime_2',44,8),('demo_anime_3',44,10),('demo_portrait',31,2),('demo_anime_portrait',31,4)]:
 p=doc[n-1];objs=list(p.get_objects(filter=[pdf.raw.FPDF_PAGEOBJ_IMAGE]));o=objs[i];o.extract(work/name)
 source=next(work.glob(name+'.*'));im=Image.open(source).convert('RGB');im.thumbnail((960,1280));dest=out/(name+'.webp');im.save(dest,'WEBP',quality=86)
 digest=hashlib.sha256(dest.read_bytes()).hexdigest()
 canonical=hashes.setdefault(digest, name)
 entry={'resource':canonical,'page':n,'image_object_index':i,'size':im.size,'sha256':digest}
 if canonical != name:
  dest.unlink();entry['fixture_alias']=name
 manifests.append(entry);p.close()
# Banner artwork is a composite clipped panel, so render only that artwork region.
p=doc[42];b=p.render(scale=2);im=b.to_pil();im.crop((32,372,748,704)).resize((1074,495)).save(out/'demo_banner.webp','WEBP',quality=90);b.close();p.close()
manifests.append({'resource':'demo_banner','page':43,'source':'Composite banner artwork crop; no app UI included','size':[1074,495],'sha256':hashlib.sha256((out/'demo_banner.webp').read_bytes()).hexdigest()})
Path('docs/demo-assets.json').write_text(json.dumps(manifests,ensure_ascii=False,indent=2)+'\n')

doc.close()
shutil.rmtree(work)
