"""Check that every app-owned text has all four locales and compatible format arguments."""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / 'app/src/main/res'
FORMAT = re.compile(r'%(?!%)(\d+)\$[+\-0 #,(]*\d*(?:\.\d+)?([a-zA-Z])')

def formats(text):
    return sorted(FORMAT.findall(text))

def catalog(folder):
    result = {}
    for name in ['strings.xml', 'ui_strings.xml', 'runtime_strings.xml']:
        for node in ET.parse(RESOURCE_ROOT / folder / name).getroot():
            key = (node.tag, node.attrib['name'])
            assert key not in result, f'Duplicate {folder}: {key}'
            if node.tag == 'plurals':
                values = {item.attrib['quantity']: ''.join(item.itertext()) for item in node}
                assert 'other' in values, (folder, key)
            else:
                values = {'text': ''.join(node.itertext())}
            assert all(value.strip() for value in values.values()), (folder, key)
            result[key] = values
    return result

base = catalog('values')
for folder in ['values-zh', 'values-es', 'values-it']:
    localized = catalog(folder)
    assert set(localized) == set(base), (folder, set(base) ^ set(localized))
    for key, values in localized.items():
        expected = formats(next(iter(base[key].values())))
        for value in values.values():
            assert formats(value) == expected, (folder, key, value)
for source in (ROOT / 'app/src/main/java').rglob('*.kt'):
    for kind, name in re.findall(r'R\.(string|plurals)\.(\w+)', source.read_text(encoding='utf-8-sig')):
        assert (kind, name) in base, (source, kind, name)
report = {'locales': ['en', 'zh', 'es', 'it'], 'resourcesPerLocale': len(base),
          'strings': sum(key[0] == 'string' for key in base),
          'plurals': sum(key[0] == 'plurals' for key in base), 'status': 'PASS'}
print(json.dumps(report, ensure_ascii=False, indent=2))
