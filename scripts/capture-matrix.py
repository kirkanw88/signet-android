"""Device layout assertions and baseline screenshots. Requires an installed APK."""
import json, pathlib, re, subprocess, time, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'tests/evidence/screenshots';out.mkdir(parents=True,exist_ok=True)
def adb(*args):
    return subprocess.check_output(['adb',*args],timeout=30)
matrix=[(360,640,1.0),(393,851,1.0),(412,915,1.0),(600,960,1.0),(800,1280,1.0),(640,360,1.0),(360,640,1.3)]
results=[]
try:
    adb('shell','wm','density','160')
    for width,height,font in matrix:
        adb('shell','settings','put','system','font_scale',str(font))
        adb('shell','wm','size',f'{width}x{height}')
        adb('shell','am','force-stop','com.signet.client')
        adb('shell','am','start','-W','-n','com.signet.client/.MainActivity')
        time.sleep(2)
        name=f'{width}x{height}-font-{font}'
        (out/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
        adb('shell','uiautomator','dump','/sdcard/signet-layout.xml')
        raw=adb('shell','cat','/sdcard/signet-layout.xml')
        (out/f'{name}.xml').write_bytes(raw)
        nodes=ET.fromstring(raw).iter('node')
        found=False
        for node in nodes:
            if node.get('text') in ('Добавить подписку','Подключиться','Отключиться'):
                x0,y0,x1,y1=map(int,re.findall(r'\d+',node.get('bounds','')))
                found = x1>x0 and y1>y0 and 0<=x0<x1<=width and 0<=y0<y1<=height
                if found:break
        results.append({'dp':[width,height],'fontScale':font,'connectVisible':'PASS' if found else 'FAIL','screenshot':f'{name}.png'})
        print(name,results[-1]['connectVisible'],flush=True)
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset');adb('shell','settings','put','system','font_scale','1.0')
    (out/'matrix.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
assert all(r['connectVisible']=='PASS' for r in results),'A connection button is outside the viewport'
