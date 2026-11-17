"""Download public geography/texture inputs, record hashes; never touches production."""
import hashlib, json, pathlib, urllib.request
root = pathlib.Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
assets.mkdir(parents=True, exist_ok=True)
sources = {
    'countries.json': 'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson',
    'earth.jpg': 'https://raw.githubusercontent.com/turban/webgl-earth/master/images/2_no_clouds_4k.jpg',
}
manifest = {}
for name, url in sources.items():
    raw = urllib.request.urlopen(url, timeout=90).read()
    manifest[name] = {'url': url, 'sourceSha256': hashlib.sha256(raw).hexdigest()}
    if name == 'countries.json':
        features = json.loads(raw)['features']
        countries = {}
        for f in features:
            p = f['properties']; iso = p.get('ISO_A2_EH') or p.get('ISO_A2')
            if not iso or iso == '-99': continue
            geom = f['geometry']; polygons = geom['coordinates'] if geom['type'] == 'MultiPolygon' else [geom['coordinates']]
            # Round well below the source's 1:110m resolution; preserve islands and holes.
            polygons = [[[[round(x, 4), round(y, 4)] for x, y, *rest in ring] for ring in poly] for poly in polygons]
            countries[iso] = {'name': p.get('NAME'), 'lat': p.get('LABEL_Y'), 'lon': p.get('LABEL_X'), 'polygons': polygons}
        raw = json.dumps(countries, ensure_ascii=False, separators=(',', ':')).encode()
    (assets / name).write_bytes(raw)
    manifest[name]['sha256'] = hashlib.sha256(raw).hexdigest()
(root / 'assets').mkdir(exist_ok=True)
(root / 'assets/sources.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print(json.dumps({name: (assets/name).stat().st_size for name in sources}))
