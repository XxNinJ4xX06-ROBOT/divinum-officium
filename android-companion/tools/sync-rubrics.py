#!/usr/bin/env python3
"""Bundle the separate [versions] sections from a local upstream checkout."""
import argparse
import json
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path, help='Local divinum-officium checkout; read only')
parser.add_argument('--check', action='store_true', help='Verify the bundled catalog without changing it')
args = parser.parse_args()

def options(path):
    text = path.read_text(encoding='utf-8')
    section = re.search(r'^\[versions\]\s*\n(.*?)(?=^\[|\Z)', text, re.M | re.S)
    if not section:
        raise ValueError(f'Missing [versions]: {path}')
    result = []
    for item in section.group(1).strip().split(','):
        label, separator, value = item.strip().partition('/')
        result.append({'label': label, 'value': value if separator else label})
    if len({x['value'] for x in result}) != len(result):
        raise ValueError(f'Duplicate versions: {path}')
    return result

catalog = {
    'source': 'https://github.com/DivinumOfficium/divinum-officium',
    'revision': subprocess.check_output(['git', '-C', str(args.source), 'rev-parse', 'HEAD'], text=True).strip(),
    'office': options(args.source / 'web/www/horas/horas.dialog'),
    'mass': options(args.source / 'web/www/missa/missa.dialog'),
}
target = Path(__file__).resolve().parents[1] / 'app/src/main/assets/rubrics.json'
if args.check:
    assert json.loads(target.read_text(encoding='utf-8')) == catalog, 'Bundled rubrics differ from upstream'
else:
    target.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"Office: {len(catalog['office'])}; Mass: {len(catalog['mass'])}; catalog verified" if args.check else f"Office: {len(catalog['office'])}; Mass: {len(catalog['mass'])}; catalog written")
