"""Pinned public NASA Black Marble, reduced for bounded on-device texture memory."""
import hashlib,io,json,pathlib,urllib.request
from PIL import Image
root=pathlib.Path(__file__).resolve().parents[1]
url='https://assets.science.nasa.gov/content/dam/science/esd/eo/images/imagerecords/144000/144898/BlackMarble_2016_01deg.jpg'
expected='d87de751a264e4f8ff69c68de5dab9606daee87a6f15ae743c93200743bd7ec1'
raw=urllib.request.urlopen(url,timeout=60).read();assert hashlib.sha256(raw).hexdigest()==expected
output=root/'app/src/main/assets/night.jpg'
with Image.open(io.BytesIO(raw)) as image:image.convert('RGB').resize((2048,1024),Image.Resampling.LANCZOS).save(output,quality=90,optimize=True)
(root/'assets/night-source.json').write_text(json.dumps({'url':url,'originalSha256':expected,'outputSha256':hashlib.sha256(output.read_bytes()).hexdigest(),'size':[2048,1024],'credit':'NASA Earth Observatory / Black Marble team, Suomi NPP VIIRS, 2016','terms':'https://www.nasa.gov/nasa-brand-center/images-and-media/'},indent=2)+'\n')
print('Night texture',output.stat().st_size,'bytes')
