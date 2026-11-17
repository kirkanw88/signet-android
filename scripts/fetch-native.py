"""Pinned official release, verified against the GitHub release asset digest."""
import hashlib, io, json, pathlib, urllib.request, zipfile, tempfile
root = pathlib.Path(__file__).resolve().parents[1]
url = 'https://github.com/XTLS/libXray/releases/download/v26.11.9/libxray-android.zip'
expected = '1b1aa12124a7112af983a6cea74c77f27aadc6b650acf3c5a57019619ba9fa48'
cache_dir = pathlib.Path('C:/Android/signet-tools') if pathlib.Path('C:/Android/signet-tools').exists() else pathlib.Path(tempfile.gettempdir()) / 'signet-artifacts'
cache_dir.mkdir(parents=True, exist_ok=True)
cache = cache_dir / 'libxray-android-v26.11.9.zip'
if not cache.exists():
    with urllib.request.urlopen(url, timeout=120) as response, cache.open('wb') as target:
        while chunk := response.read(1024*1024): target.write(chunk)
raw = cache.read_bytes()
assert hashlib.sha256(raw).hexdigest() == expected, 'Native archive checksum mismatch'
with zipfile.ZipFile(io.BytesIO(raw)) as z:
    print(z.namelist())
    for name in z.namelist():
        if name.endswith('.aar'):
            output = root / 'app/libs/libXray.aar'; output.parent.mkdir(parents=True, exist_ok=True); output.write_bytes(z.read(name))
        elif name.endswith(('geoip.dat', 'geosite.dat')):
            output = root / 'app/src/main/assets' / pathlib.PurePosixPath(name).name
            output.parent.mkdir(parents=True, exist_ok=True); output.write_bytes(z.read(name))
(root / 'assets').mkdir(exist_ok=True)
(root / 'assets/native-source.json').write_text(json.dumps({'version':'v26.11.9', 'url':url, 'sha256':expected}, indent=2))
geo_url = 'https://github.com/XTLS/Xray-core/releases/download/v26.11.9/Xray-windows-64.zip'
geo_sha = '1262522266337b824f07beb8e1375786b437683b3890105469088f8e17e0a69d'
geo_cache = cache_dir / 'Xray-windows-64-v26.11.9.zip'
if not geo_cache.exists(): geo_cache.write_bytes(urllib.request.urlopen(geo_url,timeout=120).read())
assert hashlib.sha256(geo_cache.read_bytes()).hexdigest() == geo_sha, 'Geodata archive checksum mismatch'
with zipfile.ZipFile(geo_cache) as z:
    for name in ('geoip.dat','geosite.dat'):
        (root / 'app/src/main/assets' / name).write_bytes(z.read(name))
print('Verified official native artifact')
