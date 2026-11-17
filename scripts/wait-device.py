import subprocess, time
deadline=time.monotonic()+180
while time.monotonic()<deadline:
    try:
        result=subprocess.run(['adb','shell','getprop','sys.boot_completed'],capture_output=True,timeout=5)
        if result.stdout.strip()==b'1':
            subprocess.run(['adb','shell','cmd','package','list','packages','com.signet.client'],check=True,timeout=10)
            print('Android boot completed',flush=True)
            break
    except (subprocess.TimeoutExpired,subprocess.CalledProcessError):pass
    time.sleep(1)
else:raise TimeoutError('Android did not boot within 180 seconds')
