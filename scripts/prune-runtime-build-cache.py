"""Reclaim this job's verified dependency cache after all APKs have been compiled."""
import os,pathlib,shutil
root=pathlib.Path.cwd().resolve()
expected=root/'.gradle'/'runtime-user-home'
home=pathlib.Path(os.environ['GRADLE_USER_HOME']).resolve()
assert home==expected.resolve() and home.is_relative_to(root)
cache=home/'caches'
assert not cache.is_symlink() and cache.resolve().is_relative_to(expected.resolve())
print('Free before:',shutil.disk_usage(root).free)
if cache.exists():shutil.rmtree(cache)
print('Free after:',shutil.disk_usage(root).free)
