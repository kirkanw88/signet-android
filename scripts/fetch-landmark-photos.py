"""Refresh the curated offline photo catalogue. Requires Pillow; no runtime image service.

Default: validate/preserve bundled files. --refresh: fetch licensed Commons thumbnails sequentially.
Candidate files are curated in landmark-files.json; never choose a random search result.
"""
from pathlib import Path
import urllib.request,urllib.parse,json,re,html,io,hashlib,sys
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/external/assets';OUT=ASSETS/'landmarks';OUT.mkdir(parents=True,exist_ok=True)
CATALOG=ASSETS/'landmarks.json'
CAPITALS=json.loads((ROOT/'app/src/main/assets/capitals.json').read_text(encoding='utf-8'))
FILES=json.loads((ROOT/'scripts/landmark-files.json').read_text(encoding='utf-8'))
records=json.loads(CATALOG.read_text(encoding='utf-8')) if CATALOG.exists() else {}
UA='SignetVPN-Landmarks/0.2.17 (https://github.com/kirkanw88/signet-android)'
def get(url):
    assert urllib.parse.urlsplit(url).scheme=='https'
    assert urllib.parse.urlsplit(url).hostname in ('commons.wikimedia.org','upload.wikimedia.org','thumb.wikimedia.org')
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':UA}),timeout=25) as response:
        assert urllib.parse.urlsplit(response.url).hostname in ('commons.wikimedia.org','upload.wikimedia.org','thumb.wikimedia.org')
        raw=response.read(1024*1024+1);assert len(raw)<=1024*1024;return raw
def plain(value):return html.unescape(re.sub('<[^>]*>','',value)).strip()
for code,choice in FILES.items():
    target=OUT/(code.lower()+'.webp')
    if '--refresh' not in sys.argv and code in records and target.exists():continue
    params={'action':'query','format':'json','formatversion':2,'redirects':1,'prop':'imageinfo','iiprop':'url|extmetadata|mime','iiurlwidth':640,'iiurlheight':640,'iiextmetadatafilter':'Artist|LicenseShortName|LicenseUrl','titles':'File:'+choice['file']}
    data=json.loads(get('https://commons.wikimedia.org/w/api.php?'+urllib.parse.urlencode(params)))
    info=next(p['imageinfo'][0] for p in data['query']['pages'] if p.get('imageinfo'))
    meta=info['extmetadata'];license=plain(meta['LicenseShortName']['value']);author=plain(meta['Artist']['value'])
    assert author and (license.lower().startswith(('cc by ','cc by-sa ','cc0')) or license.lower()=='public domain')
    assert info['mime'] in ('image/jpeg','image/png','image/webp')
    with Image.open(io.BytesIO(get(info['thumburl']))) as image:
        assert max(image.size)<=1280 and min(image.size)>=160
        encoded=io.BytesIO();image.convert('RGB').save(encoded,format='WEBP',quality=80,method=6)
        raw=encoded.getvalue();width,height=image.size
    target.write_bytes(raw)
    licenseUrl=meta.get('LicenseUrl',{}).get('value','').replace('http://','https://')
    if licenseUrl.startswith('//'):licenseUrl='https:'+licenseUrl
    records[code]={'city':CAPITALS[code]['name'],'landmark':choice['title'],'asset':'landmarks/'+target.name,'source':info['descriptionurl'],'author':author,'license':license,'licenseUrl':licenseUrl,'width':width,'height':height,'bytes':len(raw),'cityView':choice.get('cityView',False),'sha256':hashlib.sha256(raw).hexdigest(),'modified':'Encoded as WebP, quality 80; no crop.'}
    CATALOG.write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Photo',code,flush=True)
assert set(records)==set(CAPITALS)==set(FILES)
print('Catalogue complete:',len(records),'photos; bytes',sum(r['bytes'] for r in records.values()))
