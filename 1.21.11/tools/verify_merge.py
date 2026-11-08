"""Validate the combined mod's runtime registration and packaged screens."""
import json
from pathlib import Path
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as jar:
    assert jar.testzip() is None
    assert len(jar.namelist()) == len(set(jar.namelist())), 'Duplicate archive entries'
    manifest = jar.read('META-INF/MANIFEST.MF')
    assert b'Fabric-Mapping-Namespace: intermediary' in manifest, 'Runtime namespace metadata missing'
    assert b'Fabric-Loom-Mixin-Remap-Type: static' in manifest, 'Mixin metadata missing'
    metadata = json.loads(jar.read('fabric.mod.json'))
    entry = 'dev.randomstuff.client.gui.FriendsMod'
    assert metadata['entrypoints']['client'].count(entry) == 1, 'Expected exactly one merged GUI initializer'
    for name in jar.namelist():
        if not (name.startswith('dev/randomstuff/client/gui/') and name.endswith('.class')):
            continue
        data = jar.read(name)
        assert int.from_bytes(data[6:8], 'big') == 65, name
        assert b'dev/shuclient' not in data, name
        assert b'net/minecraft/client/gui/screens/Screen' not in data, 'Unmapped Minecraft class'
    assert 'shuclient' not in metadata.get('depends', {}), 'Separate ShuClient still required'
    assert b'randomstuff' in jar.read('dev/randomstuff/client/gui/FriendsMod.class')
    assert b'ShuClient' not in jar.read('dev/randomstuff/client/gui/ModulesScreen.class')
    assert 'dev/randomstuff/client/gui/panels/SearchPanel.class' in jar.namelist()
    assert metadata['name'] == 'Random Stuff Client'
    assert 'randomstuffclient.gui.mixins.json' in metadata['mixins'], 'GUI keybind integration missing'
    assert 'dev/randomstuff/client/gui/SearchScreen.class' not in jar.namelist(), 'Standalone search tab reintroduced'
    assert 'dev/randomstuff/client/gui/GuiSearch.class' in jar.namelist(), 'Inline search engine missing'
    for name in jar.namelist():
        assert not name.startswith(('config/','configs/','randomstuffclient/','eggclient/','shuclient/')), 'User data bundled in release: '+name
        assert not name.endswith('/gui.properties'), 'Saved GUI preferences bundled in release'
    assert 'assets/randomstuffclient/font/ui.json' in jar.namelist(), 'GUI font definition missing'
    assert 'assets/randomstuffclient/textures/font/gui.png' in jar.namelist(), 'GUI font atlas missing'
    font = json.loads(jar.read('assets/randomstuffclient/font/ui.json'))
    assert all(provider['type'] != 'ttf' for provider in font['providers']), 'Native GUI TTF loader reintroduced'
    base_path = Path(__file__).resolve().parents[1] / 'libs/client-base.jar'
    with zipfile.ZipFile(base_path) as base:
        for name in base.namelist():
            if name.startswith('dev/randomstuff/client/config/ClientDataDirectory') or name.endswith('/') or name in {'fabric.mod.json', 'META-INF/MANIFEST.MF',
                    'dev/randomstuff/client/gui/panels/SearchPanel.class'}:
                continue
            assert jar.read(name) == base.read(name), 'Base client content changed: ' + name
print('PASS: combined initializer, remapped GUI classes, branding, and classic Search panel')
