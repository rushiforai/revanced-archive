"""Publish metadata for an existing public stable GitHub release. No credentials needed."""
import argparse
from datetime import datetime
import hashlib
import io
import json
from pathlib import Path
import re
import urllib.request
import zipfile

REPO = 'SergeyBelentev/voice-over-translation-revanced'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('tag', help='An already published stable tag, for example v0.3.1')
args = parser.parse_args()
if not re.fullmatch(r'v\d+\.\d+\.\d+', args.tag):
    parser.error('Use a stable vMAJOR.MINOR.PATCH tag')

def read(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'VOT-source-metadata'})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()

release = json.loads(read(f'https://api.github.com/repos/{REPO}/releases/tags/{args.tag}'))
assert release['tag_name'] == args.tag and not release['draft'] and not release['prerelease']
version = args.tag[1:]
name = f'vot-standalone-{version}.rvp'
asset = next(a for a in release['assets'] if a['name'] == name)
download_url = f'https://github.com/{REPO}/releases/download/{args.tag}/{name}'
assert asset['browser_download_url'] == download_url
binary = read(download_url)
digest = 'sha256:' + hashlib.sha256(binary).hexdigest()
assert asset['size'] == len(binary) and asset['digest'] == digest
with zipfile.ZipFile(io.BytesIO(binary)) as bundle:
    assert 'classes.dex' in bundle.namelist()
    assert 'standalone-vot/translation.rve' in bundle.namelist()
    assert not any(n.startswith('extensions/') for n in bundle.namelist())
    manifest = bundle.read('META-INF/MANIFEST.MF').decode('utf-8')
    assert f'Version: {version}' in manifest.splitlines()

# Manager 2.6.0 uses kotlinx.datetime.LocalDateTime, not Instant: no trailing Z/offset.
published = release['published_at']
assert published.endswith('Z')
created_at = datetime.fromisoformat(published[:-1]).isoformat(timespec='seconds')
metadata = {
    'version': version,
    'created_at': created_at,
    'download_url': download_url,
    'description': release['body'] or f'VOT {version}',
}
path = Path(__file__).resolve().parents[1] / 'patches.json'
path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Updated patches.json: {version}; public RVP size, SHA-256 and bundle version verified.')
print('Commit and push patches.json to main to make this version available to Manager.')
