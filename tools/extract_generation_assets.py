from pathlib import Path
import pypdfium2 as p,json,hashlib,sys,tempfile,shutil
work=Path(tempfile.mkdtemp(prefix="aivideo-generation-assets-"))
from PIL import Image
D=p.PdfDocument(sys.argv[1]);out=Path('app/src/main/res/drawable-nodpi');manifest=[]
for name,n,i in [('demo_good_1',119,5),('demo_good_2',119,6),('demo_bad_1',119,7),('demo_bad_2',119,8),('demo_style_ghibli',157,0),('demo_style_3d',157,1),('demo_style_simpsons',157,2),('demo_style_fantasy',157,3)]:
 P=D[n-1];O=list(P.get_objects(filter=[p.raw.FPDF_PAGEOBJ_IMAGE]))[i];O.extract(work/name);src=next(work.glob(name+'.*'));im=Image.open(src).convert('RGB');im.thumbnail((600,750));dest=out/(name+'.webp');im.save(dest,'WEBP',quality=86);manifest.append({'resource':name,'page':n,'image_object_index':i,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()});P.close()
# Creating ribbon is vector artwork in the PDF; export just its illustration region.
P=D[96];b=P.render(scale=2);im=b.to_pil().crop((32,400,748,920));im.save(out/'demo_creating.webp','WEBP',quality=90);b.close();P.close();manifest.append({'resource':'demo_creating','page':97,'source':'Creating illustration crop (vector artwork)','sha256':hashlib.sha256((out/'demo_creating.webp').read_bytes()).hexdigest()})
Path('docs/second-batch-assets.json').write_text(json.dumps(manifest,indent=2)+'\n')
D.close()
shutil.rmtree(work)
