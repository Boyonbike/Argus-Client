"""Check packaged display branding and preserve the client entrypoint."""
import json
from pathlib import Path
import sys
import zipfile

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
        assert not any(old in name.lower() for old in ('randomstuff', 'random-stuff', 'eggclient', 'shuclient')), name
        if name.endswith(('.class', '.ini')):
            data = archive.read(name)
            assert b'Vortex Client' not in data, name
            # Only the config import/rename classes may name old clients, to update saved settings.
            if name.startswith(('dev/argus/client/config/ClientDataDirectory', 'dev/argus/client/config/LegacyConfigRenamer')):
                continue
            for old in (b'zio', b'spectrumdevs', b'mio', b'randomstuff', b'eggclient', b'shuclient'):
                assert old not in data.lower(), (name, old)
    for old in (b'cypphi', b'discord.com/invite', b'zio', b'mio'):
        assert old not in archive.read('fabric.mod.json').lower(), old
    assert 'dev/argus/client/gui/panels/SearchPanel.class' in archive.namelist()
print('PASS: Argus Client branding, JAR integrity, entrypoints, and Search panel')
