"""Compile/link the exact GLSL ES shader sources in a local WebGL1 context.
This is a portable shader check, not an Android GPU/FPS measurement.
"""
import json,pathlib,re,textwrap
from playwright.sync_api import sync_playwright
root=pathlib.Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/signet/client/graphics/GlobeView.kt').read_text(encoding='utf-8')
shaders=[textwrap.dedent(re.search(r'shader\(GLES20.GL_'+kind+r',\s*"""(.*?)"""',source,re.S).group(1)).strip() for kind in ('VERTEX_SHADER','FRAGMENT_SHADER')]
with sync_playwright() as p:
    browser=None
    for channel in ('chrome','msedge'):
        try:browser=p.chromium.launch(channel=channel,headless=True,args=['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']);break
        except Exception:pass
    if browser is None:raise RuntimeError('Local Chrome/Edge needed')
    page=browser.new_page()
    result=page.evaluate('''sources => {const gl=document.createElement('canvas').getContext('webgl');if(!gl)throw Error('WebGL unavailable');const program=gl.createProgram();for(let i=0;i<2;i++){let shader=gl.createShader(i===0?gl.VERTEX_SHADER:gl.FRAGMENT_SHADER);gl.shaderSource(shader,sources[i]);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(shader));gl.attachShader(program,shader);}gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));return {shaderCompile:'PASS',programLink:'PASS',version:gl.getParameter(gl.VERSION)};}''',shaders)
    browser.close()
version=max(re.findall(r'versionName = "([^"]+)"',(root/'app/build.gradle.kts').read_text()),key=lambda value:tuple(map(int,value.split("."))))
out=root/'tests/evidence'/version;out.mkdir(parents=True,exist_ok=True)
(out/'glsl-check.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
