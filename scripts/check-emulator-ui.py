"""Fail on visible system ANR dialogs; dumpsys lastanr alone can miss them."""
import pathlib, subprocess, sys
out=pathlib.Path('tests/evidence/runtime');out.mkdir(parents=True,exist_ok=True)
stage=sys.argv[1]
def adb(*args):return subprocess.check_output(['adb',*args],timeout=30)
adb('shell','uiautomator','dump','/sdcard/signet-system-window.xml')
xml=adb('shell','cat','/sdcard/signet-system-window.xml')
(out/f'{stage}-window.xml').write_bytes(xml)
(out/f'{stage}-screen.png').write_bytes(adb('exec-out','screencap','-p'))
text=xml.decode(errors='replace').lower().replace('\u2019',"'")
assert "isn't responding" not in text and 'is not responding' not in text, 'System ANR dialog blocks the emulator'
