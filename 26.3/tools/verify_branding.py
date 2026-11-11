"""Check packaged display branding and preserve the client entrypoint."""
import json
from pathlib import Path
import re
import sys
import zipfile

legacy_names = re.compile(
    rb'random[\s_-]*stuff|egg[\s_-]*client|shu[\s_-]*client|vortex[\s_-]*client'
    rb'|\b(?:zio|mio)\b|(?:zio|mio)client|spectrumdevs|cypphi|'
    + 'ᴍɪᴏ'.encode('utf-8'),
    re.IGNORECASE,
)
scanned_types = ('.class', '.ini', '.json', '.tsv', '.accesswidener', '.mcmeta')
legacy_migration_classes = (
    'dev/argus/client/config/ClientDataDirectory',
    'dev/argus/client/config/LegacyConfigRenamer',
)

jar = Path(sys.argv[1])
with zipfile.ZipFile(jar) as archive:
    assert archive.testzip() is None, 'Corrupt JAR entry'
    metadata = json.loads(archive.read('fabric.mod.json'))
    assert metadata['name'] == 'Argus Client', metadata['name']
    assert metadata['id'] == 'argusclient', 'Expected Argus Client mod ID'
    for entry in metadata['entrypoints'].values():
        for name in entry:
            assert name.replace('.', '/') + '.class' in archive.namelist(), name
    for name in archive.namelist():
        assert not legacy_names.search(name.encode('utf-8')), name
        if name.startswith(legacy_migration_classes) and name.endswith('.class'):
            continue
        if name.endswith(scanned_types):
            match = legacy_names.search(archive.read(name))
            assert match is None, (name, match.group() if match else None)
    assert b'discord.com/invite' not in archive.read('fabric.mod.json').lower()
    assert 'dev/argus/client/gui/panels/SearchPanel.class' in archive.namelist()
print('PASS: Argus Client branding, JAR integrity, entrypoints, and Search panel')
