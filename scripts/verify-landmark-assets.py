"""Validate the actual offline photo files, metadata, licensing and bounded bitmap sizes."""
from pathlib import Path
import json,hashlib,urllib.parse
from PIL import Image
root=Path(__file__).resolve().parents[1];assets=root/'app/src/external/assets'
catalog=json.loads((assets/'landmarks.json').read_text(encoding='utf-8'))
capitals=json.loads((root/'app/src/main/assets/capitals.json').read_text(encoding='utf-8'))
choices=json.loads((root/'scripts/landmark-files.json').read_text(encoding='utf-8'))
assert set(catalog)==set(capitals)==set(choices)
total=0;max_pixels=0
for code,row in catalog.items():
    assert row['asset']=='landmarks/'+code.lower()+'.webp'
    path=assets/row['asset'];raw=path.read_bytes()
    assert len(raw)==row['bytes'] and len(raw)<=1024*1024
    assert hashlib.sha256(raw).hexdigest()==row['sha256']
    assert row['city']==capitals[code]['name'] and row['landmark']==choices[code]['title']
    assert row['author'] and row['modified'] and row['license']
    license=row['license'].lower()
    assert license.startswith(('cc by ','cc by-sa ','cc0')) or license=='public domain'
    url=urllib.parse.urlsplit(row['source']);assert url.scheme=='https' and url.hostname=='commons.wikimedia.org'
    assert urllib.parse.unquote(url.path).replace('_',' ').endswith('File:'+choices[code]['file'].replace('_',' '))
    with Image.open(path) as image:
        assert image.format=='WEBP' and image.size==(row['width'],row['height'])
        assert max(image.size)<=1280 and min(image.size)>=160
        image.load();max_pixels=max(max_pixels,image.width*image.height)
    total+=len(raw)
assert {p.name for p in (assets/'landmarks').iterdir()}=={Path(row['asset']).name for row in catalog.values()}
assert total<=32*1024*1024
proof={'photos':len(catalog),'coverage':'ALL catalogue capitals','encodedBytes':total,'maxBitmapPixels':max_pixels,'maxRgb565Bytes':max_pixels*2,'authorLicenseSourceSha256':'PASS','offline':'no network or API key needed','cityViewFallbacks':[c for c,r in catalog.items() if r.get('cityView')]}
out=root/'tests/evidence/0.2.17';out.mkdir(parents=True,exist_ok=True);(out/'landmark-assets.json').write_text(json.dumps(proof,indent=2)+'\n')
print(json.dumps(proof))
