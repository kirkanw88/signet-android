"""Verify explicitly selected Maven Central artifacts; never edits Gradle trust.

Usage: python scripts/verify-central-metadata.py group:name:version filename [...]
Pairs must be supplied explicitly. Output JSON can be retained as provenance.
SHA-256 is computed from HTTPS artifact bytes, cross-checked on both Central
hostnames and against Central's published SHA-1 sidecar. SHA-1 alone is not
used as the trust decision. Missing sidecars or unequal bytes fail closed.
"""
import hashlib
import json
import re
import sys
import urllib.parse
import urllib.request


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        effective = urllib.parse.urlsplit(response.url)
        requested = urllib.parse.urlsplit(url)
        if effective.scheme != 'https' or effective.netloc != requested.netloc:
            raise ValueError(f'Unexpected redirect for {url}')
        return response.read()


def verify(coordinate, filename):
    group, name, version = coordinate.split(':')
    if not all(re.fullmatch(r'[A-Za-z0-9_.-]+', p) for p in (group, name, version, filename)):
        raise ValueError('Invalid coordinate or artifact filename')
    path = f'{group.replace(".", "/")}/{name}/{version}/{filename}'
    urls = [f'https://{host}/maven2/{path}' for host in ('repo.maven.apache.org', 'repo1.maven.org')]
    raw = fetch(urls[0])
    if fetch(urls[1]) != raw:
        raise ValueError(f'Central hostnames disagree: {coordinate}/{filename}')
    sha1 = hashlib.sha1(raw).hexdigest()
    published = fetch(urls[0] + '.sha1').decode('ascii').strip().split()[0].lower()
    if sha1 != published:
        raise ValueError(f'Published checksum mismatch: {coordinate}/{filename}')
    return {'coordinate': coordinate, 'artifact': filename, 'bytes': len(raw),
            'sha256': hashlib.sha256(raw).hexdigest(), 'publishedSha1': sha1,
            'sources': urls, 'sidecar': urls[0] + '.sha1'}


if __name__ == '__main__':
    args = sys.argv[1:]
    if not args or len(args) % 2:
        raise SystemExit(__doc__)
    print(json.dumps([verify(*args[i:i+2]) for i in range(0, len(args), 2)], indent=2))
