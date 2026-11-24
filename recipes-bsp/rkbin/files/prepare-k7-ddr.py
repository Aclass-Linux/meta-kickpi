"""Generate and verify K7 DDR configuration with the selected Rockchip tool."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

rkbin, params, output, reference = map(Path, sys.argv[1:5])
original = Path(sys.argv[5])
tool = rkbin / 'tools/ddrbin_tool.py'
expected = dict(line.strip().split('=', 1) for line in params.read_text().splitlines()
                if '=' in line and not line.startswith('start tag='))
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def run(*args):
    subprocess.run([sys.executable, str(tool), 'rk3576', *map(str, args)], check=True)
def read_params(path):
    return dict(line.strip().split('=', 1) for line in path.read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('/*'))

before = output.with_suffix('.before.txt')
after = output.with_suffix('.after.txt')
original_hash = sha(original)
run('-g', before, original)
shutil.copyfile(original, output)
# Whitespace tells the official tool to retain its original version string.
run(params, output, '--ver_edit', ' ')
run('-g', after, output)
a, b = read_params(before), read_params(after)
changes = {k: {'before': a.get(k), 'after': b.get(k)}
           for k in sorted(a.keys() | b.keys()) if a.get(k) != b.get(k)}
expected_changes = {k for k, v in expected.items() if a.get(k) != v}
if set(changes) != expected_changes or any(b.get(k) != v for k, v in expected.items()):
    raise SystemExit('Unexpected DDR parameter changes: ' + repr(changes))
for prefix in ('lp4', 'lp4x'):
    if any(int(b[f'{prefix}_f{i}_freq_mhz']) > 1560 for i in range(1, 6)):
        raise SystemExit('DDR secondary frequency exceeds F0')
reference_params = output.with_suffix('.reference.txt')
run('-g', reference_params, reference)
ref = read_params(reference_params)
if not ref or any(b.get(k) != v for k, v in ref.items()):
    raise SystemExit('DDR common parameters do not match the K7 v1.08 reference')
if sha(original) != original_hash:
    raise SystemExit('Original DDR firmware changed')
report = {'input': original.name, 'input_sha256': original_hash,
          'tool_sha256': sha(tool), 'params_sha256': sha(params),
          'output': output.name, 'output_sha256': sha(output), 'changes': changes,
          'reference_sha256': sha(reference), 'common_reference_fields_matched': len(ref),
          'hardware_validation': 'pending'}
output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
