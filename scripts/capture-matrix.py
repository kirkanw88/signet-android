"""Device layout assertions and baseline screenshots. Requires an installed APK."""
import json, pathlib, re, subprocess, time, xml.etree.ElementTree as ET, os
root=pathlib.Path(__file__).resolve().parents[1]
package=os.environ.get('SIGNET_TEST_PACKAGE','com.signet.client')
out=root/'tests/evidence/screenshots';out.mkdir(parents=True,exist_ok=True)
def adb(*args):
    return subprocess.check_output(['adb',*args],timeout=30)
# Small devices, landscape, font scaling up to the Android accessibility maximum tested in CI.
matrix=[(320,640,1.0),(360,640,1.0),(393,851,1.0),(412,915,1.0),(600,960,1.0),(800,1280,1.0),(640,360,1.0),(360,640,1.3),(360,640,1.6),(360,640,2.0),(320,640,1.6),(412,915,2.0)]
results=[]
try:
    adb('shell','wm','density','160')
    for width,height,font in matrix:
        adb('shell','settings','put','system','font_scale',str(font))
        adb('shell','wm','size',f'{width}x{height}')
        adb('shell','am','force-stop',package)
        adb('shell','am','start','-W','-n',f'{package}/com.signet.client.MainActivity')
        time.sleep(2)
        name=f'{width}x{height}-font-{font}'
        (out/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
        adb('shell','uiautomator','dump','/sdcard/signet-layout.xml')
        raw=adb('shell','cat','/sdcard/signet-layout.xml')
        (out/f'{name}.xml').write_bytes(raw)
        def connect_visible(xml):
            for node in ET.fromstring(xml).iter('node'):
                if node.get('text') in ('Добавить подписку','Подключиться','Отключиться','Отменить подключение','Повторить подключение'):
                    coords=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
                    if len(coords)==4:
                        x0,y0,x1,y1=coords
                        if x1>x0 and y1>y0 and 0<=x0<x1<=width and 0<=y0<y1<=height:
                            return True
            return False
        found=connect_visible(raw)
        # Large accessibility fonts may require scrolling the Home panel.
        # Verify the primary action remains reachable instead of falsely failing on initial viewport.
        scrolled=0
        while not found and scrolled<4:
            adb('shell','input','swipe',str(width//2),str(int(height*.78)),str(width//2),str(int(height*.32)),'300')
            time.sleep(.35)
            adb('shell','uiautomator','dump','/sdcard/signet-layout.xml')
            raw=adb('shell','cat','/sdcard/signet-layout.xml')
            found=connect_visible(raw)
            scrolled+=1
        results.append({'px':[width,height],'fontScale':font,'connectReachable':'PASS' if found else 'FAIL','requiresScroll':scrolled>0,'screenshot':f'{name}.png'})
        print(name,results[-1]['connectReachable'],f'scrolls={scrolled}',flush=True)
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset');adb('shell','settings','put','system','font_scale','1.0')
    (out/'matrix.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
assert all(r['connectReachable']=='PASS' for r in results),'Connection action is unreachable at one or more font scales'
