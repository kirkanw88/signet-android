"""Deterministic conversion of the supplied monochrome logo into native vectors.
No image synthesis. Black pixels become holes using even-odd vector paths.
"""
import pathlib, shutil, math
from PIL import Image
root=pathlib.Path(__file__).resolve().parents[1]
source=pathlib.Path('C:/Users/Администратор/Downloads/photo_2025-07-23_13-19-22.jpg')
branding=root/'assets/branding';branding.mkdir(parents=True,exist_ok=True)
shutil.copyfile(source,branding/'original.jpg')
im=Image.open(source).convert('L');w,h=im.size;pixels=im.load()
def contours(box):
    x0,y0,x1,y1=box
    def white(x,y):return x0<=x<x1 and y0<=y<y1 and pixels[x,y]>=128
    edges={}
    for y in range(y0,y1):
        for x in range(x0,x1):
            if not white(x,y):continue
            for a,b,other in [((x,y),(x+1,y),(x,y-1)),((x+1,y),(x+1,y+1),(x+1,y)),((x+1,y+1),(x,y+1),(x,y+1)),((x,y+1),(x,y),(x-1,y))]:
                if not white(*other):edges.setdefault(a,[]).append(b)
    paths=[]
    while edges:
        start=next(iter(edges));p=start;chain=[]
        while True:
            chain.append(p);values=edges[p];nxt=values.pop()
            if not values:del edges[p]
            p=nxt
            if p==start:break
            if p not in edges:break
        if len(chain)<12:continue
        # Collapse collinear pixel boundary segments without altering the original contour.
        simple=[]
        for i,p in enumerate(chain):
            a=chain[i-1];b=chain[(i+1)%len(chain)]
            if (p[0]-a[0])*(b[1]-p[1])!=(p[1]-a[1])*(b[0]-p[0]):simple.append(p)
        if len(simple)>=3: paths.append('M'+' L'.join(f'{x-x0},{y-y0}' for x,y in simple)+' Z')
    return ' '.join(paths),x1-x0,y1-y0
res=root/'app/src/main/res'
for name,box in [('signet_logo',(50,118,592,509)),('signet_mark',(50,118,592,360))]:
    path,vw,vh=contours(box)
    (branding/f'{name}.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}"><path fill="white" fill-rule="evenodd" d="{path}"/></svg>')
    (res/'drawable').mkdir(parents=True,exist_ok=True)
    vector=f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{vw}dp" android:height="{vh}dp" android:viewportWidth="{vw}" android:viewportHeight="{vh}"><path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{path}"/></vector>'
    (res/'drawable'/f'{name}.xml').write_text(vector)
    if name=='signet_mark':
        (res/'drawable/ic_notification.xml').write_text(vector)
        # Adaptive icon artwork is centered inside the 66/108 safe circle.
        scale=60/vw;tx=(108-vw*scale)/2;ty=(108-vh*scale)/2
        foreground=f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><group android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{tx}" android:translateY="{ty}"><path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{path}"/></group></vector>'
        (res/'drawable/ic_launcher_foreground.xml').write_text(foreground)
(res/'mipmap-anydpi-v26').mkdir(parents=True,exist_ok=True)
(res/'mipmap-anydpi-v26/ic_launcher.xml').write_text('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/icon_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/><monochrome android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>')
print('Original brand converted to transparent SVG and Android VectorDrawable; no generated artwork used.')
