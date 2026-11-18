"""Verify compiled release identity, signatures, zip/ELF alignment and ABI contents."""
import hashlib, json, os, pathlib, re, shutil, struct, subprocess, zipfile
root=pathlib.Path(__file__).resolve().parents[1]
sdk=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','C:/Android/Sdk')))
build=sdk/'build-tools/36.0.0'
version=re.search(r'versionName = "([^"]+)"',(root/'app/build.gradle.kts').read_text()).group(1)
code=re.search(r'versionCode = (\d+)',(root/'app/build.gradle.kts').read_text()).group(1)
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
for abi in ('arm64-v8a','armeabi-v7a','x86_64'):
    path=root/f'app/build/outputs/apk/release/app-{abi}-release.apk'
    signature=run('apksigner','verify','--verbose',path)
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
    run('zipalign','-c','-P','16','4',path)
    badging=run('aapt','dump','badging',path)
    assert "package: name='com.signet.client'" in badging
    assert f"versionName='{version}'" in badging and f"versionCode='{code}'" in badging
    assert 'android.permission.QUERY_ALL_PACKAGES' not in badging
    assert 'android.permission.CAMERA' in badging
    with zipfile.ZipFile(path) as archive:
        native=[n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
        assert native and all(n.split('/')[1]==abi for n in native)
        alignments=[]
        for name in native:
            data=archive.read(name);assert data[:4]==b'\x7fELF'
            wide=data[4]==2;order='<' if data[5]==1 else '>'
            offset=struct.unpack_from(order+('Q' if wide else 'I'),data,32 if wide else 28)[0]
            size,count=struct.unpack_from(order+'HH',data,54 if wide else 42)
            for i in range(count):
                entry=offset+i*size
                if struct.unpack_from(order+'I',data,entry)[0]==1:
                    alignment=struct.unpack_from(order+('Q' if wide else 'I'),data,entry+(48 if wide else 28))[0]
                    assert alignment>=16384,(abi,name,alignment)
                    alignments.append(alignment)
    rows.append({'abi':abi,'version':version,'versionCode':int(code),'bytes':path.stat().st_size,'sha256':digest(path),'signatureV2':'PASS','zip16k':'PASS','elf16k':'PASS','manifest':'PASS','nativeLibraries':native})
    print(abi,version,path.stat().st_size,'PASS')
out=root/'tests/evidence/0.2.0';out.mkdir(parents=True,exist_ok=True)
(out/'apk-verification.json').write_text(json.dumps(rows,indent=2)+'\n')
