#!/usr/bin/env python3
"""Check out the recorded OE build infrastructure beside this BSP."""
import json
from pathlib import Path
import subprocess


def main():
    bsp = Path(__file__).resolve().parents[1]
    for item in json.loads((bsp / 'docs/build-host-sources.json').read_text()):
        target = bsp.parent / item['name']
        if not target.exists():
            subprocess.run(['git', 'clone', '--no-checkout', item['url'], str(target)], check=True)
        if subprocess.check_output(['git', '-C', str(target), 'status', '--porcelain'], text=True).strip():
            raise SystemExit(f'Refusing to change a dirty checkout: {target}')
        present = subprocess.run(['git', '-C', str(target), 'cat-file', '-e', item['revision'] + '^{commit}'], capture_output=True)
        if present.returncode:
            subprocess.run(['git', '-C', str(target), 'fetch', item['url'], item['revision']], check=True)
        subprocess.run(['git', '-C', str(target), 'checkout', '--detach', item['revision']], check=True)
    print('Build infrastructure ready. Follow docs/new-computer.md to initialise build-k7.')


if __name__ == '__main__':
    main()
