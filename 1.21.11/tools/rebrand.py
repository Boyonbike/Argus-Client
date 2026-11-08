"""Rename display text without relocating bytecode or Minecraft resource IDs."""
from pathlib import Path
import json
import struct
import sys
import zipfile

BRAND = b'Random Stuff Client'

def rename_text(value):
    for old in (b'Vortex Client', b'Egg Client', b'EggClient'):
        value = value.replace(old, BRAND)
    return value


def rename_class(data):
    # Preserve all constant-pool indices, instructions, descriptors and frames.
    # Only UTF8 entries referenced by CONSTANT_String can be display literals.
    count = int.from_bytes(data[8:10], 'big')
    entries = []
    strings = set()
    offset, index = 10, 1
    while index < count:
        start = offset
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = int.from_bytes(data[offset:offset + 2], 'big')
            offset += 2 + length
        elif tag == 8:
            strings.add(int.from_bytes(data[offset:offset + 2], 'big'))
            offset += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
        elif tag in (7, 16, 19, 20):
            offset += 2
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f'Unsupported constant pool tag {tag}')
        entries.append((index, tag, data[start:offset]))
        index += 2 if tag in (5, 6) else 1
    result = bytearray(data[:10])
    for index, tag, raw in entries:
        if tag == 1 and index in strings:
            value = rename_text(raw[3:])
            raw = b'\x01' + struct.pack('>H', len(value)) + value
        result.extend(raw)
    result.extend(data[offset:])
    return bytes(result)


def rebrand(source, destination):
    if source.resolve() == destination.resolve():
        raise ValueError('Use distinct input and output paths')
    changed = []
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(destination, 'w') as output:
        for entry in original.infolist():
            data = original.read(entry)
            before = data
            if entry.filename.endswith('.class'):
                # These literals identify historical settings to migrate; retain them.
                if not entry.filename.endswith('/config/LegacyBranding.class'):
                    data = rename_class(data)
            elif entry.filename.endswith('.ini'):
                data = rename_text(data)
            elif entry.filename == 'fabric.mod.json':
                metadata = json.loads(data)
                metadata['name'] = BRAND.decode()
                metadata['description'] = rename_text(metadata['description'].encode()).decode()
                data = (json.dumps(metadata, indent=2) + '\n').encode()
            output.writestr(entry, data)
            if data != before:
                changed.append(entry.filename)
    print(f'Rebranded {len(changed)} entries in {destination}')

if __name__ == '__main__':
    rebrand(Path(sys.argv[1]), Path(sys.argv[2]))
