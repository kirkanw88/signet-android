"""Package ABI APKs and reviewable sources without build caches or local keys."""
import argparse, hashlib, pathlib, shutil, zipfile, re, subprocess
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'release';out.mkdir(exist_ok=True)
parser=argparse.ArgumentParser();parser.add_argument('--edition',choices=('signet','external','all'),default='all');args=parser.parse_args()
editions=('signet','external') if args.edition=='all' else (args.edition,)
metadata={edition:__import__('json').loads((root/f'app/build/outputs/apk/{edition}/release/output-metadata.json').read_text())['elements'][0] for edition in editions}
version=max(item['versionName'] for item in metadata.values())

for edition in editions:
    version=metadata[edition]['versionName']
    code=str(metadata[edition]['versionCode'])
    prefix='signet' if edition=='signet' else 'signet-external'
    for abi in ('arm64-v8a','armeabi-v7a','x86_64'):
        shutil.copy2(root/f'app/build/outputs/apk/{edition}/release/app-{edition}-{abi}-release.apk',out/f'{prefix}-{version}-{abi}.apk')
archive=out/f'signet-android-{version}-source.zip'
excluded={'.gradle','.kotlin','.git','build','release','.idea','__pycache__'}
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    # Git's tracked set excludes local credentials, generated test keys and dependency caches.
    tracked=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode('utf-8').split('\0')
    for name in sorted(filter(None,tracked)):
        relative=pathlib.Path(name);path=root/relative
        if not path.is_file() or any(p in excluded for p in relative.parts):continue
        if not path.resolve().is_relative_to(root.resolve()):continue
        if path.name in ('local.properties','secrets.properties','keystore.properties') or path.suffix in ('.jks','.keystore','.key','.pem','.p12','.pfx'):continue
        if path.name.startswith('.env') and path.name!='.env.example':continue
        z.write(path,str(pathlib.Path('signet-android')/relative))
rows=[]
for path in sorted(out.iterdir()):
    if path.suffix not in ('.apk','.zip') or version not in path.name:continue
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    rows.append(f'{digest}  {path.name}')
    print(path.name,path.stat().st_size,digest,flush=True)
(out/'SHA256SUMS.txt').write_text('\n'.join(rows)+'\n',encoding='utf-8')
