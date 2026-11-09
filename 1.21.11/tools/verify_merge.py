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
    entry = 'dev.argus.client.gui.FriendsMod'
    assert metadata['entrypoints']['client'].count(entry) == 1, 'Expected exactly one merged GUI initializer'
    for name in jar.namelist():
        if not (name.startswith('dev/argus/client/gui/') and name.endswith('.class')):
            continue
        data = jar.read(name)
        assert int.from_bytes(data[6:8], 'big') == 65, name
        assert b'dev/shuclient' not in data, name
        assert b'net/minecraft/client/gui/screens/Screen' not in data, 'Unmapped Minecraft class'
    assert 'shuclient' not in metadata.get('depends', {}), 'Separate ShuClient still required'
    assert b'argus' in jar.read('dev/argus/client/gui/FriendsMod.class')
    assert b'ShuClient' not in jar.read('dev/argus/client/gui/ModulesScreen.class')
    assert 'dev/argus/client/gui/panels/SearchPanel.class' in jar.namelist()
    assert metadata['name'] == 'Argus Client'
    assert 'argusclient.gui.mixins.json' in metadata['mixins'], 'GUI keybind integration missing'
    assert 'dev/argus/client/gui/SearchScreen.class' not in jar.namelist(), 'Standalone search tab reintroduced'
    assert 'dev/argus/client/gui/GuiSearch.class' in jar.namelist(), 'Inline search engine missing'
    for name in jar.namelist():
        assert not name.startswith(('config/','configs/','argusclient/')), 'User data bundled in release: '+name
        assert not name.endswith('/gui.properties'), 'Saved GUI preferences bundled in release'
    assert 'assets/argusclient/font/ui.json' in jar.namelist(), 'GUI font definition missing'
    assert 'assets/argusclient/textures/font/gui.png' in jar.namelist(), 'GUI font atlas missing'
    font = json.loads(jar.read('assets/argusclient/font/ui.json'))
    assert all(provider['type'] != 'ttf' for provider in font['providers']), 'Native GUI TTF loader reintroduced'
    base_path = Path(__file__).resolve().parents[1] / 'libs/client-base.jar'
    with zipfile.ZipFile(base_path) as base:
        for name in base.namelist():
            if name.startswith('dev/argus/client/config/ClientDataDirectory') or name.startswith('dev/argus/client/modules/movement/ElytraFlyModule') or name.startswith('dev/argus/client/managers/HudModuleRegistry') or name.endswith('/') or name in {'fabric.mod.json', 'META-INF/MANIFEST.MF', 'argusclient.mixins.json', 'dev/argus/mixin/LoadingOverlayMixin.class',
                    'dev/argus/client/gui/panels/SearchPanel.class'}:
                continue
            assert jar.read(name) == base.read(name), 'Base client content changed: ' + name
    assert 'dev/argus/client/modules/movement/ElytraFlyModule.class' in jar.namelist(), 'ElytraFly override missing'
    assert 'dev/argus/client/utils/movement/VolytraElytraFlyHandler.class' in jar.namelist(), 'Volytra handler missing'
    assert 'dev/argus/client/modules/hud/VolytraSpeedHudModule.class' in jar.namelist(), 'Volytra speed HUD missing'
    assert 'dev/argus/client/managers/HudModuleRegistry.class' in jar.namelist(), 'HUD registry override missing'
    mixins = json.loads(jar.read('argusclient.mixins.json'))
    assert 'LoadingOverlayMixin' not in mixins.get('client', []) + mixins.get('mixins', [])
print('PASS: combined initializer, remapped GUI classes, branding, and classic Search panel')
