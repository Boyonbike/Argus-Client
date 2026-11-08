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
            if name != 'dev/argus/client/config/ClientDataDirectory.class':
                assert b'randomstuff' not in data.lower(), name
                assert b'eggclient' not in data.lower(), name
                assert b'shuclient' not in data.lower(), name
    assert 'dev/argus/client/gui/panels/SearchPanel.class' in archive.namelist()
print('PASS: Argus Client branding, JAR integrity, entrypoints, and Search panel')
