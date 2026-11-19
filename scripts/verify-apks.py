"""Verify compiled release identity, signatures, zip/ELF alignment and ABI contents."""
import argparse, hashlib, json, os, pathlib, re, shutil, struct, subprocess, zipfile
root=pathlib.Path(__file__).resolve().parents[1]
sdk=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','C:/Android/Sdk')))
build=sdk/'build-tools/36.0.0'
parser=argparse.ArgumentParser();parser.add_argument('--edition',choices=('signet','external','all'),default='all');args=parser.parse_args()
editions=('signet','external') if args.edition=='all' else (args.edition,)
metadata={edition:__import__('json').loads((root/f'app/build/outputs/apk/{edition}/release/output-metadata.json').read_text())['elements'][0] for edition in editions}
version=max(item['versionName'] for item in metadata.values())

def run(tool,*args):
    suffix='.bat' if os.name=='nt' and tool=='apksigner' else '.exe' if os.name=='nt' else ''
    arguments=[str(a.relative_to(root)) if isinstance(a,pathlib.Path) and a.is_relative_to(root) else str(a) for a in args]
    return subprocess.check_output([str(build/(tool+suffix)),*arguments],cwd=root,stderr=subprocess.STDOUT).decode(errors='replace')
def digest(path):
    result=hashlib.sha256()
    with path.open('rb') as stream:
        while block:=stream.read(1024*1024):result.update(block)
    return result.hexdigest()
rows=[]
certificates=set()
for edition in editions:
    version=metadata[edition]['versionName']
    code=str(metadata[edition]['versionCode'])
    package='com.signet.client'+('.external' if edition=='external' else '')
    for abi in ('arm64-v8a','armeabi-v7a','x86_64'):
        path=root/f'app/build/outputs/apk/{edition}/release/app-{edition}-{abi}-release.apk'
        signature=run('apksigner','verify','--verbose',path)
        assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
        certificate=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',run('apksigner','verify','--print-certs',path)).group(1)
        certificates.add(certificate)
        run('zipalign','-c','-P','16','4',path)
        badging=run('aapt','dump','badging',path)
        assert f"package: name='{package}'" in badging
        manifest=run('aapt','dump','xmltree',path,'AndroidManifest.xml')
        assert ('oauth' in manifest)==(edition=='signet')
        assert ('signet-external' in manifest)==(edition=='external')
        assert "application-label:'Signet VPN'" in badging
        assert f"versionName='{version}'" in badging and f"versionCode='{code}'" in badging
        assert 'android.permission.QUERY_ALL_PACKAGES' not in badging
        assert 'android.permission.CAMERA' in badging
        with zipfile.ZipFile(path) as archive:
            dex=b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
            for marker in (b'https://signet-cab.com',b'/api/mobile/v1',b'/support/messages'):
                assert (marker in dex)==(edition=='signet'), (edition,marker)
            native=[n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
            assert native and all(n.split('/')[1]==abi for n in native)
            alignments=[]
            for name in native:
                data=archive.read(name);assert data[:4]==b'\x7fELF'
                wide=data[4]==2;order='<' if data[5]==1 else '>'
                assert struct.unpack_from(order+'H',data,18)[0]=={'arm64-v8a':183,'armeabi-v7a':40,'x86_64':62}[abi]
                offset=struct.unpack_from(order+('Q' if wide else 'I'),data,32 if wide else 28)[0]
                size,count=struct.unpack_from(order+'HH',data,54 if wide else 42)
                for i in range(count):
                    entry=offset+i*size
                    if struct.unpack_from(order+'I',data,entry)[0]==1:
                        alignment=struct.unpack_from(order+('Q' if wide else 'I'),data,entry+(48 if wide else 28))[0]
                        assert alignment>=16384,(abi,name,alignment)
                        alignments.append(alignment)
        rows.append({'edition':edition,'package':package,'abi':abi,'version':version,'versionCode':int(code),'bytes':path.stat().st_size,'sha256':digest(path),'certificateSha256':certificate,'cabinetApiPresent':edition=='signet','signatureV2':'PASS','zip16k':'PASS','elf16k':'PASS','manifest':'PASS','nativeLibraries':native})
        print(edition,abi,version,path.stat().st_size,'PASS')
assert len(certificates)==1, 'Editions must share the release signing identity'
out=root/'tests/evidence'/version;out.mkdir(parents=True,exist_ok=True)
(out/'apk-verification.json').write_text(json.dumps(rows,indent=2)+'\n')
