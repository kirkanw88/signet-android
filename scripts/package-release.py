"""Package ABI APKs and reviewable sources without build caches or local keys."""
import hashlib, pathlib, shutil, zipfile, re
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'release';out.mkdir(exist_ok=True)
version=re.search(r'versionName = "([^"]+)"',(root/'app/build.gradle.kts').read_text()).group(1)
for abi in ('arm64-v8a','armeabi-v7a','x86_64'):
    shutil.copy2(root/f'app/build/outputs/apk/release/app-{abi}-release.apk',out/f'signet-{version}-{abi}.apk')
archive=out/f'signet-android-{version}-source.zip'
excluded={'.gradle','.kotlin','.git','build','release','.idea','__pycache__'}
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for path in sorted(root.rglob('*')):
        relative=path.relative_to(root)
        if not path.is_file() or any(p in excluded for p in relative.parts):continue
        if path.name in ('local.properties',) or path.suffix in ('.jks','.keystore'):continue
        z.write(path,str(pathlib.Path('signet-android')/relative))
rows=[]
for path in sorted(out.iterdir()):
    if path.suffix not in ('.apk','.zip') or version not in path.name:continue
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    rows.append(f'{digest}  {path.name}')
    print(path.name,path.stat().st_size,digest,flush=True)
(out/'SHA256SUMS.txt').write_text('\n'.join(rows)+'\n',encoding='utf-8')
